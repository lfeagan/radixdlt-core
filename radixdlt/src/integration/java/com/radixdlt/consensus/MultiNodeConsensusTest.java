/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.RotatingLeaders;
import com.radixdlt.consensus.liveness.Dictatorship;
import com.radixdlt.consensus.safety.QuorumRequirements;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.safety.WhitelistQuorum;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

public class MultiNodeConsensusTest {
	private static final Logger log = Logging.getLogger("Test");
	private final TestEventCoordinatorNetwork testEventCoordinatorNetwork = new TestEventCoordinatorNetwork();

	private Atom genesis;
	private Vertex genesisVertex;
	private QuorumCertificate genesisQC;

	@Before
	public void setup() {
		this.genesis = mock(Atom.class);
		when(this.genesis.toString()).thenReturn(Long.toHexString(0));
		this.genesisVertex = Vertex.createGenesis(genesis);
		this.genesisQC = new QuorumCertificate(
			new VertexMetadata(View.genesis(), genesisVertex.getId(), null, null),
			new ECDSASignatures()
		);
	}

	private ChainedBFT createBFTInstance(
		ECKeyPair key,
		ProposerElection proposerElection,
		QuorumRequirements quorumRequirements,
		VertexStore vertexStore
	) {
		Mempool mempool = mock(Mempool.class);
		AtomicLong atomId = new AtomicLong();
		doAnswer(inv -> {
			Atom atom = mock(Atom.class);
			when(atom.toString()).thenReturn(Long.toHexString(atomId.incrementAndGet()));
			return Collections.singletonList(atom);
		}).when(mempool).getAtoms(anyInt(), anySet());
		ProposalGenerator proposalGenerator = new ProposalGenerator(vertexStore, mempool);
		SafetyRules safetyRules = new SafetyRules(key, vertexStore, SafetyState.initialState());
		PacemakerImpl pacemaker = new PacemakerImpl(quorumRequirements, Executors.newSingleThreadScheduledExecutor());
		PendingVotes pendingVotes = new PendingVotes(quorumRequirements);
		EventCoordinator eventCoordinator = new EventCoordinator(
			proposalGenerator,
			mempool,
			testEventCoordinatorNetwork.getNetworkSender(key.getUID()),
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			key
		);

		return new ChainedBFT(
			eventCoordinator,
			testEventCoordinatorNetwork.getNetworkRx(key.getUID()),
			pacemaker
		);
	}

	private List<TestObserver<Vertex>> runBFT(
		List<ECKeyPair> nodes,
		QuorumRequirements quorumRequirements,
		ProposerElection proposerElection
	) {
		return nodes.stream()
			.map(e -> {
				RadixEngine radixEngine = mock(RadixEngine.class);
				when(radixEngine.staticCheck(any())).thenReturn(Optional.empty());
				VertexStore vertexStore = new VertexStore(genesisVertex, genesisQC, radixEngine);
				TestObserver<Vertex> testObserver = TestObserver.create();
				vertexStore.lastCommittedVertex().subscribe(testObserver);
				ChainedBFT chainedBFT = createBFTInstance(e, proposerElection, quorumRequirements, vertexStore);
				chainedBFT.processEvents().subscribe();
				return testObserver;
			})
			.collect(Collectors.toList());
	}

	@Test
	public void given_3_correct_bft_instances_with_single_leader__then_all_instances_should_get_3_commits() throws Exception {
		final List<ECKeyPair> nodes = Arrays.asList(new ECKeyPair(), new ECKeyPair(), new ECKeyPair());
		final QuorumRequirements quorumRequirements = WhitelistQuorum.from(nodes.stream().map(ECKeyPair::getPublicKey));
		final ProposerElection proposerElection = new Dictatorship(nodes.get(0).getUID());
		final List<TestObserver<Vertex>> committedListeners = runBFT(nodes, quorumRequirements, proposerElection);

		final int commitCount = 3;
		for (TestObserver<Vertex> committedListener : committedListeners) {
			committedListener.awaitCount(commitCount);
			for (int i = 0; i < commitCount; i++) {
				final int id = i;
				// Checking Atom's mocked toString()
				committedListener.assertValueAt(i, v -> v.getAtom().toString().equals(Integer.toHexString(id)));
			}
		}
	}

	@Test
	public void given_3_correct_bft_instances_with_rotating_leaders__then_all_instances_should_get_the_same_5_commits() throws Exception {
		final List<ECKeyPair> nodes = Arrays.asList(new ECKeyPair(), new ECKeyPair(), new ECKeyPair());
		final QuorumRequirements quorumRequirements = WhitelistQuorum.from(nodes.stream().map(ECKeyPair::getPublicKey));
		final ProposerElection proposerElection = new RotatingLeaders(nodes.stream().map(ECKeyPair::getUID).collect(ImmutableList.toImmutableList()));
		final List<TestObserver<Vertex>> committedListeners = runBFT(nodes, quorumRequirements, proposerElection);

		final int commitCount = 5;
		for (TestObserver<Vertex> committedListener : committedListeners) {
			committedListener.awaitCount(commitCount);
		}

		for (TestObserver<Vertex> committedListener : committedListeners) {
			for (TestObserver<Vertex> otherCommittedListener : committedListeners) {
				assertThat(committedListener.values().subList(0, commitCount))
					.isEqualTo(otherCommittedListener.values().subList(0, commitCount));
			}
		}
	}

	@Test
	public void given_2_out_of_3_correct_bft_instances_with_single_leader__then_all_instances_should_only_get_genesis_commit() throws Exception {
		final List<ECKeyPair> nodes = Arrays.asList(new ECKeyPair(), new ECKeyPair(), new ECKeyPair());
		final QuorumRequirements quorumRequirements = WhitelistQuorum.from(nodes.stream().map(ECKeyPair::getPublicKey));
		final ProposerElection proposerElection = new Dictatorship(nodes.get(0).getUID());
		testEventCoordinatorNetwork.setSendingDisable(nodes.get(2).getUID(), true);
		final List<TestObserver<Vertex>> committedListeners = runBFT(nodes, quorumRequirements, proposerElection);
		final int commitCount = 10;

		for (TestObserver<Vertex> committedListener : committedListeners) {
			committedListener.awaitCount(commitCount);
			committedListener.assertValue(v -> v.getAtom().toString().equals(Integer.toHexString(0)));
		}
	}
}
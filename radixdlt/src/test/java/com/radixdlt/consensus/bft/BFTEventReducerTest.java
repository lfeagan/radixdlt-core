/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.ViewTimeoutSigned;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hash;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BFTEventReducerTest {
	private BFTEventReducer reducer;
	private NextCommandGenerator nextCommandGenerator;
	private ProposerElection proposerElection;
	private SafetyRules safetyRules;
	private Pacemaker pacemaker;
	private PendingVotes pendingVotes;
	private BFTEventReducer.BFTEventSender sender;
	private VertexStore vertexStore;
	private BFTSync vertexStoreSync;
	private BFTValidatorSet validatorSet;
	private SystemCounters counters;
	private BFTNode self;
	private Hasher hasher;

	@Before
	public void setUp() {
		this.nextCommandGenerator = mock(NextCommandGenerator.class);
		this.sender = mock(BFTEventReducer.BFTEventSender.class);
		this.safetyRules = mock(SafetyRules.class);
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.vertexStoreSync = mock(BFTSync.class);
		this.pendingVotes = mock(PendingVotes.class);
		this.proposerElection = mock(ProposerElection.class);
		this.validatorSet = mock(BFTValidatorSet.class);
		this.counters = mock(SystemCounters.class);
		this.self = mock(BFTNode.class);
		this.hasher = mock(Hasher.class);

		when(hasher.hash(any())).thenReturn(mock(Hash.class));

		this.reducer = new BFTEventReducer(
			self,
			nextCommandGenerator,
			sender,
			safetyRules,
			pacemaker,
			vertexStore,
			vertexStoreSync,
			pendingVotes,
			proposerElection,
			validatorSet,
			counters,
			System::currentTimeMillis,
			hasher
		);
	}

	@Test
	public void when_start__then_should_proceed_to_first_view() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		when(proposerElection.getProposer(any())).thenReturn(self);
		when(vertexStore.getHighestQC()).thenReturn(qc);
		reducer.start();
		verify(pacemaker, times(1)).processQC(eq(qc), any());
	}

	@Test
	public void when_process_vote_and_new_qc_not_synced__then_bft_update_should_cause_it_to_process_it() {
		Vote vote = mock(Vote.class);
		when(vote.getAuthor()).thenReturn(mock(BFTNode.class));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View view = mock(View.class);
		when(qc.getView()).thenReturn(view);
		BFTHeader header = mock(BFTHeader.class);
		Hash id = mock(Hash.class);
		when(header.getVertexId()).thenReturn(id);
		when(qc.getProposed()).thenReturn(header);
		when(pendingVotes.insertVote(eq(vote), eq(validatorSet))).thenReturn(Optional.of(qc));
		when(vertexStoreSync.syncToQC(eq(qc), any(), any())).thenReturn(SyncResult.IN_PROGRESS);
		reducer.processVote(vote);
		verify(pacemaker, never()).processQC(any(), any());

		BFTUpdate update = mock(BFTUpdate.class);
		VerifiedVertex v = mock(VerifiedVertex.class);
		when(v.getId()).thenReturn(id);
		when(update.getInsertedVertex()).thenReturn(v);
		reducer.processBFTUpdate(update);
		verify(pacemaker, never()).processQC(eq(qc), any());
	}

	@Test
	public void when_processing_vote_as_not_proposer__then_nothing_happens() {
		Vote voteMessage = mock(Vote.class);
		BFTHeader proposal = new BFTHeader(View.of(2), Hash.random(), mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(View.of(1), Hash.random(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(proposal, parent, null);
		when(voteMessage.getVoteData()).thenReturn(voteData);

		reducer.processVote(voteMessage);
		verify(pacemaker, times(0)).processQC(any(), any());
	}

	@Test
	public void when_processing_relevant_local_timeout__then_pacemaker_processes() {
		reducer.processLocalTimeout(View.of(0L));

		verify(pacemaker, times(1)).processLocalTimeout(eq(View.of(0L)));
	}

	@Test
	public void when_processing_timeout_as_proposer__then_timeout_is_processed_and_proposal_is_sent() {
		ViewTimeoutSigned viewTimeout = mock(ViewTimeoutSigned.class);
		when(viewTimeout.getQC()).thenReturn(mock(QuorumCertificate.class));
		when(viewTimeout.view()).thenReturn(View.of(0L));
		when(pacemaker.getCurrentView()).thenReturn(View.of(0L));
		when(pacemaker.processViewTimeout(any(), any())).thenReturn(Optional.of(View.of(1L)));
		when(proposerElection.getProposer(any())).thenReturn(self);
		QuorumCertificate highQC = mock(QuorumCertificate.class);
		BFTHeader header = mock(BFTHeader.class);
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(highQC.getProposed()).thenReturn(header);
		when(vertexStore.getHighestQC()).thenReturn(highQC);
		when(nextCommandGenerator.generateNextCommand(eq(View.of(1L)), any())).thenReturn(mock(Command.class));
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of());
		reducer.processViewTimeout(viewTimeout);
		verify(pacemaker, times(1)).processViewTimeout(any(), any());
		verify(sender, times(1)).broadcastProposal(any(), any());
	}

	@Test
	public void when_processing_valid_stored_proposal__then_atom_is_voted_on_and_timeout() throws SafetyViolationException {
		View currentView = View.of(123);

		when(proposerElection.getProposer(any())).thenReturn(mock(BFTNode.class));

		UnverifiedVertex proposedVertex = mock(UnverifiedVertex.class);
		when(proposedVertex.getCommand()).thenReturn(mock(Command.class));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(any(), any(), anyLong(), any());
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(pacemaker, times(1)).processNextView(any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_next_leader__then_atom_is_voted_on_and_timeout() throws SafetyViolationException {
		View currentView = View.of(123);
		QuorumCertificate currentQC = mock(QuorumCertificate.class);
		when(currentQC.getView()).thenReturn(currentView);

		when(proposerElection.getProposer(eq(currentView))).thenReturn(mock(BFTNode.class));
		when(proposerElection.getProposer(eq(currentView.next()))).thenReturn(self);

		UnverifiedVertex proposedVertex = mock(UnverifiedVertex.class);
		when(proposedVertex.getCommand()).thenReturn(mock(Command.class));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(any(), any(), anyLong(), any());

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(pacemaker, times(0)).processNextView(any());
	}

	@Test
	public void when_processing_valid_stored_proposal_and_leader__then_atom_is_voted_on_and_no_timeout() throws SafetyViolationException {
		View currentView = View.of(123);
		QuorumCertificate currentQC = mock(QuorumCertificate.class);
		when(currentQC.getView()).thenReturn(currentView);

		when(proposerElection.getProposer(eq(currentView))).thenReturn(self);

		UnverifiedVertex proposedVertex = mock(UnverifiedVertex.class);
		when(proposedVertex.getCommand()).thenReturn(mock(Command.class));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		View qcView = mock(View.class);
		when(qc.getView()).thenReturn(qcView);
		when(proposedVertex.getQC()).thenReturn(qc);
		when(proposedVertex.getView()).thenReturn(currentView);

		Proposal proposal = mock(Proposal.class);
		when(proposal.getVertex()).thenReturn(proposedVertex);

		when(pacemaker.getCurrentView()).thenReturn(currentView);
		Vote vote = mock(Vote.class);
		doReturn(vote).when(safetyRules).voteFor(any(), any(), anyLong(), any());

		reducer.processProposal(proposal);

		verify(sender, times(1)).sendVote(eq(vote), any());
		verify(pacemaker, times(0)).processNextView(any());
	}
}

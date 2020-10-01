/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ViewTimeoutSigned;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.consensus.Vote;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.observers.TestObserver;

import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralBFTNetworkTest {
	private BFTNode self;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private MessageCentralBFTNetwork network;

	@Before
	public void setUp() {
		this.self = mock(BFTNode.class);
		Universe universe = mock(Universe.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = mock(MessageCentral.class);
		this.network = new MessageCentralBFTNetwork(self, universe, addressBook, messageCentral);
	}

	@Test
	public void when_send_timeout_to_self__then_should_receive_timeout_message() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.bftEvents().subscribe(testObserver);
		ViewTimeoutSigned viewTimeout = mock(ViewTimeoutSigned.class);
		network.sendViewTimeout(viewTimeout, self);
		testObserver.awaitCount(1);
		testObserver.assertValue(viewTimeout);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_vote_message() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.bftEvents().subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.sendVote(vote, self);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_proposal() {
		TestObserver<ConsensusEvent> testObserver = TestObserver.create();
		network.bftEvents().subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.broadcastProposal(proposal, Collections.singleton(this.self));
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_send_timeout__then_message_central_should_be_sent_timeout_message() {
		ViewTimeoutSigned viewTimeout = mock(ViewTimeoutSigned.class);
		ECPublicKey leaderPk = ECKeyPair.generateNew().getPublicKey();
		BFTNode leader = mock(BFTNode.class);
		when(leader.getKey()).thenReturn(leaderPk);
		Peer peer = mock(Peer.class);
		when(peer.getNID()).thenReturn(leaderPk.euid());
		when(addressBook.peer(leaderPk.euid())).thenReturn(Optional.of(peer));

		network.sendViewTimeout(viewTimeout, leader);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusEventMessage.class));
	}

	@Test
	public void when_send_timeout_to_nonexistent__then_no_message_sent() {
		ViewTimeoutSigned viewTimeout = mock(ViewTimeoutSigned.class);
		BFTNode node = mock(BFTNode.class);
		when(node.getKey()).thenReturn(mock(ECPublicKey.class));
		network.sendViewTimeout(viewTimeout, node);
		verify(messageCentral, never()).send(any(), any());
	}

	@Test
	public void when_send_vote__then_message_central_should_be_sent_vote_message() {
		Vote vote = mock(Vote.class);
		ECPublicKey leaderPk = ECKeyPair.generateNew().getPublicKey();
		BFTNode leader = mock(BFTNode.class);
		when(leader.getKey()).thenReturn(leaderPk);
		Peer peer = mock(Peer.class);
		when(peer.getNID()).thenReturn(leaderPk.euid());
		when(addressBook.peer(leaderPk.euid())).thenReturn(Optional.of(peer));

		network.sendVote(vote, leader);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusEventMessage.class));
	}
}
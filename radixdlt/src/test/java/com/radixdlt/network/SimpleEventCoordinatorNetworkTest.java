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

package com.radixdlt.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.ConsensusMessage;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Vote;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;

public class SimpleEventCoordinatorNetworkTest {
	private ECPublicKey selfKey;
	private Universe universe;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private SimpleEventCoordinatorNetwork network;

	@Before
	public void setUp() {
		this.selfKey = ECKeyPair.generateNew().getPublicKey();
		this.universe = mock(Universe.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = mock(MessageCentral.class);
		this.network = new SimpleEventCoordinatorNetwork(selfKey, universe, addressBook, messageCentral);
	}

	@Test
	public void when_send_new_view_to_self__then_should_receive_new_view_message() {
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.consensusMessages().subscribe(testObserver);
		NewView newView = mock(NewView.class);
		network.sendNewView(newView, selfKey);
		testObserver.awaitCount(1);
		testObserver.assertValue(newView);
	}

	@Test
	public void when_send_vote_to_self__then_should_receive_vote_message() {
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.consensusMessages().subscribe(testObserver);
		Vote vote = mock(Vote.class);
		network.sendVote(vote, selfKey);
		testObserver.awaitCount(1);
		testObserver.assertValue(vote);
	}

	@Test
	public void when_broadcast_proposal__then_should_receive_proposal() {
		TestObserver<ConsensusMessage> testObserver = TestObserver.create();
		network.consensusMessages().subscribe(testObserver);
		Proposal proposal = mock(Proposal.class);
		network.broadcastProposal(proposal);
		testObserver.awaitCount(1);
		testObserver.assertValue(proposal);
	}

	@Test
	public void when_send_new_view__then_message_central_should_be_sent_new_view_message() {
		NewView newView = mock(NewView.class);
		ECPublicKey leader = ECKeyPair.generateNew().getPublicKey();
		Peer peer = mock(Peer.class);
		when(peer.getNID()).thenReturn(leader.euid());
		when(addressBook.peers()).thenReturn(Stream.of(peer));

		network.sendNewView(newView, leader);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusMessageDto.class));
	}

	@Test
	public void when_send_vote__then_message_central_should_be_sent_vote_message() {
		Vote vote = mock(Vote.class);
		ECPublicKey leader = ECKeyPair.generateNew().getPublicKey();
		Peer peer = mock(Peer.class);
		when(peer.getNID()).thenReturn(leader.euid());
		when(addressBook.peers()).thenReturn(Stream.of(peer));

		network.sendVote(vote, leader);
		verify(messageCentral, times(1)).send(eq(peer), any(ConsensusMessageDto.class));
	}
}
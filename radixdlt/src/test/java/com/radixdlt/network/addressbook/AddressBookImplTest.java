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

package com.radixdlt.network.addressbook;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.Radix;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.properties.RuntimeProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.reactivex.rxjava3.observers.TestObserver;

public class AddressBookImplTest {

	private AtomicInteger savedPeerCount;
	private AtomicInteger deletedPeerCount;
	private AddressBookImpl addressbook;
	private TestObserver<AddressBookEvent> peersObserver;

	@Before
	public void setUp() {
		this.savedPeerCount = new AtomicInteger(0);
		this.deletedPeerCount = new AtomicInteger(0);
		// No danger of resources not being closed with mock
		@SuppressWarnings("resource")
		PeerPersistence persistence = mock(PeerPersistence.class);
		doAnswer(invocation -> {
			savedPeerCount.incrementAndGet();
			return true;
		}).when(persistence).savePeer(any());
		doAnswer(invocation -> {
			deletedPeerCount.incrementAndGet();
			return true;
		}).when(persistence).deletePeer(any());
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doReturn(60_000L).when(properties).get(eq("addressbook.recency_ms"), anyLong());
		this.addressbook = new AddressBookImpl(persistence, properties, System::currentTimeMillis);
		this.peersObserver = TestObserver.create();
		this.addressbook.peerUpdates()
			.subscribe(this.peersObserver);
		this.peersObserver.awaitCount(1)
			.assertValue(e -> e instanceof PeersAddedEvent && e.peers().isEmpty());
	}

	@After
	public void tearDown() throws IOException {
		// Ensure emitters are properly removed on disposal
		this.peersObserver.dispose();
		assertSetEmpty(Whitebox.getInternalState(this.addressbook, "emitters"));
	    addressbook.close();
	}

	@Test
	public void testAddPeerWithTransport() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		PeerWithTransport peer = new PeerWithTransport(transportInfo);

		// Adding should return true, and also cause event, but not saved
		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(1)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(0, this.savedPeerCount.get());

		// Adding again should return false, and also not fire broadcast event, not saved
		assertFalse(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(1)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2); // But no new ones
		assertEquals(0, this.savedPeerCount.get());

		// Should be able to find by transport in address book
		Peer foundPeer = this.addressbook.peer(transportInfo);
		assertSame(peer, foundPeer);

		// Quick check of internal state too
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}

	@Test
	public void testAddPeerWithNid() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		// Adding should return true, and also cause event, saved
		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(1)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());

		// Adding again should return false, and also not fire broadcast event, not saved again
		assertFalse(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2); // But no new ones
		assertEquals(1, this.savedPeerCount.get());

		// Should be able to find by nid in address book
		Optional<Peer> foundPeer = this.addressbook.peer(nid);
		assertTrue(foundPeer.isPresent());
		assertSame(peer, foundPeer.get());

		// Quick check of internal state too
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testAddPeerWithSystem() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		// Prefer to mock RadixSystem.  Not really sure what's going on in there.
		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.ONE);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		PeerWithSystem peer = new PeerWithSystem(system);

		// Adding should return true, and also fire broadcast event, saved
		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());

		// Adding again should return false, and also not fire broadcast event, not saved again
		assertFalse(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2); // But no new ones
		assertEquals(1, this.savedPeerCount.get());

		// Should be able to find by nid in address book
		Optional<Peer> foundPeer1 = this.addressbook.peer(system.getNID());
		assertTrue(foundPeer1.isPresent());
		assertSame(peer, foundPeer1.get());

		// Should be able to find by transportInfo in address book
		Peer foundPeer2 = this.addressbook.peer(transportInfo);
		assertSame(peer, foundPeer2);

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRemovePeerWithNid() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		// Adding should return true and broadcast add
		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(0, this.deletedPeerCount.get());

		assertTrue(this.addressbook.removePeer(peer));
		this.peersObserver.awaitCount(3)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(3)
			.assertValueAt(2, e -> e instanceof PeersRemovedEvent);
		assertEquals(1, this.deletedPeerCount.get());

		// Quick check of internal state too
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRemovePeerWithTransport() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		PeerWithTransport peer = new PeerWithTransport(transportInfo);

		// Adding should return true and broadcast add
		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(0, this.deletedPeerCount.get());

		assertTrue(this.addressbook.removePeer(peer));
		this.peersObserver.awaitCount(3)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(3)
			.assertValueAt(2, e -> e instanceof PeersRemovedEvent);
		assertEquals(0, this.deletedPeerCount.get()); // Wasn't saved, wasn't deleted

		// Quick check of internal state too
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeer() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());

		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		PeerWithSystem peer2 = new PeerWithSystem(system);
		assertTrue(this.addressbook.updatePeer(peer2));
		this.peersObserver.awaitCount(3)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(3)
			.assertValueAt(2, e -> e instanceof PeersUpdatedEvent);
		assertEquals(2, this.savedPeerCount.get());

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeerSystem() {
		EUID nid = EUID.ONE;
		PeerWithNid peer = new PeerWithNid(nid);

		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(1)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, e -> e instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());

		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		Peer peer2 = this.addressbook.updatePeerSystem(peer, system);
		assertNotNull(peer2);
		this.peersObserver.awaitCount(3)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(3)
			.assertValueAt(2, e -> e instanceof PeersUpdatedEvent);
		assertEquals(2, this.savedPeerCount.get());

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeerSameSystem() throws InterruptedException, IOException {
		EUID nid = EUID.ONE;
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(nid);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		PeerWithSystem peer = new PeerWithSystem(system);

		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());
		assertSetSize(1, Whitebox.getInternalState(this.addressbook, "emitters"));

		Peer peer2 = this.addressbook.updatePeerSystem(new PeerWithTransport(transportInfo), system);
		assertNotNull(peer2);
		assertEquals(1, this.savedPeerCount.get());
		this.addressbook.close();
		this.peersObserver.await(100, TimeUnit.MILLISECONDS); // Just in case something arrives
		this.peersObserver
			.assertNoErrors()
			.assertComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testUpdatePeerSystemChangedNid() {
		PeerWithNid peer = new PeerWithNid(EUID.ONE);

		assertTrue(this.addressbook.addPeer(peer));
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());
		assertEquals(0, this.deletedPeerCount.get());

		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		Peer peer2 = this.addressbook.updatePeerSystem(peer, system);
		assertNotNull(peer2);
		assertNotSame(peer, peer2);
		this.peersObserver.awaitCount(4)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(4)
			.assertValueAt(2, v -> v instanceof PeersAddedEvent)
			.assertValueAt(3, v -> v instanceof PeersRemovedEvent);
		assertEquals(2, this.savedPeerCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Updating again should have no effect
		Peer peer3 = this.addressbook.updatePeerSystem(peer2, system);
		assertSame(peer2, peer3);
		this.peersObserver.awaitCount(4)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(4);
		assertEquals(2, this.savedPeerCount.get());
		assertEquals(1, this.deletedPeerCount.get());

		// Quick check of internal state too
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testPeerNid() {
		Optional<Peer> peer = this.addressbook.peer(EUID.ONE);
		assertFalse(peer.isPresent());
		this.peersObserver
			.assertValueCount(1);
		assertEquals(0, this.savedPeerCount.get());

		ECKeyPair key = ECKeyPair.generateNew();
		RadixSystem sys = new RadixSystem(key.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, ImmutableList.of());
		PeerWithSystem pws = new PeerWithSystem(sys);
		this.addressbook.addPeer(pws);

		Optional<Peer> peer2 = this.addressbook.peer(pws.getNID());
		assertTrue(peer2.isPresent());
		this.peersObserver.awaitCount(2)
			.assertNoErrors()
			.assertNotComplete()
			.assertValueCount(2)
			.assertValueAt(1, v -> v instanceof PeersAddedEvent);
		assertEquals(1, this.savedPeerCount.get());
		assertSame(pws, peer2.get());

		// Quick check of internal state too
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapSize(1, Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testPeerTransportInfo() {
		TransportInfo transportInfo = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		Peer peer = this.addressbook.peer(transportInfo);
		assertNotNull(peer);
		this.peersObserver
			.assertValueCount(1);
		assertEquals(0, this.savedPeerCount.get());

		Peer peer2 = this.addressbook.peer(transportInfo);
		assertNotNull(peer2);
		this.peersObserver
			.assertValueCount(1);
		assertEquals(0, this.savedPeerCount.get());
		assertEquals(peer, peer2);

		// Quick check of internal state too
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertMapSize(0, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}

	@Test
	public void testPeers() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		PeerWithTransport peer1 = new PeerWithTransport(transportInfo1);
		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now
		PeerWithTransport peer2 = new PeerWithTransport(transportInfo2);
		peer2.setTimestamp(Timestamps.ACTIVE, 0L); // A long time ago

		assertTrue(this.addressbook.addPeer(peer1));
		assertTrue(this.addressbook.addPeer(peer2));

		ImmutableSet<Peer> peers = this.addressbook.peers().collect(ImmutableSet.toImmutableSet());
		assertEquals(2, peers.size());
		assertTrue(peers.contains(peer1));
		assertTrue(peers.contains(peer2));

		// Quick check of internal state too
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testRecentPeers() {
		TransportInfo transportInfo1 = TransportInfo.of("DUMMY1", StaticTransportMetadata.empty());
		TransportInfo transportInfo2 = TransportInfo.of("DUMMY2", StaticTransportMetadata.empty());
		PeerWithTransport peer1 = new PeerWithTransport(transportInfo1);
		peer1.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp()); // Now
		PeerWithTransport peer2 = new PeerWithTransport(transportInfo2);
		peer2.setTimestamp(Timestamps.ACTIVE, 0L); // A long time ago

		assertTrue(this.addressbook.addPeer(peer1));
		assertTrue(this.addressbook.addPeer(peer2));

		ImmutableSet<Peer> peers = this.addressbook.recentPeers().collect(ImmutableSet.toImmutableSet());
		assertEquals(1, peers.size());
		assertTrue(peers.contains(peer1));
		assertFalse(peers.contains(peer2));

		// Quick check of internal state too
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByInfo"));
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByNid"));
	}

	@Test
	public void testNids() {
		PeerWithNid peer1 = new PeerWithNid(EUID.ONE);
		PeerWithNid peer2 = new PeerWithNid(EUID.TWO);

		assertTrue(this.addressbook.addPeer(peer1));
		assertTrue(this.addressbook.addPeer(peer2));

		ImmutableSet<EUID> nids = this.addressbook.nids().collect(ImmutableSet.toImmutableSet());
		assertEquals(2, nids.size());
		assertTrue(nids.contains(EUID.ONE));
		assertTrue(nids.contains(EUID.TWO));

		// Quick check of internal state too
		assertMapSize(2, Whitebox.getInternalState(this.addressbook, "peersByNid"));
		assertMapEmpty(Whitebox.getInternalState(this.addressbook, "peersByInfo"));
	}


	// Type coercion
	private <T, U> void assertMapEmpty(Map<T, U> map) {
		assertTrue(map.isEmpty());
	}

	// Type coercion
	private <T, U> void assertMapSize(int size, Map<T, U> map) {
		assertEquals(size, map.size());
	}

	// Type coercion
	private <T> void assertSetEmpty(Set<T> set) {
		assertTrue(set.isEmpty());
	}

	// Type coercion
	private <T> void assertSetSize(int size, Set<T> set) {
		assertEquals(size, set.size());
	}
}

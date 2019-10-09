package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.consensus.messages.SampleRequestMessage;
import com.radixdlt.tempo.consensus.messages.SampleResponseMessage;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import com.radixdlt.tempo.discovery.AtomDiscoveryListener;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public final class SampleRetriever implements Closeable, AtomDiscoverer {
	private static final Logger log = Logging.getLogger("consensus.sampler");
	private static final int REQUEST_QUEUE_CAPACITY = 8192;
	private static final int REQUEST_PROCESSOR_THREADS = 1;
	private static final int SAMPLE_REQUEST_TIMEOUT_MILLISECONDS = 1000;

	private final Scheduler scheduler;
	private final TempoAtomStoreView storeView;
	private final MessageCentral messageCentral;

	private final PendingSamplesState pendingSamples = new PendingSamplesState();

	private final Collection<AtomDiscoveryListener> discoveryListeners;

	private final BlockingQueue<SampleRequest> requestQueue;
	private final SimpleThreadPool<SampleRequest> requestThreadPool;

	@Inject
	public SampleRetriever(
		Scheduler scheduler,
		TempoAtomStoreView storeView,
		MessageCentral messageCentral
	) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.storeView = Objects.requireNonNull(storeView);
		this.messageCentral = Objects.requireNonNull(messageCentral);

		// TODO improve locking to something like in messaging
		this.discoveryListeners = Collections.synchronizedList(new ArrayList<>());

		messageCentral.addListener(SampleRequestMessage.class, this::onRequest);
		messageCentral.addListener(SampleResponseMessage.class, this::onResponse);

		this.requestQueue = new ArrayBlockingQueue<>(REQUEST_QUEUE_CAPACITY);
		this.requestThreadPool = new SimpleThreadPool<>("Sample request processing", REQUEST_PROCESSOR_THREADS, requestQueue::take, this::processRequest, log);
		this.requestThreadPool.start();
	}

	CompletableFuture<Samples> sample(AID aid, Set<LedgerIndex> indices, Collection<Peer> peers) {
		// TODO batch requests to same node over time window?
		EUID tag = generateTag();
		log.debug("Request sampling '" + tag + "' for " + indices + " from " + peers);

		CompletableFuture<Samples> future = new CompletableFuture<>();
		pendingSamples.put(tag, future, indices, peers.stream().map(Peer::getNID).collect(Collectors.toSet()));
		SampleRequestMessage request = new SampleRequestMessage(tag, ImmutableMap.of(aid, indices));
		for (Peer peer : peers) {
			messageCentral.send(peer, request);
		}

		// schedule timeout
		scheduler.schedule(() -> attemptComplete(tag, pendingSamples.timeout(tag)), SAMPLE_REQUEST_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
		return future;
	}

	private void attemptComplete(EUID tag, Samples completeSamples) {
		if (completeSamples != null) {
			log.debug("Completed sampling '" + tag + "', got " + completeSamples);
			pendingSamples.getFuture(tag).complete(completeSamples);
			pendingSamples.remove(tag);
		}
	}

	private void onRequest(Peer peer, SampleRequestMessage message) {
		SampleRequest request = new SampleRequest(message, peer);
		requestQueue.add(request);
	}

	private void processRequest(SampleRequest request) {
		Set<AID> proposedAids = new HashSet<>();
		Map<LedgerIndex, AID> aidByIndex = new HashMap<>();
		Set<LedgerIndex> unavailableIndices = new HashSet<>();

		Map<AID, Set<LedgerIndex>> requestedIndicesByAids = request.getMessage().getRequestedIndicesByAids();
		for (AID proposedAid : requestedIndicesByAids.keySet()) {
			boolean haveConflictingAtom = false;
			Set<LedgerIndex> requestedIndices = requestedIndicesByAids.get(proposedAid);
			for (LedgerIndex requestedIndex : requestedIndices) {
				LedgerCursor cursor = storeView.search(LedgerIndex.LedgerIndexType.UNIQUE, requestedIndex, LedgerSearchMode.EXACT);
				if (cursor != null) {
					AID myAidForIndex = cursor.get();
					aidByIndex.put(requestedIndex, myAidForIndex);
					if (!myAidForIndex.equals(proposedAid)) {
						haveConflictingAtom = true;
					}
				} else {
					unavailableIndices.add(requestedIndex);
				}
			}
			if (!haveConflictingAtom) {
				proposedAids.add(proposedAid);
			}
		}

		// TODO need to reconsider notifying for every unknown aid, may be sampled many times for same aid etc.
		if (!proposedAids.isEmpty()) {
			notifyListeners(proposedAids, request.getPeer());
		}

		Sample sample = new Sample(aidByIndex, unavailableIndices);
		SampleResponseMessage response = new SampleResponseMessage(request.getMessage().getTag(), sample);
		messageCentral.send(request.getPeer(), response);
		log.debug("Responding to sample request '" + request.getMessage().getTag() + "' from " + request.getPeer() + " for " + request.getMessage().getRequestedIndicesByAids());
	}

	private void onResponse(Peer peer, SampleResponseMessage message) {
		log.debug("Received sample response for '" + message.getTag() + "' from " + peer);
		Samples completeSamples = pendingSamples.receiveSample(message.getTag(), peer.getNID(), message.getSample());
		attemptComplete(message.getTag(), completeSamples);
	}

	private EUID generateTag() {
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		byte[] value = new byte[EUID.BYTES];
		rng.nextBytes(value);
		return new EUID(value);
	}

	@Override
	public void close() {
		requestThreadPool.stop();
		messageCentral.removeListener(SampleRequestMessage.class, this::onRequest);
		messageCentral.removeListener(SampleResponseMessage.class, this::onResponse);
	}

	@Override
	public void addListener(AtomDiscoveryListener listener) {
		discoveryListeners.add(listener);
	}

	@Override
	public void removeListener(AtomDiscoveryListener listener) {
		discoveryListeners.remove(listener);
	}

	private void notifyListeners(Set<AID> aids, Peer peer) {
		discoveryListeners.forEach(listener -> listener.accept(aids, peer));
	}

	private static final class SampleRequest {
		private final SampleRequestMessage message;
		private final Peer peer;

		private SampleRequest(SampleRequestMessage message, Peer peer) {
			this.message = message;
			this.peer = peer;
		}

		private SampleRequestMessage getMessage() {
			return message;
		}

		private Peer getPeer() {
			return peer;
		}
	}
}

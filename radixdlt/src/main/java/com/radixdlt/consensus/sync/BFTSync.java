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

package com.radixdlt.consensus.sync;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTSyncer;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.BFTUpdateProcessor;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerUpdateProcessor;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages keeping the VertexStore and pacemaker in sync for consensus
 */
public final class BFTSync implements BFTSyncResponseProcessor, BFTUpdateProcessor, BFTSyncer, LedgerUpdateProcessor<LedgerUpdate> {
	private enum SyncStage {
		PREPARING,
		GET_COMMITTED_VERTICES,
		SYNC_TO_COMMIT,
		GET_QC_VERTICES
	}

	private static class SyncState {
		private final Hash localSyncId;
		private final SyncInfo syncInfo;
		private final BFTHeader committedHeader;
		private final VerifiedLedgerHeaderAndProof committedProof;
		private final BFTNode author;
		private SyncStage syncStage;
		private final LinkedList<VerifiedVertex> fetched = new LinkedList<>();

		SyncState(SyncInfo syncInfo, BFTNode author) {
			this.localSyncId = syncInfo.highestQC().getProposed().getVertexId();
			Pair<BFTHeader, VerifiedLedgerHeaderAndProof> pair = syncInfo.highestCommittedQC()
				.getCommittedAndLedgerStateProof()
				.orElseThrow(() -> new IllegalStateException("committedQC must have a commit"));
			this.committedHeader = pair.getFirst();
			this.committedProof = pair.getSecond();
			this.syncInfo = syncInfo;
			this.author = author;
			this.syncStage = SyncStage.PREPARING;
		}

		void setSyncStage(SyncStage syncStage) {
			this.syncStage = syncStage;
		}

		SyncInfo syncInfo() {
			return this.syncInfo;
		}

		@Override
		public String toString() {
			return String.format("%s{syncInfo=%s syncState=%s}", this.getClass().getSimpleName(), syncInfo, syncStage);
		}
	}


	/**
	 * An asynchronous supplier which retrieves data for a vertex with a given id
	 */
	public interface SyncVerticesRequestSender {
		/**
		 * Send an RPC request to retrieve vertices given an Id and number of
		 * vertices. i.e. The vertex with the given id and (count - 1) ancestors
		 * will be returned.
		 *
		 * @param node the node to retrieve the vertex info from
		 * @param id the id of the vertex to retrieve
		 * @param count number of vertices to retrieve
		 */
		void sendGetVerticesRequest(BFTNode node, Hash id, int count);
	}

	private static final Logger log = LogManager.getLogger();
	private final VertexStore vertexStore;
	private final Pacemaker pacemaker;
	private final Map<Hash, SyncState> syncing = new HashMap<>();
	private final TreeMap<LedgerHeader, List<Hash>> ledgerSyncing;
	private final Map<Pair<Hash, Integer>, List<Hash>> bftSyncing = new HashMap<>();
	private final SyncVerticesRequestSender requestSender;
	private final SyncLedgerRequestSender syncLedgerRequestSender;
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;

	public BFTSync(
		VertexStore vertexStore,
		Pacemaker pacemaker,
		Comparator<LedgerHeader> ledgerHeaderComparator,
		SyncVerticesRequestSender requestSender,
		SyncLedgerRequestSender syncLedgerRequestSender,
		VerifiedLedgerHeaderAndProof currentLedgerHeader
	) {
		this.vertexStore = vertexStore;
		this.pacemaker = pacemaker;
		this.ledgerSyncing = new TreeMap<>(ledgerHeaderComparator);
		this.requestSender = requestSender;
		this.syncLedgerRequestSender = syncLedgerRequestSender;
		this.currentLedgerHeader = Objects.requireNonNull(currentLedgerHeader);
	}

	@Override
	public SyncResult syncToQC(SyncInfo syncInfo, @Nullable BFTNode author) {
		final Hash vertexId = syncInfo.highestQC().getProposed().getVertexId();
		if (syncInfo.highestQC().getView().compareTo(vertexStore.getRoot().getView()) < 0) {
			return SyncResult.INVALID;
		}

		if (vertexStore.addQC(syncInfo.highestQC())) {
			// TODO: check if already sent highest
			this.pacemaker.processQC(vertexStore.getHighestQC(), vertexStore.getHighestCommittedQC());
			return SyncResult.SYNCED;
		}

		// TODO: Move this check into pre-check
		// Bad genesis qc, ignore...
		if (syncInfo.highestQC().getView().isGenesis()) {
			log.warn("SYNC_TO_QC: Bad Genesis: {}", syncInfo);
			return SyncResult.INVALID;
		}

		log.trace("SYNC_TO_QC: Need sync: {}", syncInfo);

		if (syncing.containsKey(vertexId)) {
			return SyncResult.IN_PROGRESS;
		}

		if (author == null) {
			throw new IllegalStateException("Syncing required but author wasn't provided.");
		}

		this.startSync(syncInfo, author);

		return SyncResult.IN_PROGRESS;
	}

	private boolean requiresLedgerSync(SyncState syncState) {
		final BFTHeader committedHeader = syncState.committedHeader;
		if (!vertexStore.containsVertex(committedHeader.getVertexId())) {
			View rootView = vertexStore.getRoot().getView();
			return rootView.compareTo(committedHeader.getView()) < 0;
		}

		return false;
	}

	private void startSync(SyncInfo syncInfo, BFTNode author) {
		final SyncState syncState = new SyncState(syncInfo, author);
		syncing.put(syncState.localSyncId, syncState);
		if (requiresLedgerSync(syncState)) {
			this.doCommittedSync(syncState);
		} else {
			this.doQCSync(syncState);
		}
	}

	private void doQCSync(SyncState syncState) {
		syncState.setSyncStage(SyncStage.GET_QC_VERTICES);
		log.debug("SYNC_VERTICES: QC: Sending initial GetVerticesRequest for sync={}", syncState);
		this.sendBFTSyncRequest(syncState, syncState.syncInfo().highestQC().getProposed().getVertexId(), 1);
	}

	private void doCommittedSync(SyncState syncState) {
		final Hash committedQCId = syncState.syncInfo().highestCommittedQC().getProposed().getVertexId();
		syncState.setSyncStage(SyncStage.GET_COMMITTED_VERTICES);
		log.debug("SYNC_VERTICES: Committed: Sending initial GetVerticesRequest for sync={}", syncState);
		// Retrieve the 3 vertices preceding the committedQC so we can create a valid committed root
		this.sendBFTSyncRequest(syncState, committedQCId, 3);
	}

	private void sendBFTSyncRequest(SyncState syncState, Hash vertexId, int count) {
		bftSyncing.compute(Pair.of(vertexId, count), (pair, syncing) -> {
			if (syncing == null) {
				syncing = new ArrayList<>();
			}
			syncing.add(syncState.localSyncId);
			return syncing;
		});
		requestSender.sendGetVerticesRequest(syncState.author, vertexId, count);
	}

	private void rebuildAndSyncQC(SyncState syncState) {
		log.info("SYNC_STATE: Rebuilding and syncing QC: sync={} curRoot={}", syncState, vertexStore.getRoot());

		// TODO: check if there are any vertices which haven't been local sync processed yet
		if (requiresLedgerSync(syncState)) {
			syncState.fetched.sort(Comparator.comparing(VerifiedVertex::getView));
			List<VerifiedVertex> nonRootVertices = syncState.fetched.stream().skip(1).collect(Collectors.toList());
			vertexStore.rebuild(syncState.fetched.get(0), syncState.fetched.get(1).getQC(), syncState.syncInfo().highestCommittedQC(), nonRootVertices);
		} else {
			log.info("SYNC_STATE: skipping rebuild");
		}

		// At this point we are guaranteed to be in sync with the committed state
		this.syncing.remove(syncState.localSyncId);
		this.syncToQC(syncState.syncInfo(), syncState.author);
	}

	private void processVerticesResponseForCommittedSync(SyncState syncState, GetVerticesResponse response) {
		log.info("SYNC_STATE: Processing vertices {} View {} From {}", syncState, response.getVertices().get(0).getView(), response.getSender());

		ImmutableList<BFTNode> signers = ImmutableList.of(syncState.author);
		syncState.fetched.addAll(response.getVertices());

		// TODO: verify actually extends rather than just state version comparison
		if (syncState.committedProof.getStateVersion() <= this.currentLedgerHeader.getStateVersion()) {
			rebuildAndSyncQC(syncState);
		} else {
			syncState.setSyncStage(SyncStage.SYNC_TO_COMMIT);
			ledgerSyncing.compute(syncState.committedProof.getRaw(), (header, syncing) -> {
				if (syncing == null) {
					syncing = new ArrayList<>();
				}
				syncing.add(syncState.localSyncId);
				return syncing;
			});
			LocalSyncRequest localSyncRequest = new LocalSyncRequest(
				syncState.committedProof,
				signers
			);
			syncLedgerRequestSender.sendLocalSyncRequest(localSyncRequest);
		}
	}

	private void processVerticesResponseForQCSync(SyncState syncState, GetVerticesResponse response) {
		VerifiedVertex vertex = response.getVertices().get(0);
		syncState.fetched.addFirst(vertex);
		Hash parentId = vertex.getParentId();

		if (vertexStore.containsVertex(parentId)) {
			// TODO: combine
			for (VerifiedVertex v: syncState.fetched) {
				if (!vertexStore.addQC(v.getQC())) {
					log.info("GET_VERTICES failed: {}", syncState.syncInfo());
					return;
				}

				vertexStore.insertVertex(v);
			}

			// Finish it off
			this.syncing.remove(syncState.localSyncId);
			this.syncToQC(syncState.syncInfo(), syncState.author);
		} else {
			log.info("SYNC_VERTICES: Sending further GetVerticesRequest for syncInfo={} fetched={} root={}",
				syncState.syncInfo(), syncState.fetched.size(), vertexStore.getRoot());

			this.sendBFTSyncRequest(syncState, parentId, 1);
		}
	}

	@Override
	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		// TODO: check response

		log.info("SYNC_VERTICES: Received GetVerticesErrorResponse {} ", response);

		// error response indicates that the node has moved on from last sync so try and sync to a new sync
		this.syncToQC(response.syncInfo(), response.getSender());
	}

	@Override
	public void processGetVerticesResponse(GetVerticesResponse response) {
		// TODO: check response

		log.trace("SYNC_VERTICES: Received GetVerticesResponse {}", response);

		VerifiedVertex firstVertex = response.getVertices().get(0);
		Pair<Hash, Integer> requestInfo = Pair.of(firstVertex.getId(), response.getVertices().size());
		List<Hash> syncs = bftSyncing.remove(requestInfo);
		if (syncs != null) {
			for (Hash syncTo : syncs) {
				SyncState syncState = syncing.get(syncTo);
				if (syncState == null) {
					continue; // sync requirements already satisfied by another sync
				}
				switch (syncState.syncStage) {
					case GET_COMMITTED_VERTICES:
						processVerticesResponseForCommittedSync(syncState, response);
						break;
					case GET_QC_VERTICES:
						processVerticesResponseForQCSync(syncState, response);
						break;
					default:
						throw new IllegalStateException("Unknown sync stage: " + syncState.syncStage);
				}
			}
		}
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
	}

	// TODO: Verify headers match
	@Override
	public void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		log.trace("SYNC_STATE: update {}", ledgerUpdate);

		this.currentLedgerHeader = ledgerUpdate.getTail();

		Collection<List<Hash>> listeners = this.ledgerSyncing.headMap(
			ledgerUpdate.getTail().getRaw(), true
		).values();
		Iterator<List<Hash>> listenersIterator = listeners.iterator();
		while (listenersIterator.hasNext()) {
			List<Hash> syncs = listenersIterator.next();
			for (Hash syncTo : syncs) {
				SyncState syncState = syncing.get(syncTo);
				if (syncState != null) {
					rebuildAndSyncQC(syncState);
				}
			}
			listenersIterator.remove();
		}
	}
}

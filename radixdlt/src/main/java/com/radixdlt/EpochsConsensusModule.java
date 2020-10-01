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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.bft.ViewTimeoutSigner;
import com.radixdlt.consensus.bft.SignedViewTimeoutToLeaderSender;
import com.radixdlt.consensus.bft.SignedViewTimeoutToLeaderSender.BFTViewTimeoutSender;
import com.radixdlt.consensus.epoch.ProposerElectionFactory;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.VertexStoreFactory;
import com.radixdlt.consensus.epoch.BFTSyncFactory;
import com.radixdlt.consensus.epoch.BFTSyncRequestProcessorFactory;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.ProceedToViewSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.counters.SystemCounters;
import java.util.Comparator;

/**
 * Module which allows for consensus to have multiple epochs
 */
public class EpochsConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;
	private final long pacemakerTimeout;
	private final double pacemakerRate;
	private final int pacemakerMaxExponent;

	public EpochsConsensusModule(long pacemakerTimeout, double pacemakerRate, int pacemakerMaxExponent) {
		this.pacemakerTimeout = pacemakerTimeout;
		this.pacemakerRate = pacemakerRate;
		this.pacemakerMaxExponent = pacemakerMaxExponent;
	}

	@Override
	protected void configure() {
		bind(EpochManager.class).in(Scopes.SINGLETON);
	}

	@Provides
	private PacemakerTimeoutSender initialTimeoutSender(LocalTimeoutSender localTimeoutSender, EpochChange initialEpoch) {
		return (view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(initialEpoch.getEpoch(), view), ms);
	}

	@Provides
	private PacemakerInfoSender initialInfoSender(EpochInfoSender epochInfoSender, EpochChange initialEpoch, ProposerElection proposerElection) {
		return new PacemakerInfoSender() {
			@Override
			public void sendCurrentView(View view) {
				epochInfoSender.sendCurrentView(EpochView.of(initialEpoch.getEpoch(), view));
			}

			@Override
			public void sendTimeoutProcessed(View view) {
				BFTNode leader = proposerElection.getProposer(view);
				Timeout timeout = new Timeout(EpochView.of(initialEpoch.getEpoch(), view), leader);
				epochInfoSender.sendTimeoutProcessed(timeout);
			}
		};
	}

	// TODO: Load from storage
	@Provides
	private EpochChange initialEpoch(
		VerifiedLedgerHeaderAndProof proof,
		BFTConfiguration initialBFTConfig
	) {
		return new EpochChange(proof, initialBFTConfig);
	}

	@Provides
	private ProposerElectionFactory proposerElectionFactory() {
		return validatorSet -> new WeightedRotatingLeaders(
			validatorSet,
			Comparator.comparing(v -> v.getNode().getKey().euid()),
			ROTATING_WEIGHTED_LEADERS_CACHE_SIZE
		);
	}

	@Provides
	private PacemakerFactory pacemakerFactory(ViewTimeoutSigner viewTimeoutSigner, BFTViewTimeoutSender bftViewTimeoutSender) {
		return (timeoutSender, infoSender, proposerElection, genesisQC) -> {
			final ProceedToViewSender proceedToViewSender = new SignedViewTimeoutToLeaderSender(
				viewTimeoutSigner,
				proposerElection,
				bftViewTimeoutSender
			);
			return new ExponentialTimeoutPacemaker(
				this.pacemakerTimeout,
				this.pacemakerRate,
				this.pacemakerMaxExponent,
				proceedToViewSender,
				timeoutSender,
				infoSender,
				genesisQC
			);
		};
	}

	@Provides
	private BFTSyncRequestProcessorFactory vertexStoreSyncVerticesRequestProcessorFactory(
		SyncVerticesResponseSender syncVerticesResponseSender
	) {
		return vertexStore -> new VertexStoreBFTSyncRequestProcessor(vertexStore, syncVerticesResponseSender);
	}

	@Provides
	private BFTSyncFactory bftSyncFactory(
		SyncVerticesRequestSender requestSender,
		SyncLedgerRequestSender syncLedgerRequestSender,
		BFTConfiguration configuration
	) {
		return (vertexStore, pacemaker) -> new BFTSync(
			vertexStore,
			pacemaker,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			requestSender,
			syncLedgerRequestSender,
			configuration.getGenesisHeader()
		);
	}

	@Provides
	private VertexStoreFactory vertexStoreFactory(
		BFTUpdateSender updateSender,
		SystemCounters counters
	) {
		return (genesisVertex, genesisQC, ledger, vertexStoreEventSender) -> new VertexStore(
			genesisVertex,
			genesisQC,
			ledger,
			updateSender,
			vertexStoreEventSender,
			counters
		);
	}
}


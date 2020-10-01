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

package com.radixdlt.integration.distributed.deterministic.network;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.radixdlt.ConsensusModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.SignedViewTimeoutToLeaderSender.BFTViewTimeoutSender;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.epochs.EpochChangeManager.EpochsLedgerUpdateSender;
import com.radixdlt.integration.distributed.deterministic.DeterministicConsensusRunner;
import com.radixdlt.integration.distributed.deterministic.DeterministicNetworkModule;
import com.radixdlt.integration.distributed.MockedCryptoModule;
import com.radixdlt.utils.Pair;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 */
public final class DeterministicNetwork {
	private static final Logger log = LogManager.getLogger();

	public interface DeterministicSender extends
		BFTEventSender,
		BFTViewTimeoutSender,
		VertexStoreEventSender,
		SyncVerticesRequestSender,
		SyncVerticesResponseSender,
		BFTUpdateSender,
		EpochsLedgerUpdateSender,
		LocalTimeoutSender,
		SyncEpochsRPCSender {
		// Aggregation, no additional stuff
	}

	private final MessageQueue messageQueue = new MessageQueue();
	private final MessageSelector messageSelector;
	private final MessageMutator messageMutator;

	private final ImmutableBiMap<BFTNode, Integer> nodeLookup;
	private final ImmutableList<Injector> nodeInstances;

	/**
	 * Create a BFT test network for deterministic tests.
	 * @param nodes The nodes on the network
	 * @param messageSelector A {@link MessageSelector} for choosing messages to process next
	 * @param messageMutator A {@link MessageMutator} for mutating and queueing messages
	 * @param syncExecutionModules Guice modules to use for specifying sync execution sub-system
	 */
	public DeterministicNetwork(
		List<BFTNode> nodes,
		MessageSelector messageSelector,
		MessageMutator messageMutator,
		Collection<Module> syncExecutionModules,
		Module overrideModule
	) {
		this.messageSelector = Objects.requireNonNull(messageSelector);
		this.messageMutator = Objects.requireNonNull(messageMutator);
		this.nodeLookup = Streams.mapWithIndex(
			nodes.stream(),
			(node, index) -> Pair.of(node, (int) index)
		).collect(ImmutableBiMap.toImmutableBiMap(Pair::getFirst, Pair::getSecond));
		this.nodeInstances = Streams.mapWithIndex(
			nodes.stream(),
			(node, index) -> createBFTInstance(node, (int) index, syncExecutionModules, overrideModule)
		).collect(ImmutableList.toImmutableList());

		log.debug("Nodes {}", this.nodeLookup);
	}

	/**
	 * Create the network sender for the specified node.
	 * @param nodeIndex The node index/id that this sender is for
	 * @return A newly created {@link DeterministicSender} for the specified node
	 */
	public DeterministicSender createSender(BFTNode self, int nodeIndex) {
		return new ControlledSender(this, self, nodeIndex);
	}

	public void run() {
		List<DeterministicConsensusRunner> consensusRunners = this.nodeInstances.stream()
			.map(i -> i.getInstance(DeterministicConsensusRunner.class))
			.collect(Collectors.toList());

		consensusRunners.forEach(DeterministicConsensusRunner::start);

		while (true) {
			List<ControlledMessage> controlledMessages = this.messageQueue.lowestRankMessages();
			if (controlledMessages.isEmpty()) {
				throw new IllegalStateException("No messages available (Lost Responsiveness)");
			}
			ControlledMessage controlledMessage = this.messageSelector.select(controlledMessages);
			if (controlledMessage == null) {
				// We are done
				break;
			}
			this.messageQueue.remove(controlledMessage);
			int receiver = controlledMessage.channelId().receiverIndex();
			Thread thisThread = Thread.currentThread();
			String oldThreadName = thisThread.getName();
			thisThread.setName(oldThreadName + " " + this.nodeLookup.inverse().get(receiver));
			try {
				log.debug("Received message {}", controlledMessage);
				consensusRunners.get(receiver).handleMessage(controlledMessage.message());
			} finally {
				thisThread.setName(oldThreadName);
			}
		}
	}

	public int numNodes() {
		return this.nodeInstances.size();
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return this.nodeInstances.get(nodeIndex).getInstance(SystemCounters.class);
	}

	public void dumpMessages(PrintStream out) {
		this.messageQueue.dump(out);
	}

	int lookup(BFTNode node) {
		return this.nodeLookup.get(node);
	}

	void handleMessage(MessageRank rank, ControlledMessage controlledMessage) {
		log.debug("Sent message {}", controlledMessage);
		if (!this.messageMutator.mutate(rank, controlledMessage, this.messageQueue)) {
			// If nothing processes this message, we just add it to the queue
			this.messageQueue.add(rank, controlledMessage);
		}
	}

	private Injector createBFTInstance(BFTNode self, int index, Collection<Module> syncExecutionModules, Module overrideModule) {
		Module module = Modules.combine(
			// An arbitrary timeout for the pacemaker, as time is handled differently
			// in a deterministic test.
			new ConsensusModule(1, 2.0, 63),
			new MockedCryptoModule(),
			new DeterministicNetworkModule(self, createSender(self, index)),
			Modules.combine(syncExecutionModules)
		);
		if (overrideModule != null) {
			module = Modules.override(module).with(overrideModule);
		}
		return Guice.createInjector(module);
	}
}

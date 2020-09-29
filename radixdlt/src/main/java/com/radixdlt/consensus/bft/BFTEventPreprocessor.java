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

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.RequiresSyncConsensusEvent;
import com.radixdlt.consensus.ViewTimeoutSigned;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.bft.SyncQueues.SyncQueue;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.crypto.Hash;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preprocesses consensus events and ensures that the vertexStore is synced to
 * the correct state before they get forwarded to the actual state reducer.
 *
 * This class should not be updating any part of the BFT Safety state besides
 * the VertexStore.
 *
 * A lot of the queue logic could be done more "cleanly" and functionally using
 * lambdas and Functions but the performance impact is too great.
 *
 * This class is NOT thread-safe.
 */
public final class BFTEventPreprocessor implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTNode self;
	private final BFTEventProcessor forwardTo;
	private final BFTSyncer bftSyncer;
	private final PacemakerState pacemakerState;
	private final ProposerElection proposerElection;
	private final SyncQueues queues;

	public BFTEventPreprocessor(
		BFTNode self,
		BFTEventProcessor forwardTo,
		PacemakerState pacemakerState,
		BFTSyncer bftSyncer,
		ProposerElection proposerElection,
		SyncQueues queues
	) {
		this.self = Objects.requireNonNull(self);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.bftSyncer = Objects.requireNonNull(bftSyncer);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.queues = queues;
		this.forwardTo = forwardTo;
	}

	// TODO: Cleanup
	// TODO: remove queues and treat each message independently
	private boolean clearAndExecute(SyncQueue queue, View view) {
		final RequiresSyncConsensusEvent event = queue.clearViewAndGetNext(view);
		if (event == null) {
			return false;
		}

		// Explicitly using switch case method here rather than functional method
		// to process these events due to much better performance
		if (event instanceof ViewTimeoutSigned) {
			final ViewTimeoutSigned newView = (ViewTimeoutSigned) event;
			return this.processNewViewInternal(newView);
		}

		if (event instanceof Proposal) {
			final Proposal proposal = (Proposal) event;
			return this.processProposalInternal(proposal);
		}

		throw new IllegalStateException("Unexpected consensus event: " + event);
	}

	private boolean peekAndExecute(SyncQueue queue, Hash vertexId) {
		final RequiresSyncConsensusEvent event = queue.peek(vertexId);
		if (event == null) {
			return false;
		}

		// Explicitly using switch case method here rather than functional method
		// to process these events due to much better performance
		if (event instanceof ViewTimeoutSigned) {
			final ViewTimeoutSigned viewTimeout = (ViewTimeoutSigned) event;
			return this.processNewViewInternal(viewTimeout);
		}

		if (event instanceof Proposal) {
			final Proposal proposal = (Proposal) event;
			return this.processProposalInternal(proposal);
		}

		throw new IllegalStateException("Unexpected consensus event: " + event);
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
		Hash vertexId = update.getInsertedVertex().getId();

		log.trace("LOCAL_SYNC: {}", vertexId);
		for (SyncQueue queue : queues.getQueues()) {
			if (peekAndExecute(queue, vertexId)) {
				queue.pop();
				while (peekAndExecute(queue, null)) {
					queue.pop();
				}
			}
		}

		forwardTo.processBFTUpdate(update);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("VOTE: PreProcessing {}", vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVoteData().getProposed().getView();
		// TODO: currently we don't check view of vote relative to our pacemakerState. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		if (!Objects.equals(proposerElection.getProposer(view), this.self)) {
			log.warn("VOTE: Ignoring confused vote {} for {}", vote.hashCode(), view);
			return;
		}

		forwardTo.processVote(vote);
	}

	private boolean processNewViewInternal(ViewTimeoutSigned newView) {
		log.trace("ViewTimeout: PreProcessing {}", newView);

		// only do something if we're actually the leader for the view
		final View view = newView.view();
		if (!Objects.equals(proposerElection.getProposer(view), this.self)) {
			log.warn("ViewTimeout: Got confused new-view {} for view {}", newView, view);
			return true;
		}

		final View currentView = pacemakerState.getCurrentView();
		if (view.compareTo(currentView) < 0) {
			log.trace("ViewTimeout: Ignoring {} Current is: {}", view, currentView);
			return true;
		}

		SyncResult syncResult = this.bftSyncer.syncToQC(newView.getQC(), newView.getCommittedQC(), newView.getAuthor());
		switch (syncResult) {
			case SYNCED:
				forwardTo.processViewTimeout(newView);
				return true;
			case INVALID:
				return true;
			case IN_PROGRESS:
				return false;
			default:
				throw new IllegalStateException("Unknown syncResult " + syncResult);
		}
	}

	@Override
	public void processViewTimeout(ViewTimeoutSigned viewTimeout) {
		log.trace("ViewTimeout: Queueing {}", viewTimeout);
		if (queues.isEmptyElseAdd(viewTimeout)) {
			if (!processNewViewInternal(viewTimeout)) {
				log.debug("ViewTimeout: Queuing {}, waiting for Sync", viewTimeout);
				queues.add(viewTimeout);
			}
		} else {
			log.trace("ViewTimeout added to queue");
		}
	}

	private boolean processProposalInternal(Proposal proposal) {
		log.trace("PROPOSAL: PreProcessing {}", proposal);

		final View proposedVertexView = proposal.getVertex().getView();
		final View currentView = this.pacemakerState.getCurrentView();
		if (proposedVertexView.compareTo(currentView) < 0) {
			log.trace("PROPOSAL: Ignoring view {}, current is: {}", proposedVertexView, currentView);
			return true;
		}

		SyncResult syncResult = this.bftSyncer.syncToQC(proposal.getQC(), proposal.getCommittedQC(), proposal.getAuthor());
		switch (syncResult) {
			case SYNCED:
				forwardTo.processProposal(proposal);
				return true;
			case INVALID:
				return true;
			case IN_PROGRESS:
				return false;
			default:
				throw new IllegalStateException("Unknown syncResult " + syncResult);
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("PROPOSAL: PreProcessing {}", proposal);
		if (queues.isEmptyElseAdd(proposal) && !processProposalInternal(proposal)) {
			log.debug("PROPOSAL: Queuing {}, waiting for Sync", proposal);
			queues.add(proposal);
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		final View curView = this.pacemakerState.getCurrentView();
		forwardTo.processLocalTimeout(view);
		final View nextView = this.pacemakerState.getCurrentView();
		if (!curView.equals(nextView)) {
			log.debug("{}: LOCAL_TIMEOUT: Clearing Queues: {}", this.self::getSimpleName, () -> queues);
			for (SyncQueue queue : queues.getQueues()) {
				if (clearAndExecute(queue, nextView.previous())) {
					queue.pop();
					while (peekAndExecute(queue, null)) {
						queue.pop();
					}
				}
			}
		}
	}

	@Override
	public void start() {
		forwardTo.start();
	}
}

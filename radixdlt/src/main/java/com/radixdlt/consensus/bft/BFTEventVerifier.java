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

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.TimestampedVoteData;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.ViewTimeoutSigned;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verifies signatures of BFT messages then forwards to the next processor
 */
@SecurityCritical({ SecurityKind.SIG_VERIFY, SecurityKind.GENERAL })
public final class BFTEventVerifier implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTValidatorSet validatorSet;
	private final BFTEventProcessor forwardTo;
	private final Hasher hasher;
	private final HashVerifier verifier;

	public BFTEventVerifier(
		BFTValidatorSet validatorSet,
		BFTEventProcessor forwardTo,
		Hasher hasher,
		HashVerifier verifier
	) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.hasher = Objects.requireNonNull(hasher);
		this.verifier = Objects.requireNonNull(verifier);
		this.forwardTo = forwardTo;
	}

	@Override
	public void start() {
		forwardTo.start();
	}

	@Override
	public void processVote(Vote vote) {
		validAuthor(vote).ifPresent(node ->
			vote.getSignature().ifPresentOrElse(signature -> {
				final TimestampedVoteData voteData = vote.getTimestampedVoteData();
				final Hash voteHash = this.hasher.hash(voteData);
				if (!this.verifier.verify(node.getKey(), voteHash, signature)) {
					if (log.isInfoEnabled()) {
						// Some help for mocks that don't provide this
						BFTHeader proposed = (voteData == null) ? null : voteData.getProposed();
						View view = (proposed == null) ? null : proposed.getView();
						log.info("Vote from author {} in view {} has invalid signature", node, view);
					}
				} else {
					forwardTo.processVote(vote);
				}
			}, () -> {
				if (log.isInfoEnabled()) {
					// Some help for mocks that don't provide this
					TimestampedVoteData voteData = vote.getTimestampedVoteData();
					BFTHeader proposed = (voteData == null) ? null : voteData.getProposed();
					View view = (proposed == null) ? null : proposed.getView();
					log.info("Vote from author {} in view {} is missing signature", node, view);
				}
			})
		);
	}

	@Override
	public void processViewTimeout(ViewTimeoutSigned viewTimeout) {
		validAuthor(viewTimeout).ifPresent(node -> {
			final ECPublicKey key = node.getKey();
			final Hash newViewId = this.hasher.hash(viewTimeout.viewTimeout());
			final ECDSASignature signature = viewTimeout.signature();
			if (!this.verifier.verify(key, newViewId, signature)) {
				log.info("ViewTimeout from author {} in view {} has invalid signature", node, viewTimeout.view());
			} else {
				forwardTo.processViewTimeout(viewTimeout);
			}
		});
	}

	@Override
	public void processProposal(Proposal proposal) {
		validAuthor(proposal).ifPresent(node -> {
			final ECPublicKey key = node.getKey();
			final Hash vertexHash = this.hasher.hash(proposal.getVertex());
			final ECDSASignature signature = proposal.getSignature();
			if (!this.verifier.verify(key, vertexHash, signature)) {
				if (log.isInfoEnabled()) {
					// Some help for mocks that don't provide this
					UnverifiedVertex vertex = proposal.getVertex();
					View view = (vertex == null) ? null : vertex.getView();
					log.info("Proposal from author {} in view {} has invalid signature", node, view);
				}
			} else {
				forwardTo.processProposal(proposal);
			}
		});
	}

	@Override
	public void processLocalTimeout(View view) {
		forwardTo.processLocalTimeout(view);
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
		forwardTo.processBFTUpdate(update);
	}

	private Optional<BFTNode> validAuthor(ConsensusEvent event) {
		BFTNode node = event.getAuthor();
		if (!validatorSet.containsNode(node)) {
			log.warn("CONSENSUS_EVENT: {} from author {} not in validator set {}", event.getClass().getSimpleName(), node, this.validatorSet);
			return Optional.empty();
		}
		return Optional.of(node);
	}
}

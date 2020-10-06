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

import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposerElection;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sends a signed view timeout message to the leader of the view
 */
public final class SignedViewTimeoutToLeaderSender implements ProceedToViewSender {
	private static final Logger log = LogManager.getLogger();

	public interface BFTViewTimeoutSender {
		/**
		 * Send a view timeotu message to a given validator
		 * @param viewTimeout the view timeout message
		 * @param nextLeader the validator the message gets sent to
		 */
		void sendViewTimeout(ViewTimeout viewTimeout, BFTNode nextLeader);
	}

	private final ViewTimeoutSigner viewTimeoutSigner;
	private final ProposerElection proposerElection;
	private final BFTViewTimeoutSender sender;

	public SignedViewTimeoutToLeaderSender(
		ViewTimeoutSigner viewTimeoutSigner,
		ProposerElection proposerElection,
		BFTViewTimeoutSender sender
	) {
		this.viewTimeoutSigner = Objects.requireNonNull(viewTimeoutSigner);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.sender = Objects.requireNonNull(sender);
	}

	@Override
	public void sendProceedToNextView(View nextView, SyncInfo syncInfo) {
		ViewTimeout viewTimeout = viewTimeoutSigner.signViewTimeout(nextView, syncInfo);
		BFTNode nextLeader = this.proposerElection.getProposer(nextView);
		log.trace("Sending ViewTimeout to {}: {}", nextLeader, viewTimeout);
		this.sender.sendViewTimeout(viewTimeout, nextLeader);
	}
}

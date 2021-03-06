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

package com.radixdlt.epochs;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.LedgerUpdate;
import java.util.Objects;
import java.util.Optional;

/**
 * A ledger update with possible epoch related information
 */
public final class EpochsLedgerUpdate implements LedgerUpdate {
	private final LedgerUpdate base;
	private final EpochChange epochChange;

	EpochsLedgerUpdate(LedgerUpdate base, EpochChange epochChange) {
		this.base = Objects.requireNonNull(base);
		this.epochChange = epochChange;
	}

	public Optional<EpochChange> getEpochChange() {
		return Optional.ofNullable(epochChange);
	}

	@Override
	public ImmutableList<Command> getNewCommands() {
		return base.getNewCommands();
	}

	@Override
	public VerifiedLedgerHeaderAndProof getTail() {
		return base.getTail();
	}

	@Override
	public Optional<BFTValidatorSet> getNextValidatorSet() {
		return base.getNextValidatorSet();
	}

	@Override
	public String toString() {
		return String.format("%s{numCmds=%s}", this.getClass().getSimpleName(), base.getNewCommands().size());
	}
}

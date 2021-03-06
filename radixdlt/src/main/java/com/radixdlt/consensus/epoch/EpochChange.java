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

package com.radixdlt.consensus.epoch;

import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import java.util.Objects;

/**
 * An epoch change message to consensus
 */
public final class EpochChange {
	private final VerifiedLedgerHeaderAndProof proof;
	private final BFTConfiguration bftConfiguration;

	public EpochChange(VerifiedLedgerHeaderAndProof proof, BFTConfiguration bftConfiguration) {
		this.proof = Objects.requireNonNull(proof);
		this.bftConfiguration = Objects.requireNonNull(bftConfiguration);
	}

	public BFTConfiguration getBFTConfiguration() {
		return bftConfiguration;
	}

	public long getEpoch() {
		return proof.getEpoch() + 1;
	}

	public VerifiedLedgerHeaderAndProof getProof() {
		return proof;
	}

	@Override
	public String toString() {
		return String.format(
			"%s{proof=%s config=%s}", this.getClass().getSimpleName(), proof, bftConfiguration
		);
	}
}

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

package com.radixdlt.middleware2.store;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores epoch proofs in memory
 */
public final class InMemoryCommittedEpochProofsStore {
	private final ConcurrentHashMap<Long, VerifiedLedgerHeaderAndProof> epochProofs = new ConcurrentHashMap<>();

	public void commit(VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof) {
		if (!verifiedLedgerHeaderAndProof.isEndOfEpoch()) {
			throw new IllegalArgumentException("Proof must be end of epoch");
		}

		epochProofs.put(verifiedLedgerHeaderAndProof.getEpoch() + 1, verifiedLedgerHeaderAndProof);
	}

	public VerifiedLedgerHeaderAndProof getEpochProof(long epoch) {
		return epochProofs.get(epoch);
	}
}
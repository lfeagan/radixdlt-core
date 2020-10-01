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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.ViewTimeoutSigned;
import com.radixdlt.crypto.ECDSASignature;
import java.util.Objects;

/**
 * Creates and signs a view timeout message
 */
public final class ViewTimeoutSigner {
	private final HashSigner signer;
	private final Hasher hasher;
	private final BFTNode self;

	@Inject
	public ViewTimeoutSigner(@Named("self") BFTNode self, Hasher hasher, HashSigner signer) {
		this.self = Objects.requireNonNull(self);
		this.hasher = Objects.requireNonNull(hasher);
		this.signer = Objects.requireNonNull(signer);
	}

	/**
	 * Create a signed view timeout
	 * @param nextView the view of the view timeout
	 * @param syncInfo {@link SyncInfo} for highest QC's
	 * @return a signed view timeout
	 */
	public ViewTimeoutSigned signViewTimeout(View nextView, SyncInfo syncInfo) {
		ViewTimeout timeout = new ViewTimeout(this.self, syncInfo.highestQC().getProposed().getLedgerHeader().getEpoch(), nextView);
		ECDSASignature signature = this.signer.sign(this.hasher.hash(timeout));
		return new ViewTimeoutSigned(timeout, syncInfo, signature);
	}
}

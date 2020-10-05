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

import javax.annotation.Nullable;

/**
 * Synchronizes the state in order for consensus preparation
 * TODO: Fix interfaces as it seems like a still rather awkward interface
 */
public interface BFTSyncer {
	enum SyncResult {
		SYNCED,
		IN_PROGRESS,
		INVALID
	}

	/**
	 * Initiate a sync to a given QC and a committedQC. Returns true if already synced
	 * otherwise will initiate a syncing process.
	 * An author is used because the author will most likely have the corresponding vertices
	 * still in memory.
	 *
	 * @param syncInfo the information on QC's to sync to
	 * @param author the original author of the qc
	 * @return a {@link SyncResult} indicating the current sync state
	 */
	SyncResult syncToQC(SyncInfo syncInfo, @Nullable BFTNode author);
}

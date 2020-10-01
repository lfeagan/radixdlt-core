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

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import org.radix.network.messaging.Message;

@SerializerId2("message.consensus.vertices_error_response")
public class GetVerticesErrorResponseMessage extends Message {
	@JsonProperty("sync_info")
	@DsonOutput(Output.ALL)
	private final SyncInfo syncInfo;

	GetVerticesErrorResponseMessage() {
		// Serializer only
		super(0);
		this.syncInfo = null;
	}

	GetVerticesErrorResponseMessage(int magic, SyncInfo syncInfo) {
		super(magic);
		this.syncInfo = Objects.requireNonNull(syncInfo);
	}

	public SyncInfo syncInfo() {
		return this.syncInfo;
	}

	public QuorumCertificate getHighestQC() {
		return this.syncInfo.highestQC();
	}

	public QuorumCertificate getHighestCommittedQC() {
		return this.syncInfo.highestCommittedQC();
	}

	@Override
	public String toString() {
		return String.format("%s{%s}", getClass().getSimpleName(), this.syncInfo);
	}
}

/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

/**
 * Represents a view timeout message from the pacemaker
 */
@Immutable
@SerializerId2("consensus.view_timeout_signed")
public final class ViewTimeoutSigned implements RequiresSyncConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("view_timeout")
	@DsonOutput(Output.ALL)
	private final ViewTimeout viewTimeout;

	@JsonProperty("sync_info")
	@DsonOutput(Output.ALL)
	private final SyncInfo syncInfo;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature;

	public static ViewTimeoutSigned from(ViewTimeout viewTimeout, SyncInfo syncInfo, ECDSASignature signature) {
		return new ViewTimeoutSigned(viewTimeout, syncInfo, signature);
	}

	@JsonCreator
	private ViewTimeoutSigned(
		@JsonProperty("view_timeout") ViewTimeout viewTimeout,
		@JsonProperty("sync_info") SyncInfo syncInfo,
		@JsonProperty("signature") ECDSASignature signature
	) {
		this.viewTimeout = Objects.requireNonNull(viewTimeout);
		this.syncInfo = Objects.requireNonNull(syncInfo);
		this.signature = Objects.requireNonNull(signature);
	}

	@Override
	public BFTNode getAuthor() {
		return this.viewTimeout.author();
	}

	@Override
	public long getEpoch() {
		return this.viewTimeout.epoch();
	}

	public View view() {
		return this.viewTimeout.view();
	}

	public SyncInfo syncInfo() {
		return this.syncInfo;
	}

	@Override
	public QuorumCertificate getCommittedQC() {
		return this.syncInfo.highestCommittedQC();
	}

	@Override
	public QuorumCertificate getQC() {
		return this.syncInfo.highestQC();
	}

	@Override
	public View getView() {
		return this.view();
	}

	public ViewTimeout viewTimeout() {
		return this.viewTimeout;
	}

	public ECDSASignature signature() {
		return this.signature;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof ViewTimeoutSigned) {
			ViewTimeoutSigned that = (ViewTimeoutSigned) o;
			return Objects.equals(this.viewTimeout, that.viewTimeout)
				&& Objects.equals(this.syncInfo, that.syncInfo)
				&& Objects.equals(this.signature, that.signature);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.viewTimeout, this.syncInfo, this.signature);
	}

	@Override
	public String toString() {
		return String.format("%s{author=%s epoch=%s view=%s qc=%s}",
			getClass().getSimpleName(), this.getAuthor(), this.getEpoch(), this.view(), this.getQC());
	}
}

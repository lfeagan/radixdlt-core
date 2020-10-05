/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * Current state of synchronisation for sending node.
 */
@Immutable
@SerializerId2("consensus.sync_info")
public final class SyncInfo {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("highest_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate highestQC;

	@JsonProperty("committed_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate highestCommittedQC;

	@JsonCreator
	private static SyncInfo serializerCreate(
		@JsonProperty("highest_qc") QuorumCertificate highestQC,
		@JsonProperty("committed_qc") QuorumCertificate highestCommittedQC
	) {
		return (highestCommittedQC == null) ? new SyncInfo(highestQC, highestQC) : new SyncInfo(highestQC, highestCommittedQC);
	}

	public SyncInfo(QuorumCertificate highestQC, QuorumCertificate highestCommittedQC) {
		this.highestQC = Objects.requireNonNull(highestQC);
		Objects.requireNonNull(highestCommittedQC);
		// Don't include separate committedQC if it is the same view as highQC
		if (highestQC.equals(highestCommittedQC)) {
			this.highestCommittedQC = null;
		} else {
			this.highestCommittedQC = highestCommittedQC;
		}
	}

	public QuorumCertificate highestQC() {
		return this.highestQC;
	}

	public QuorumCertificate highestCommittedQC() {
		return this.highestCommittedQC == null ? this.highestQC : this.highestCommittedQC;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.highestQC, this.highestCommittedQC);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof SyncInfo) {
			SyncInfo that = (SyncInfo) o;
			return Objects.equals(this.highestCommittedQC, that.highestCommittedQC)
				&& Objects.equals(this.highestQC, that.highestQC);
		}
		return false;
	}

	@Override
	public String toString() {
		String highestCommittedString = (this.highestCommittedQC == null) ? "<same>" : this.highestCommittedQC.toString();
		return String.format("%s[highest=%s, highestCommitted=%s]",
			getClass().getSimpleName(), this.highestQC, highestCommittedString);
	}
}

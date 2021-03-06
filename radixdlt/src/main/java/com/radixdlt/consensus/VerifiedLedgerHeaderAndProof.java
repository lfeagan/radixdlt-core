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

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Comparator;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * Ledger header with proof
 */
@Immutable
@SerializerId2("ledger.verified_ledger_header_and_proof")
public final class VerifiedLedgerHeaderAndProof {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	// proposed
	@JsonProperty("opaque0")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque0;

	// parent
	@JsonProperty("opaque1")
	@DsonOutput(Output.ALL)
	private final BFTHeader opaque1;

	// committed view
	@JsonProperty("opaque2")
	@DsonOutput(Output.ALL)
	private final long opaque2;

	// committed vertexId
	@JsonProperty("opaque3")
	@DsonOutput(Output.ALL)
	private final Hash opaque3;

	// committed ledgerState
	@JsonProperty("ledgerState")
	@DsonOutput(Output.ALL)
	private final LedgerHeader ledgerHeader;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final TimestampedECDSASignatures signatures;

	@JsonCreator
	public VerifiedLedgerHeaderAndProof(
		@JsonProperty("opaque0") BFTHeader opaque0,
		@JsonProperty("opaque1") BFTHeader opaque1,
		@JsonProperty("opaque2") long opaque2,
		@JsonProperty("opaque3") Hash opaque3,
		@JsonProperty("ledgerState") LedgerHeader ledgerHeader,
		@JsonProperty("signatures") TimestampedECDSASignatures signatures
	) {
		this.opaque0 = Objects.requireNonNull(opaque0);
		this.opaque1 = Objects.requireNonNull(opaque1);
		this.opaque2 = opaque2;
		this.opaque3 = Objects.requireNonNull(opaque3);
		this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public static VerifiedLedgerHeaderAndProof genesis(Hash accumulator) {
		LedgerHeader genesisLedgerHeader = LedgerHeader.genesis(accumulator);
		BFTHeader header = BFTHeader.ofGenesisAncestor(genesisLedgerHeader);
		return new VerifiedLedgerHeaderAndProof(
			header,
			header,
			0,
			Hash.ZERO_HASH,
			genesisLedgerHeader,
			new TimestampedECDSASignatures()
		);
	}

	public static final class OrderByEpochAndVersionComparator implements Comparator<VerifiedLedgerHeaderAndProof> {
		@Override
		public int compare(VerifiedLedgerHeaderAndProof p0, VerifiedLedgerHeaderAndProof p1) {
			if (p0.ledgerHeader.getEpoch() != p1.ledgerHeader.getEpoch()) {
				return p0.ledgerHeader.getEpoch() > p1.ledgerHeader.getEpoch() ? 1 : -1;
			}

			if (p0.ledgerHeader.isEndOfEpoch() != p1.ledgerHeader.isEndOfEpoch()) {
				return p0.ledgerHeader.isEndOfEpoch() ? 1 : -1;
			}

			return Long.compare(
				p0.ledgerHeader.getAccumulatorState().getStateVersion(),
				p1.ledgerHeader.getAccumulatorState().getStateVersion()
			);
		}
	}

	public DtoLedgerHeaderAndProof toDto() {
		return new DtoLedgerHeaderAndProof(
			opaque0,
			opaque1,
			opaque2,
			opaque3,
			ledgerHeader,
			signatures
		);
	}

	public LedgerHeader getRaw() {
		return ledgerHeader;
	}

	public long getEpoch() {
		return ledgerHeader.getEpoch();
	}

	public View getView() {
		return ledgerHeader.getView();
	}

	public AccumulatorState getAccumulatorState() {
		return ledgerHeader.getAccumulatorState();
	}

	// TODO: Remove
	public long getStateVersion() {
		return ledgerHeader.getAccumulatorState().getStateVersion();
	}

	public long timestamp() {
		return ledgerHeader.timestamp();
	}

	public boolean isEndOfEpoch() {
		return ledgerHeader.isEndOfEpoch();
	}

	public TimestampedECDSASignatures getSignatures() {
		return signatures;
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque0, opaque1, opaque2, opaque3, ledgerHeader, signatures);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedLedgerHeaderAndProof)) {
			return false;
		}

		VerifiedLedgerHeaderAndProof other = (VerifiedLedgerHeaderAndProof) o;
		return Objects.equals(this.opaque0, other.opaque0)
			&& Objects.equals(this.opaque1, other.opaque1)
			&& this.opaque2 == other.opaque2
			&& Objects.equals(this.opaque3, other.opaque3)
			&& Objects.equals(this.ledgerHeader, other.ledgerHeader)
			&& Objects.equals(this.signatures, other.signatures);
	}

	@Override
	public String toString() {
		return String.format("%s{ledger=%s}", this.getClass().getSimpleName(), this.ledgerHeader);
	}
}

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
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.crypto.ECPublicKey;
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
@SerializerId2("consensus.view_timeout")
public final class ViewTimeout implements RequiresSyncConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private final BFTNode author;

	private final View view;

	@JsonProperty("qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate qc;

	@JsonProperty("committed_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate committedQC;

	@JsonCreator
	ViewTimeout(
		@JsonProperty("author") byte[] author,
		@JsonProperty("view") long view,
		@JsonProperty("qc") QuorumCertificate qc,
		@JsonProperty("committed_qc") QuorumCertificate committedQC
	) throws PublicKeyException {
		this(BFTNode.create(ECPublicKey.fromBytes(author)), View.of(view), qc, committedQC);
	}

	/**
	 * Signals to the receiver that a view timeout has occurred for the author.
	 *
	 * @param author The author of the timeout message
	 * @param view The view that timed out
	 * @param qc The highest QC that the author has seen
	 * @param committedQC The highest committed QC that the author has seen
	 */
	public ViewTimeout(BFTNode author, View view, QuorumCertificate qc, QuorumCertificate committedQC) {
		this.author = Objects.requireNonNull(author);
		this.view = Objects.requireNonNull(view);
		this.qc = Objects.requireNonNull(qc);
		this.committedQC = committedQC;
	}

	@Override
	public long getEpoch() {
		return this.qc.getProposed().getLedgerHeader().getEpoch();
	}

	@Override
	public QuorumCertificate getCommittedQC() {
		return this.committedQC;
	}

	@Override
	public QuorumCertificate getQC() {
		return this.qc;
	}

	@Override
	public BFTNode getAuthor() {
		return author;
	}

	@Override
	public View getView() {
		return view;
	}

	public View view() {
		return this.view;
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getKey().getBytes();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof ViewTimeout) {
			ViewTimeout newView = (ViewTimeout) o;
			return Objects.equals(this.author, newView.author)
				&& Objects.equals(this.view, newView.view)
				&& Objects.equals(this.qc, newView.qc)
				&& Objects.equals(this.committedQC, newView.committedQC);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.view, this.qc, this.committedQC);
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s author=%s qc=%s}",
			getClass().getSimpleName(), this.getEpoch(), this.view, this.author, this.qc);
	}
}

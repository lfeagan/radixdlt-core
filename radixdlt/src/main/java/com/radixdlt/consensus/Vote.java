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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a vote on a vertex
 */
@Immutable
@SerializerId2("consensus.vote")
public final class Vote implements RequiresSyncConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private final BFTNode author;

	@JsonProperty("sync_info")
	@DsonOutput(Output.ALL)
	private final SyncInfo syncInfo;

	@JsonProperty("vote_data")
	@DsonOutput(Output.ALL)
	private final TimestampedVoteData voteData;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature; // may be null if not signed (e.g. for genesis)

	@JsonCreator
	private Vote(
		@JsonProperty("author") byte[] author,
		@JsonProperty("sync_info") SyncInfo syncInfo,
		@JsonProperty("vote_data") TimestampedVoteData voteData,
		@JsonProperty("signature") ECDSASignature signature
	) throws PublicKeyException {
		this(BFTNode.create(ECPublicKey.fromBytes(author)), syncInfo, voteData, signature);
	}

	public Vote(BFTNode author, SyncInfo syncInfo, TimestampedVoteData voteData, ECDSASignature signature) {
		this.author = Objects.requireNonNull(author);
		this.syncInfo = Objects.requireNonNull(syncInfo);
		this.voteData = Objects.requireNonNull(voteData);
		this.signature = signature;
	}

	@Override
	public BFTNode getAuthor() {
		return this.author;
	}

	@Override
	public long getEpoch() {
		return this.voteData.getVoteData().getProposed().getLedgerHeader().getEpoch();
	}

	@Override
	public View getView() {
		return this.voteData.getVoteData().getProposed().getLedgerHeader().getView();
	}

	@Override
	public QuorumCertificate getQC() {
		return this.syncInfo.highestQC();
	}

	@Override
	public QuorumCertificate getCommittedQC() {
		return this.syncInfo.highestCommittedQC();
	}

	public VoteData getVoteData() {
		return voteData.getVoteData();
	}

	public TimestampedVoteData getTimestampedVoteData() {
		return voteData;
	}

	public Optional<ECDSASignature> getSignature() {
		return Optional.ofNullable(this.signature);
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getKey().getBytes();
	}

	@Override
	public String toString() {
		return String.format("%s{author=%s, epoch=%s view=%s sync=%s}", getClass().getSimpleName(),
			this.author, this.getEpoch(), this.getView(), this.syncInfo);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.syncInfo, this.voteData, this.signature);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Vote) {
			Vote that = (Vote) o;
			return Objects.equals(this.author, that.author)
				&& Objects.equals(this.syncInfo, that.syncInfo)
				&& Objects.equals(this.voteData, that.voteData)
				&& Objects.equals(this.signature, that.signature);
		}
		return false;
	}
}

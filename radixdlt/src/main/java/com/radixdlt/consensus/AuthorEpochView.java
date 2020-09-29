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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * To be signed by a node on timeout.
 */
@SerializerId2("consensus.author_epoch_view")
public class AuthorEpochView {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private final BFTNode author;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private final View view;

	@JsonCreator
	private AuthorEpochView(
		@JsonProperty("author") byte[] authorKey,
		@JsonProperty("epoch") long epoch,
		@JsonProperty("view") long view
	) throws PublicKeyException {
		this(BFTNode.create(ECPublicKey.fromBytes(authorKey)), epoch, View.of(view));
	}

	/**
	 * Create an author, epoch, view triple for signing on timeout.
	 *
	 * @param author the author that will be signing the timeout
	 * @param epoch the epoch the timeout occurred in
	 * @param view the view the timeout occurred in
	 */
	public AuthorEpochView(BFTNode author, long epoch, View view) {
		this.author  = Objects.requireNonNull(author);
		this.epoch = epoch;
		this.view = Objects.requireNonNull(view);
	}

	public BFTNode author() {
		return this.author;
	}

	public long epoch() {
		return this.epoch;
	}

	public View view() {
		return this.view;
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getKey().getBytes();
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}
}

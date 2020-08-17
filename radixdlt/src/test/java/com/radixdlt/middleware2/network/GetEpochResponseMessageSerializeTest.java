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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import org.radix.serialization.SerializeMessageObject;

public class GetEpochResponseMessageSerializeTest extends SerializeMessageObject<GetEpochResponseMessage> {
	public GetEpochResponseMessageSerializeTest() {
		super(GetEpochResponseMessage.class, GetEpochResponseMessageSerializeTest::get);
	}

	private static GetEpochResponseMessage get() {
		BFTNode author = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		ECKeyPair keyPair = ECKeyPair.generateNew();
		BFTValidatorSet bftValidatorSet = BFTValidatorSet.from(ImmutableSet.of(
			BFTValidator.from(BFTNode.create(keyPair.getPublicKey()), UInt256.ONE)
		));
		return new GetEpochResponseMessage(author, 12345, VertexMetadata.ofGenesisAncestor(bftValidatorSet));
	}
}

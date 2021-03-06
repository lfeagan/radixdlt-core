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

import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.crypto.Hash;
import org.radix.serialization.SerializeMessageObject;

public class GetVerticesErrorResponseMessageSerializeTest extends SerializeMessageObject<GetVerticesErrorResponseMessage> {
	public GetVerticesErrorResponseMessageSerializeTest() {
		super(GetVerticesErrorResponseMessage.class, GetVerticesErrorResponseMessageSerializeTest::get);
	}

	private static GetVerticesErrorResponseMessage get() {
		LedgerHeader ledgerHeader = LedgerHeader.genesis(Hash.ZERO_HASH);
		return new GetVerticesErrorResponseMessage(
			12345,
			QuorumCertificate.ofGenesis(new VerifiedVertex(UnverifiedVertex.createGenesis(ledgerHeader), Hash.ZERO_HASH), ledgerHeader),
			QuorumCertificate.ofGenesis(new VerifiedVertex(UnverifiedVertex.createGenesis(ledgerHeader), Hash.ZERO_HASH), ledgerHeader)
		);
	}
}

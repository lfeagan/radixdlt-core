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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.UInt256;
import java.util.function.Function;
import java.util.stream.Stream;

public class MockedBFTConfigurationModule extends AbstractModule {

	@Provides
	Function<BFTNode, BFTConfiguration> config(Stream<BFTNode> nodes) {
		BFTValidatorSet validatorSet = BFTValidatorSet.from(nodes.map(node -> BFTValidator.from(node, UInt256.ONE)));
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(Hash.ZERO_HASH));
		VerifiedVertex hashedGenesis = new VerifiedVertex(genesis, Hash.ZERO_HASH);

		return node -> new BFTConfiguration(
			validatorSet,
			hashedGenesis,
			QuorumCertificate.ofGenesis(hashedGenesis, LedgerHeader.genesis(Hash.ZERO_HASH))
		);
	}
}
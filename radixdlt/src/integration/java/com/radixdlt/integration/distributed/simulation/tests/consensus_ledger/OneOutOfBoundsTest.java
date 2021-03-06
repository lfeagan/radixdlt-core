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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

/**
 * Runs checks with a consensus and ledger module across 4 nodes with a single
 * node out of bounds and verifies sanity checks are maintained
 */
public class OneOutOfBoundsTest {
	private final int latency = 50;
	private final int synchronousTimeout = 8 * latency;
	private final int outOfBoundsLatency = synchronousTimeout;
	// TODO: Add 1 timeout check
	private final Builder bftTestBuilder = SimulationTest.builder()
		.ledger()
		.pacemakerTimeout(synchronousTimeout)
		.checkConsensusLiveness("liveness", 2 * synchronousTimeout, TimeUnit.MILLISECONDS)
		.checkConsensusSafety("safety")
		.checkLedgerInOrder("ledgerInOrder")
		.checkLedgerProcessesConsensusCommitted("consensusToLedger");

	/**
	 * Tests a configuration of 1 out of 4 nodes out of synchrony bounds
	 */
	@Test
	public void given_1_out_of_4_nodes_out_of_synchrony_bounds() {
		SimulationTest test = bftTestBuilder
			.numNodesAndLatencies(4, latency, latency, latency, outOfBoundsLatency)
			.build();

		TestResults results = test.run();
		assertThat(results.getCheckResults()).allSatisfy((name, error) -> AssertionsForClassTypes.assertThat(error).isNotPresent());
	}

}

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.radixdlt.consensus.Counters.CounterType;
import org.junit.Test;

public class CountersTest {
	@Test
	public void when_get_count__then_count_should_be_0() {
		Counters counters = new Counters();
		assertThat(counters.getCount(CounterType.TIMEOUT)).isEqualTo(0);
	}

	@Test
	public void when_increment__then_count_should_be_1() {
		Counters counters = new Counters();
		counters.increment(CounterType.TIMEOUT);
		assertThat(counters.getCount(CounterType.TIMEOUT)).isEqualTo(1);
	}
}
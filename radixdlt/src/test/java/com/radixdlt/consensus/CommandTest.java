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

import static org.assertj.core.api.Assertions.assertThat;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class CommandTest {
	private Command command;

	@Before
	public void setUp() {
		this.command = new Command(new byte[] {1, 2, 3});
	}

	@Test
	public void testGetters() {
		assertThat(this.command.getHash()).isNotNull();
		assertThat(this.command.getPayload()).isEqualTo(new byte[] {1, 2, 3});
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Command.class)
			.verify();
	}
}
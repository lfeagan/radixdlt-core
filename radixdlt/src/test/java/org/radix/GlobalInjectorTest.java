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

package org.radix;

import com.radixdlt.consensus.bft.BFTNode;
import java.io.File;

import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.GlobalInjector.LocalSystemProvider;
import org.radix.database.DatabaseEnvironment;
import org.radix.serialization.TestSetupUtils;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GlobalInjectorTest {

	private GlobalInjector globalInjector;

	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testInjectorNotNull() {
		setup("none");
		assertNotNull(this.globalInjector.getInjector());
	}

	@Test
	public void testInjectorNotNullPow() {
		setup("pow");
		assertNotNull(this.globalInjector.getInjector());
	}

	@Test
	public void testInjectorNotNullToken() {
		setup("token");
		assertNotNull(this.globalInjector.getInjector());
	}

	@Test(expected = IllegalStateException.class)
	public void testInjectorInvalidFee() {
		setup("rhinoplasty");
	}

	@Test
	public void testNid() {
		setup("none");
		testSelfInstance(EUID.class);
	}

	@Test
	public void testKeyPair() {
		setup("none");
		testSelfInstance(ECKeyPair.class);
	}

	@Test
	public void testPublicKey() {
		setup("none");
		testSelfInstance(ECPublicKey.class);
	}

	@Test
	public void testAddress() {
		setup("none");
		testSelfInstance(RadixAddress.class);
	}

	@Test
	public void testBFTNode() {
		setup("none");
		testSelfInstance(BFTNode.class);
	}

	@Test
	public void testLocalSystem() {
		setup("none");
		LocalSystemProvider provider = this.globalInjector.getInjector().getInstance(LocalSystemProvider.class);
		LocalSystem localSystem = provider.get();
		assertNotNull(localSystem.getInfo());
	}

	private <T> void testSelfInstance(Class<T> cls) {
		Key<T> key = Key.get(cls, Names.named("self"));
		T obj = this.globalInjector.getInjector().getInstance(key);
		assertNotNull(obj);
	}

	private void setup(String feeType) {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doReturn("127.0.0.1").when(properties).get(eq("host.ip"), any());
		doReturn(feeType).when(properties).get(eq("debug.fee_module"), any());
		DatabaseEnvironment dbEnv = mock(DatabaseEnvironment.class);
		Universe universe = mock(Universe.class);

		Files.delete(new File("nonesuch.ks"));
		when(properties.get(eq("node.key.path"), any(String.class))).thenReturn("nonesuch.ks");

		this.globalInjector = new GlobalInjector(properties, dbEnv, universe);
	}
}

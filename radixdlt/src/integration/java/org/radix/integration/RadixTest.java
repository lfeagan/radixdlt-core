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

package org.radix.integration;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.radix.Radix;
import org.radix.RadixUniverseBuilder;
import org.radix.serialization.TestSetupUtils;
import org.radix.utils.IOUtils;

import java.util.Objects;

public class RadixTest {
	private static Serialization serialization;
	private static String dbLocation = null;
	private static RuntimeProperties properties;
	private static Universe universe;

	@BeforeClass
	public static void startRadixTest() throws Exception {
		TestSetupUtils.installBouncyCastleProvider();

		serialization = DefaultSerialization.getInstance();

		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null) {
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));
		}

		properties = new RuntimeProperties(runtimeConfigurationJSON, null);

		// Tests need this
		properties.set("debug.nopow", true);
		properties.set("host.ip", "127.0.0.1");

		if (dbLocation == null) {
			// Avoid RADIXDB_TEST_TEST_TEST_TEST_TEST situation
			dbLocation = properties.get("db.location", ".//RADIXDB") + "_TEST";
		}
		properties.set("db.location", dbLocation);

		universe = RadixUniverseBuilder.test().build().getSecond();
	}

	@AfterClass
	public static void stopRadixTest() {
		serialization = null;
		dbLocation = null;
		properties = null;
		universe = null;
	}

	protected Serialization getSerialization() {
		return Objects.requireNonNull(serialization, "serialization was not initialized");
	}

	protected RuntimeProperties getProperties() {
		return Objects.requireNonNull(properties, "properties was not initialized");
	}

	protected Universe getUniverse() {
		return Objects.requireNonNull(universe, "universe was not initialized");
	}
}

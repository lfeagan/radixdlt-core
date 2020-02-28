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

package org.radix.benchmark;


import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Util.AssertFailException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.radix.logging.Logging;

import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import org.radix.serialization.TestSetupUtils;

/**
 * Some JMH driven benchmarks for testing serialisation performance of
 * Radix and some third party libraries.
 * <p>
 * Note that the build system has been set up to make it easier to
 * run these performance tests under gradle.  Using gradle, it should
 * be possible to execute:
 * <pre>
 *    $ gradle clean jmh
 * </pre>
 * from the RadixCode/radixdlt directory.  Note that the JMH plugin
 * does not appear to be super robust, and changes to benchmark tests
 * and other code are not always re-instrumented correctly by gradle
 * daemons.  This can be worked around by stopping the daemons before
 * starting the tests using:
 * <pre>
 *    $ gradle --stop && gradle clean jmh
 * </pre>
 * Alternatively, the configuration option {@code org.gradle.daemon=false}
 * can be added to the {@code ~/.gradle/gradle.properties} to completely
 * disable gradle daemons.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
public class SigVerfBenchmark {

	private static final byte[] hash;

	private static final ECKeyPair bckp;
	private static final ECPublicKey bckpub;
	private static final ECSignature bcsignature;

	private static final byte[] bjpriv;
	private static final byte[] bjpub;
	private static final byte[] bjsig;


	static {
		// Disable this output for now, as the serialiser is quite verbose when starting.
		Logging.getLogger().setLevels(Logging.ALL & ~Logging.INFO & ~Logging.TRACE & ~Logging.DEBUG);
		try {
			TestSetupUtils.installBouncyCastleProvider();

			Random r = new Random();
			hash = new byte[Hash.BYTES];
			r.nextBytes(hash);

			bckp = new ECKeyPair();
			bckpub = bckp.getPublicKey();
			bcsignature = bckp.sign(hash);

			bjpriv = bckp.getPrivateKey().clone();
			bjpub = bckp.getPublicKey().getBytes().clone();
			bjsig = NativeSecp256k1.sign(hash, bjpriv);
		} catch (CryptoException | AssertFailException ex) {
			throw new IllegalStateException("Can't initialise test objects", ex);
		}
	}

	@Benchmark
	public void signatureVerificationBC(Blackhole bh) {
		boolean isOk = bckpub.verify(hash, bcsignature);
		bh.consume(isOk);
	}

	@Benchmark
	public void signatureVerificationBJ(Blackhole bh) throws AssertFailException {
		boolean isOk = NativeSecp256k1.verify(hash, bjsig, bjpub);
		if (!isOk) {
			throw new IllegalStateException();
		}
		bh.consume(isOk);
	}
}

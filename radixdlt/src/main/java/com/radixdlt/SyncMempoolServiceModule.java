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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.SharedMempool;
import com.radixdlt.mempool.SubmissionControl;
import com.radixdlt.mempool.SubmissionControlImpl;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.middleware2.converters.AtomToClientAtomConverter;
import com.radixdlt.serialization.Serialization;

/**
 * Module which manages synchronization of mempool atoms across of nodes
 */
public class SyncMempoolServiceModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Mempool.class).to(SharedMempool.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	SubmissionControl submissionControl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		AtomToClientAtomConverter converter,
		SubmissionControlSender submissionControlSender
	) {
		return new SubmissionControlImpl(
			mempool,
			radixEngine,
			serialization,
			converter,
			submissionControlSender
		);
	}

	@Provides
	@Singleton
	private AtomToClientAtomConverter converter() {
		return atom -> {
			try {
				return ClientAtom.convertFromApiAtom(atom);
			} catch (LedgerAtomConversionException e) {
				throw new AtomConversionException(e.getDataPointer(), e);
			}
		};
	}
}

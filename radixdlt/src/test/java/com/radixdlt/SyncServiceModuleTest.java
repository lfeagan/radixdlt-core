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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.RemoteSyncResponse;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.VerifiedAccumulatorSender;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.utils.TypedMocks;

import java.util.Comparator;
import org.junit.Test;

public class SyncServiceModuleTest {
	@Inject
	private VerifiedAccumulatorSender verifiedAccumulatorSender;

	private Ledger ledger = mock(Ledger.class);

	public Module getExternalModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
				bind(Ledger.class).toInstance(ledger);
				bind(HashVerifier.class).toInstance(mock(HashVerifier.class));
				bind(Hasher.class).toInstance(mock(Hasher.class));
				bind(LedgerAccumulatorVerifier.class).toInstance(mock(LedgerAccumulatorVerifier.class));
				bind(CommittedReader.class).toInstance(mock(CommittedReader.class));
				bind(SyncTimeoutScheduler.class).toInstance(mock(SyncTimeoutScheduler.class));
				bind(StateSyncNetwork.class).toInstance(mock(StateSyncNetwork.class));
				bind(Key.get(new TypeLiteral<Comparator<AccumulatorState>>() { })).toInstance(TypedMocks.rmock(Comparator.class));
				bind(BFTConfiguration.class).toInstance(mock(BFTConfiguration.class));
			}
		};
	}

	@Test
	public void given_configured_with_correct_interfaces__when_send_verified_accumulator__then_should_commit_to_ledger() {
		Injector injector = Guice.createInjector(new SyncServiceModule(), getExternalModule());
		injector.injectMembers(this);

		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		DtoCommandsAndProof dtoCommandsAndProof = mock(DtoCommandsAndProof.class);
		when(dtoCommandsAndProof.getCommands()).thenReturn(ImmutableList.of());
		DtoLedgerHeaderAndProof dtoLedgerHeaderAndProof = mock(DtoLedgerHeaderAndProof.class);
		when(dtoLedgerHeaderAndProof.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(dtoLedgerHeaderAndProof.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(dtoLedgerHeaderAndProof.getOpaque3()).thenReturn(mock(Hash.class));
		when(dtoLedgerHeaderAndProof.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(dtoLedgerHeaderAndProof.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(dtoCommandsAndProof.getTail()).thenReturn(dtoLedgerHeaderAndProof);
		when(response.getCommandsAndProof()).thenReturn(dtoCommandsAndProof);

		verifiedAccumulatorSender.sendVerifiedAccumulator(response);

		verify(ledger, times(1)).commit(any());
	}

}
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

package org.radix.api.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.mempool.SubmissionControl;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
import com.radixdlt.store.LedgerEntryStore;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.Before;
import org.junit.Test;

public class AtomsServiceTest {
	private AtomsService atomsService;
	private SubmissionErrorsRx submissionErrorsRx;
	private PublishSubject<CommittedAtomWithResult> committedAtomsSubject;
	private LedgerEntryStore store;
	private SubmissionControl submissionControl;
	private CommandToBinaryConverter commandToBinaryConverter;
	private ClientAtomToBinaryConverter clientAtomToBinaryConverter;

	@Before
	public void setUp() {
		this.committedAtomsSubject = PublishSubject.create();
		this.submissionErrorsRx = mock(SubmissionErrorsRx.class);
		when(this.submissionErrorsRx.deserializationFailures()).thenReturn(Observable.never());
		when(this.submissionErrorsRx.submissionFailures()).thenReturn(Observable.never());

		this.submissionControl = mock(SubmissionControl.class);
		this.store = mock(LedgerEntryStore.class);
		this.commandToBinaryConverter = mock(CommandToBinaryConverter.class);
		this.clientAtomToBinaryConverter = mock(ClientAtomToBinaryConverter.class);

		atomsService = new AtomsService(
			this.submissionErrorsRx,
			() -> this.committedAtomsSubject,
			this.store,
			this.submissionControl,
			this.commandToBinaryConverter,
			this.clientAtomToBinaryConverter
		);
	}

	@Test
	public void when_process_command_no_listeners__no_exceptions_occur() {
		atomsService.start();
		CommittedAtomWithResult committedAtomWithResult = mock(CommittedAtomWithResult.class);
		committedAtomsSubject.onNext(committedAtomWithResult);
	}
}
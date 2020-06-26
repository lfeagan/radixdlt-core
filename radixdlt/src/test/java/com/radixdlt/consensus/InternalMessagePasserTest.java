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

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class InternalMessagePasserTest {
	@Test
	public void when_send_sync_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<Hash> testObserver = internalMessagePasser.syncedVertices().test();
		Hash hash = mock(Hash.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getId()).thenReturn(hash);
		internalMessagePasser.syncedVertex(vertex);
		testObserver.awaitCount(1);
		testObserver.assertValue(hash);
		testObserver.assertNotComplete();
	}
}
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

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import org.junit.Before;
import org.junit.Test;

public class GetVerticesErrorResponseTest {
	private QuorumCertificate highestQC;
	private QuorumCertificate highestCommittedQC;
	private GetVerticesErrorResponse response;
	private BFTNode node;
	private SyncInfo syncInfo;

	@Before
	public void setUp() {
		this.highestQC = mock(QuorumCertificate.class);
		this.highestCommittedQC = mock(QuorumCertificate.class);
		this.node = mock(BFTNode.class);
		this.syncInfo = mock(SyncInfo.class);
		when(syncInfo.highestQC()).thenReturn(this.highestQC);
		when(syncInfo.highestCommittedQC()).thenReturn(this.highestCommittedQC);
		this.response = new GetVerticesErrorResponse(this.node, this.syncInfo);
	}

	@Test
	public void testGetters() {
		assertThat(this.response.syncInfo()).isEqualTo(this.syncInfo);
		assertThat(this.response.getHighestQC()).isEqualTo(this.highestQC);
		assertThat(this.response.getHighestCommittedQC()).isEqualTo(this.highestCommittedQC);
	}

	@Test
	public void testToString() {
		assertThat(this.response.toString()).isNotNull();
	}

}
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.bft.SignedViewTimeoutToLeaderSender.BFTViewTimeoutSender;
import com.radixdlt.consensus.liveness.ProposerElection;
import org.junit.Before;
import org.junit.Test;

public class SignedViewTimeoutToLeaderSenderTest {
	private SignedViewTimeoutToLeaderSender sender;
	private ViewTimeoutSigner viewTimeoutSigner = mock(ViewTimeoutSigner.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);
	private BFTViewTimeoutSender timeoutSender = mock(BFTViewTimeoutSender.class);

	@Before
	public void setup() {
		this.sender = new SignedViewTimeoutToLeaderSender(
			viewTimeoutSigner,
			proposerElection,
			timeoutSender
		);
	}

	@Test
	public void testSend() {
		View view = mock(View.class);
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		when(viewTimeoutSigner.signViewTimeout(eq(view), any())).thenReturn(viewTimeout);
		BFTNode node = mock(BFTNode.class);
		when(proposerElection.getProposer(eq(view))).thenReturn(node);

		this.sender.sendProceedToNextView(view, mock(SyncInfo.class));

		verify(timeoutSender, times(1)).sendViewTimeout(eq(viewTimeout), eq(node));
	}
}
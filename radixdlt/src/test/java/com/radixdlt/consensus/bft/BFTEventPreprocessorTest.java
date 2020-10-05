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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.ViewTimeoutSigned;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import org.junit.Before;
import org.junit.Test;

public class BFTEventPreprocessorTest {
	private static final ECKeyPair SELF_KEY = ECKeyPair.generateNew();
	private BFTEventPreprocessor preprocessor;
	private ProposerElection proposerElection;
	private Pacemaker pacemaker;
	private BFTSync vertexStoreSync;
	private BFTEventProcessor forwardTo;
	private SyncQueues syncQueues;
	private BFTNode self;

	@Before
	public void setUp() {
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStoreSync = mock(BFTSync.class);
		this.proposerElection = mock(ProposerElection.class);
		this.forwardTo = mock(BFTEventProcessor.class);
		this.syncQueues = mock(SyncQueues.class);
		this.self = mock(BFTNode.class);

		when(this.self.getKey()).thenReturn(SELF_KEY.getPublicKey());

		when(proposerElection.getProposer(any())).thenReturn(self);
		when(pacemaker.getCurrentView()).thenReturn(View.of(1));

		this.preprocessor = new BFTEventPreprocessor(
			self,
			forwardTo,
			pacemaker,
			vertexStoreSync,
			proposerElection,
			syncQueues
		);
	}

	private ViewTimeoutSigned createViewTimeout(boolean goodView, boolean synced) {
		ViewTimeoutSigned viewTimeout = mock(ViewTimeoutSigned.class);
		when(viewTimeout.signature()).thenReturn(mock(ECDSASignature.class));
		when(viewTimeout.getAuthor()).thenReturn(self);
		when(viewTimeout.view()).thenReturn(goodView ? View.of(2) : View.of(0));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		Hash vertexId = mock(Hash.class);
		BFTHeader proposed = mock(BFTHeader.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(proposed.getVertexId()).thenReturn(vertexId);
		when(viewTimeout.getQC()).thenReturn(qc);
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		when(viewTimeout.getCommittedQC()).thenReturn(committedQC);
		when(vertexStoreSync.syncToQC(any(), any())).thenReturn(synced ? SyncResult.SYNCED : SyncResult.IN_PROGRESS);
		return viewTimeout;
	}

	private Proposal createProposal(boolean goodView, boolean synced) {
		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(self);
		UnverifiedVertex vertex = mock(UnverifiedVertex.class);
		when(proposal.getVertex()).thenReturn(vertex);
		when(vertex.getView()).thenReturn(goodView ? View.of(1) : View.of(0));

		QuorumCertificate qc = mock(QuorumCertificate.class);
		BFTHeader proposed = mock(BFTHeader.class);
		when(qc.getProposed()).thenReturn(proposed);
		when(vertex.getQC()).thenReturn(qc);

		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		when(proposal.getCommittedQC()).thenReturn(committedQC);
		when(proposal.getQC()).thenReturn(qc);
		when(vertexStoreSync.syncToQC(any(), any())).thenReturn(synced ? SyncResult.SYNCED : SyncResult.IN_PROGRESS);
		return proposal;
	}

	@Test
	public void when_process_vote_as_not_proposer__then_vote_gets_thrown_away() {
		Vote vote = mock(Vote.class);
		VoteData voteData = mock(VoteData.class);
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(1));
		when(voteData.getProposed()).thenReturn(header);
		when(vote.getVoteData()).thenReturn(voteData);
		when(proposerElection.getProposer(eq(View.of(1)))).thenReturn(mock(BFTNode.class));
		preprocessor.processVote(vote);
		verify(forwardTo, never()).processVote(vote);
	}

	@Test
	public void when_process_vote__event_gets_forwarded() {
		Vote vote = mock(Vote.class);
		VoteData voteData = mock(VoteData.class);
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(1));
		when(voteData.getProposed()).thenReturn(header);
		when(vote.getVoteData()).thenReturn(voteData);
		when(vote.getSignature()).thenReturn(mock(ECDSASignature.class));
		when(vote.getAuthor()).thenReturn(mock(BFTNode.class));
		preprocessor.processVote(vote);
		verify(forwardTo, times(1)).processVote(vote);
	}

	@Test
	public void when_process_irrelevant_view_timeout__event_gets_thrown_away() {
		ViewTimeoutSigned viewTimeout = createViewTimeout(false, true);
		when(syncQueues.isEmptyElseAdd(any())).thenReturn(true);
		preprocessor.processViewTimeout(viewTimeout);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, never()).processViewTimeout(any());
	}

	@Test
	public void when_process_irrelevant_proposal__event_gets_thrown_away() {
		Proposal proposal = createProposal(false, true);
		when(syncQueues.isEmptyElseAdd(any())).thenReturn(true);
		preprocessor.processProposal(proposal);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_processing_view_timeout_as_not_proposer__then_event_gets_thrown_away() {
		ViewTimeoutSigned viewTimeout = createViewTimeout(true, true);
		when(syncQueues.isEmptyElseAdd(eq(viewTimeout))).thenReturn(true);
		when(proposerElection.getProposer(View.of(2))).thenReturn(mock(BFTNode.class));
		when(viewTimeout.getAuthor()).thenReturn(self);
		preprocessor.processViewTimeout(viewTimeout);
		verify(forwardTo, never()).processViewTimeout(any());
	}

	@Test
	public void when_process_view_timeout_not_synced__then_event_is_queued() {
		ViewTimeoutSigned viewTimeout = createViewTimeout(true, false);
		when(syncQueues.isEmptyElseAdd(eq(viewTimeout))).thenReturn(true);
		preprocessor.processViewTimeout(viewTimeout);
		verify(syncQueues, times(1)).add(eq(viewTimeout));
		verify(forwardTo, never()).processViewTimeout(any());
	}

	@Test
	public void when_process_proposal_not_synced__then_proposal_is_queued() {
		Proposal proposal = createProposal(true, false);
		when(syncQueues.isEmptyElseAdd(eq(proposal))).thenReturn(true);
		preprocessor.processProposal(proposal);
		verify(syncQueues, times(1)).add(eq(proposal));
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_process_view_timeout_synced__then_event_is_forwarded() {
		ViewTimeoutSigned viewTimeout = createViewTimeout(true, true);
		when(syncQueues.isEmptyElseAdd(eq(viewTimeout))).thenReturn(true);
		preprocessor.processViewTimeout(viewTimeout);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, times(1)).processViewTimeout(eq(viewTimeout));
	}

	@Test
	public void when_process_proposal_synced__then_proposal_is_forwarded() {
		Proposal proposal = createProposal(true, true);
		when(syncQueues.isEmptyElseAdd(eq(proposal))).thenReturn(true);
		preprocessor.processProposal(proposal);
		verify(syncQueues, never()).add(any());
		verify(forwardTo, times(1)).processProposal(eq(proposal));
	}
}
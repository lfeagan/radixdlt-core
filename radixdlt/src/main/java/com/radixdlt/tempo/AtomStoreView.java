package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.utils.Pair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;

import java.util.List;
import java.util.Optional;

/**
 * A read-only view of a specific AtomStore
 */
public interface AtomStoreView {
	/**
	 * Checks whether the given aid is contained in this view
	 * @param aid The aid
	 * @return Whether the given aid is contained in this view
	 */
	boolean contains(AID aid);

	/**
	 * Check whether this store contains a given partial {@link AID}
	 * @param partialAid The partial aid
	 * @return Whether any atom matching the partial {@link AID} is contained in this store
	 */
	boolean contains(byte[] partialAid);

	/**
	 * Get the {@link AID}s starting with a specific partial {@link AID}
	 * @param partialAid The partial aid
	 * @return The list of {@link AID}s in this store that begin with the given partial {@link AID}
	 */
	List<AID> get(byte[] partialAid);

	/**
	 * Gets the {@link AID} associated with a certain aid
	 * @param clock The clock
	 * @return The {@link AID} associated with the given clock (if any)
	 */
	Optional<AID> get(long clock);

	/**
	 * Gets the atom associated with a certain aid
	 * @param aid The aid
	 * @return The atom associated with the given aid (if any)
	 */
	Optional<TempoAtom> get(AID aid);

	/**
	 * Searches for a certain index.
	 *
	 * @param type The type of index
	 * @param index The index
	 * @param mode The mode
	 * @return The resulting ledger cursor
	 */
	LedgerCursor search(LedgerCursor.LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode);

	/**
	 * Advance the cursor to discover up to certain number of aids within a shard range
	 * @param logicalClock The current cursor
	 * @param limit The maximum number of aids
	 * @return The relevant aids and the advanced cursor
	 */
	ImmutableList<AID> getNext(long logicalClock, int limit);
}
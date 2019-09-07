package com.radixdlt;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;

/**
 * An atom comprising some arbitrary content located at a set of shards
 */
public interface Atom {
	/**
	 * Gets the immutable content of this atom.
	 * This must be a pure function.
	 *
	 * @return The immutable content
	 */
	AtomContent getContent();

	/**
	 * Gets the immutable non-empty set of shards of this atom.
	 * This must be a pure function.
	 *
	 * @return The immutable non-empty set of shards.
	 */
	ImmutableSet<Long> getShards();

	/**
	 * Gets the immutable atom identifier of this atom.
	 * This must be a pure function.
	 *
	 * @return The atom identifier.
	 */
	AID getAID();
}

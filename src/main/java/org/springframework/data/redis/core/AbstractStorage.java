package org.springframework.data.redis.core;

import org.springframework.data.keyvalue.core.AbstractKeyValueAdapter;

import java.util.List;

abstract class AbstractStorage extends AbstractKeyValueAdapter {

	// these 2 methods are missing from AbstractKeyValueAdapter,
	// but we provide them as functionality in the public RedisKeyValueAdapter so we need them 100% for compatibility

	// PartialUpdate is using SET or DEL; we should think about JSON update (JSON.SET with path, JSONARRAPPEND, NUMINCRBY;
	// HINCRBY for hashes as well? maybe, maybe do not overcomplicate? )
	abstract void update(PartialUpdate<?> update);

	// offset - index value to start reading? I do not understand this, does mean the index from an array/list type of data structure?
	// rows - maximum number or entities to return ? maybe it is a typo and it means number OF entities
	abstract <T> List<T> getAllOf(String keyspace, Class<T> classType, long offset, int rows);
}

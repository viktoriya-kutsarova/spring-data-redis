package org.springframework.data.redis.core;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.convert.Bucket;
import org.springframework.data.redis.core.convert.HashConverter;
import org.springframework.data.util.CloseableIterator;

// The new hash layout, for @RedisHash + @SearchIndex. Same Redis structure as the legacy one but different content:
// lists, points and dates stored in a way Redis Search can index, so it will need its own converter.
final class HashStorage extends AbstractStorage {

	private final HashConverter converter;
	private final RedisOperations<?, ?> redisOps;

	HashStorage(HashConverter converter, RedisOperations<?, ?> redisOps) {
		this.converter = converter;
		this.redisOps = redisOps;
	}

	@Override
	public @Nullable Object put(Object id, Object item, String keyspace) {

		Bucket bucket = new Bucket();
		converter.write(item, bucket);

		redisOps.execute((RedisCallback<Object>) connection -> {

			byte[] key = toBytes(id);
			byte[] objectKey = toBytes(keyspace + ":" + id);

			boolean isNew = connection.del(objectKey) == 0;

			connection.hMSet(objectKey, bucket.rawMap());

			if (isNew) {
				connection.sAdd(toBytes(keyspace), key);
			}

			return null;
		});

		return item;
	}

	private static byte[] toBytes(Object source) {
		return source.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public boolean contains(Object id, String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	public @Nullable Object get(Object id, String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	public @Nullable Object delete(Object id, String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Object> getAllOf(String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	<T> List<T> getAllOf(String keyspace, Class<T> classType, long offset, int rows) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteAllOf(String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long count(String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	void update(PartialUpdate<?> update) {
		throw new UnsupportedOperationException();
	}

	@Override
	public CloseableIterator<Map.Entry<Object, Object>> entries(String keyspace) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {}

	@Override
	public void destroy() {}
}

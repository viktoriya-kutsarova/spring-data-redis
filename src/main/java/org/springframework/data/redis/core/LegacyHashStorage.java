package org.springframework.data.redis.core;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisConverter;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.util.ByteUtils;
import org.springframework.data.util.CloseableIterator;

// Maybe we do not want any inheritance of the legacy hash storage, I guess? Hence final
final class LegacyHashStorage extends AbstractStorage {

	private RedisConverter converter;
	private RedisOperations<?, ?> redisOps;

	LegacyHashStorage(RedisConverter converter, RedisOperations<?, ?> redisOps) {
		this.converter = converter;
		this.redisOps = redisOps;
	}


	@Override
	public @Nullable Object put(Object id, Object item, String keyspace) {
		RedisData rdo = item instanceof RedisData ? (RedisData) item : new RedisData();

		if (!(item instanceof RedisData)) {
			converter.write(item, rdo);
		}

		if (rdo.getId() == null) {
			rdo.setId(converter.getConversionService().convert(id, String.class));
		}

		// why are we using the connection directly instead of opsForHash and opsForSet
		// using one connection? also, serialise/deserialise vs raw bytyes
		redisOps.execute((RedisCallback<Object>) connection -> {

			byte[] key = toBytes(rdo.getId());
			byte[] objectKey = createKey(rdo.getKeyspace(), rdo.getId());

			boolean isNew = connection.del(objectKey) == 0;

			connection.hMSet(objectKey, rdo.getBucket().rawMap());

			if (isNew) {
				connection.sAdd(toBytes(rdo.getKeyspace()), key);
			}

			if (expires(rdo)) {
				connection.expire(objectKey, rdo.getTimeToLive());
			}

			// next to copy is to figure out what we do with the phantom copy,
			// the issue to copy it here is the keepShadowCopy place, but anyway we want a whole strategy
			// for legacy hash, the new hash and probably for json, so we need to rethink it anyway


			// this is specific to the old indexing strategy with the auxiliary SETs
			IndexWriter indexWriter = new IndexWriter(connection, converter);

			if (isNew) {
				indexWriter.createIndexes(key, rdo.getIndexedData());
			} else {
				indexWriter.deleteAndUpdateIndexes(key, rdo.getIndexedData());
			}

			return null;
		});


		return item;
	}

	// copies of the two public methods of RedisKeyValueAdapter
	// to discuss with Mark and Moritz what to do as they are public AND overridable
	// and also how to pass the keepShadowCopy potentially ?
	private byte[] createKey(String keyspace, String id) {
		return toBytes(keyspace + ":" + id);
	}

	private byte[] toBytes(Object source) {
		return source instanceof byte[] bytes ? bytes : converter.getConversionService().convert(source, byte[].class);
	}

	// method copied from the RedisKeyValueAdapter but this time it is private there so we can do whatever
	private boolean expires(RedisData data) {
		return data.getTimeToLive() != null && data.getTimeToLive() > 0;
	}

	// placeholders for now

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

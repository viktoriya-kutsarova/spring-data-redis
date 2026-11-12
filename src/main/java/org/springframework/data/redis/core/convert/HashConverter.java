package org.springframework.data.redis.core.convert;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.core.mapping.RedisPersistentEntity;
import org.springframework.data.redis.core.mapping.RedisPersistentProperty;

// example for the new hash layout: a string as it is,
// a list as ONE field joined by a separator
// this should become a bean? separator I think can be configurable, it would probably also hold custom conversions?
public final class HashConverter { // if it implements RedisConverter, we force the write to use RedisData which holds the IndexedData? maybe worth exploring

	// but now I feel like the HashStorages can be merged in a way, but just use different converters?


	static final String SEPARATOR = ",";

	private final RedisMappingContext mappingContext;

	public HashConverter(RedisMappingContext mappingContext) {
		this.mappingContext = mappingContext;
	}

	public void write(Object source, Bucket sink) {

		RedisPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(source.getClass());
		PersistentPropertyAccessor<?> accessor = entity.getPropertyAccessor(source);

		entity.doWithProperties((PropertyHandler<RedisPersistentProperty>) property -> {

			Object value = accessor.getProperty(property);

			if (value == null) {
				return;
			}

			String text = value instanceof Collection<?> collection
					? collection.stream().map(Object::toString).collect(Collectors.joining(SEPARATOR))
					: value.toString();

			sink.put(property.getName(), text.getBytes(StandardCharsets.UTF_8));
		});
	}
}

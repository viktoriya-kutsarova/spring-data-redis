package org.springframework.data.redis.core;

import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.core.search.SearchIndex;

@ExtendWith(MockitoExtension.class)
class RedisKeyValueAdapterStorageSelectionUnitTests {

	@Mock JedisConnectionFactory connectionFactory;
	@Mock LegacyHashStorage legacyHash;
	@Mock HashStorage hash;

	RedisKeyValueAdapter adapter;

	@BeforeEach
	void setUp() {

		RedisTemplate<?, ?> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.afterPropertiesSet();

		adapter = new RedisKeyValueAdapter(template, new RedisMappingContext());

		setField(adapter, "legacyHash", legacyHash);
		setField(adapter, "hash", hash);
	}

	@Test
	void putShouldGoToLegacyHashStorageForRedisHashOnly() {

		Person person = new Person();

		adapter.put("1", person, "persons");

		verify(legacyHash).put("1", person, "persons");
		verifyNoInteractions(hash);
	}

	@Test
	void putShouldGoToHashStorageForRedisHashWithSearchIndex() {

		RedisSearchPerson person = new RedisSearchPerson();

		adapter.put("1", person, "redis-search-persons");

		verify(hash).put("1", person, "redis-search-persons");
		verifyNoInteractions(legacyHash);
	}

	@RedisHash("persons")
	static class Person {

		@Id String id;
		String firstname;
	}

	@RedisHash("redis-search-persons")
	@SearchIndex(name = "redis-search-persons-idx")
	static class RedisSearchPerson {

		@Id String id;
		String firstname;
	}
}

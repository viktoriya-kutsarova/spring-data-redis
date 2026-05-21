/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.connection.jedis;

import org.springframework.data.redis.connection.RedisSearchCommands;
import org.springframework.data.redis.search.DropIndexOptions;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.schemafields.SchemaField;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullUnmarked;

import org.springframework.data.redis.search.IndexDefinition;
import org.springframework.data.redis.search.Schema;
import org.springframework.data.redis.search.SearchDocument;
import org.springframework.data.redis.search.SearchHit;
import org.springframework.data.redis.search.SearchOptions;
import org.springframework.data.redis.search.SearchQuery;
import org.springframework.data.redis.search.SearchResult;

/**
 * Jedis implementation of {@link RedisSearchCommands}.
 *
 * @author Viktoriya Kutsarova
 * @since 4.1
 */
@NullUnmarked
class JedisSearchCommands implements RedisSearchCommands {

	private final JedisConnection connection;

	JedisSearchCommands(JedisConnection connection) {
		this.connection = connection;
	}

	@Override
	public String createIndex(String name, Schema schema) {
		return createIndex(name, schema, IndexDefinition.create());
	}

	@Override
	public String createIndex(String name, Schema schema, IndexDefinition definition) {

		FTCreateParams createParams = JedisConverters.toFTCreateParams(definition);
		List<SchemaField> schemaFields = JedisConverters.toSchemaFields(schema);

		return connection.invoke().just(jedis -> jedis.ftCreate(name, createParams, schemaFields));
	}

	@Override
	public void dropIndex(String name) {
		connection.invoke().just(jedis -> jedis.ftDropIndex(name));
	}

	@Override
	public void dropIndex(String name, DropIndexOptions options) {
		if (options.isDeleteDocuments()) {
			connection.invoke().just(jedis -> jedis.ftDropIndexDD(name));
		} else {
			dropIndex(name);
		}

	}

	@Override
	public SearchResult search(String index, SearchQuery query) {
		return search(index, query, SearchOptions.create());
	}

	@Override
	public SearchResult search(String index, SearchQuery query, SearchOptions options) {

		redis.clients.jedis.search.SearchResult reply = connection.invoke()
				.just(jedis -> jedis.ftSearch(index, query.toQueryString(), JedisConverters.toFTSearchParams(options)));

		return JedisConverters.toSearchResult(reply, options);
	}
}


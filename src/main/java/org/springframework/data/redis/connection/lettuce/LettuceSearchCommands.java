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
package org.springframework.data.redis.connection.lettuce;

import io.lettuce.core.api.async.RediSearchAsyncCommands;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.search.SearchReply;
import io.lettuce.core.search.arguments.CreateArgs;
import io.lettuce.core.search.arguments.FieldArgs;
import io.lettuce.core.search.arguments.SearchArgs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullUnmarked;

import org.springframework.data.redis.connection.RedisSearchCommands;
import org.springframework.data.redis.search.DropIndexOptions;
import org.springframework.data.redis.search.IndexDefinition;
import org.springframework.data.redis.search.Schema;
import org.springframework.data.redis.search.SearchOptions;
import org.springframework.data.redis.search.SearchQuery;
import org.springframework.data.redis.search.SearchResult;

/**
 * Lettuce implementation of {@link RedisSearchCommands}.
 *
 * @author Viktoriya Kutsarova
 * @since 4.1
 */
@NullUnmarked
class LettuceSearchCommands implements RedisSearchCommands {

	private enum FtKeyword implements ProtocolKeyword {

		INFO("FT.INFO");

		private final byte[] bytes;

		FtKeyword(String keyword) {
			this.bytes = keyword.getBytes(StandardCharsets.US_ASCII);
		}

		@Override
		public byte[] getBytes() {
			return bytes;
		}
	}

	private final LettuceConnection connection;

	LettuceSearchCommands(LettuceConnection connection) {
		this.connection = connection;
	}

	@Override
	public String createIndex(String name, Schema schema, IndexDefinition definition) {
		CreateArgs<byte[], byte[]> createArgs = LettuceConverters.toCreateArgs(definition);
		List<FieldArgs<byte[]>> fieldArgs = LettuceConverters.toFieldArgs(schema);

		String createIndexReply = connection.invoke().just(cmd -> {
			RediSearchAsyncCommands<byte[], byte[]> searchCmd = (RediSearchAsyncCommands<byte[], byte[]>) cmd;
			return searchCmd.ftCreate(name, createArgs, fieldArgs);
		});
		return createIndexReply;
	}

	@Override
	public void dropIndex(String name, DropIndexOptions options) {
		connection.invoke().just(cmd -> {
			RediSearchAsyncCommands<byte[], byte[]> searchCmd = (RediSearchAsyncCommands<byte[], byte[]>) cmd;
			return searchCmd.ftDropindex(name);
		});
	}

	@Override
	public SearchResult search(String index, SearchQuery query) {
		return search(index, query, SearchOptions.create());
	}

	@Override
	@SuppressWarnings("unchecked")
	public SearchResult search(String index, SearchQuery query, SearchOptions options) {

		SearchArgs<byte[], byte[]> searchArgs = LettuceConverters.toSearchArgs(options);
		byte[] queryBytes = query.toQueryString().getBytes(StandardCharsets.UTF_8);

		SearchReply<byte[], byte[]> reply = connection.invoke().just(cmd -> {
			RediSearchAsyncCommands<byte[], byte[]> searchCmd = (RediSearchAsyncCommands<byte[], byte[]>) cmd;
			return searchCmd.ftSearch(index, queryBytes, searchArgs);
		});

		return LettuceConverters.toSearchResult(reply, options);
	}

	/**
	 * Parse a flat alternating key/value list into a {@link Map}.
	 * <p>
	 * Used for parsing FT.INFO responses returned via {@link NestedMultiOutput} with the {@code byte[]} codec.
	 */
	@SuppressWarnings("unchecked")
	static Map<String, Object> parseFlatPairList(List<Object> list) {

		Map<String, Object> map = new LinkedHashMap<>();

		for (int i = 0; i + 1 < list.size(); i += 2) {
			String key = decodeBytes(list.get(i));
			Object value = parseValue(list.get(i + 1));
			map.put(key, value);
		}

		return map;
	}

	@SuppressWarnings("unchecked")
	private static Object parseValue(Object raw) {

		if (raw instanceof byte[] bytes) {
			return new String(bytes, StandardCharsets.UTF_8);
		}

		if (raw instanceof List<?> list) {

			if (list.isEmpty()) {
				return Collections.emptyList();
			}

			Object first = list.get(0);

			if (first instanceof List) {
				// List of sublists — each sublist is a flat-pair map (e.g., "attributes")
				List<Map<String, Object>> result = new ArrayList<>(list.size());
				for (Object item : list) {
					if (item instanceof List) {
						result.add(parseFlatPairList((List<Object>) item));
					}
				}
				return result;
			}

			// Check if this looks like a flat key-value pair list (even count, first element is bytes)
			if (first instanceof byte[] && list.size() >= 2 && list.size() % 2 == 0) {
				// Flat-pair list (e.g., individual attribute definition)
				return parseFlatPairList((List<Object>) list);
			}

			boolean hasNestedList = list.stream().anyMatch(e -> e instanceof List);

			if (hasNestedList) {
				// Flat-pair list where some values are nested lists (e.g., "index_definition")
				return parseFlatPairList((List<Object>) list);
			}

			// Simple list of strings or numbers (e.g., "prefixes")
			List<Object> result = new ArrayList<>(list.size());
			for (Object item : list) {
				result.add(parseValue(item));
			}
			return result;
		}

		// Long (integer), etc. — return as-is
		return raw;
	}

	private static String decodeBytes(Object raw) {
		if (raw instanceof byte[] bytes) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		return String.valueOf(raw);
	}
}


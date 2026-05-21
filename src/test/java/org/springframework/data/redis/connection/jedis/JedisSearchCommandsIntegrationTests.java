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

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.data.redis.SettingsUtils;
import org.springframework.data.redis.connection.RedisSearchCommands;
import org.springframework.data.redis.search.IndexDataType;
import org.springframework.data.redis.search.IndexDefinition;
import org.springframework.data.redis.search.Schema;
import org.springframework.data.redis.search.SchemaField;
import org.springframework.data.redis.search.SearchHit;
import org.springframework.data.redis.search.SearchOptions;
import org.springframework.data.redis.search.SearchQuery;
import org.springframework.data.redis.search.SearchResult;
import org.springframework.data.redis.test.condition.EnabledOnCommand;

/**
 * Integration tests for {@link JedisSearchCommands}.
 *
 * @author Viktoriya Kutsarova
 */
@EnabledOnCommand("FT.CREATE")
@Disabled("Jedis search implementation not yet complete")
class JedisSearchCommandsIntegrationTests {

	JedisConnectionFactory connectionFactory;
	JedisConnection connection;
	RedisSearchCommands commands;

	@BeforeEach
	void setUp() {
		connectionFactory = new JedisConnectionFactory(SettingsUtils.standaloneConfiguration());
		connectionFactory.afterPropertiesSet();
		connection = (JedisConnection) connectionFactory.getConnection();
		commands = connection.searchCommands();
	}

	@AfterEach
	void tearDown() {
		connection.close();
		connectionFactory.destroy();
	}

	// -------------------------------------------------------------------------
	// Section 1 — createIndex
	// -------------------------------------------------------------------------

	@Test
	void createIndexShouldReturnOK() {

		Schema schema = Schema.of(SchemaField.text("title"));
		IndexDefinition definition = IndexDefinition.create()
				.on(IndexDataType.HASH)
				.prefix("doc:");

		assertThat(commands.createIndex("idx-" + UUID.randomUUID(), schema, definition)).isEqualTo("OK");
	}

	// -------------------------------------------------------------------------
	// Section 2 — search: Tag exact match
	// -------------------------------------------------------------------------

	@Test
	void searchByTagExactMatchShouldReturnMatchingDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "product:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.tag("status"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"status".getBytes(), "active".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"status".getBytes(), "inactive".getBytes()));
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"status".getBytes(), "active".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("@status:{active}"));

		assertThat(result.getTotalResults()).isEqualTo(2);
		assertThat(result.getHits()).hasSize(2);
		assertThat(result.getHits()).extracting(SearchHit::getId)
				.containsExactlyInAnyOrder(prefix + "1", prefix + "3");
	}

	@Test
	void searchByTagWithNoMatchShouldReturnEmptyResult() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "item:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.tag("category"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"category".getBytes(), "electronics".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("@category:{clothing}"));

		assertThat(result.getTotalResults()).isZero();
		assertThat(result.getHits()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Section 3 — search: Numeric range
	// -------------------------------------------------------------------------

	@Test
	void searchByNumericRangeShouldReturnMatchingDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "product:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.numeric("price"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"price".getBytes(), "50".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"price".getBytes(), "150".getBytes()));
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"price".getBytes(), "250".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("@price:[100 200]"));

		assertThat(result.getTotalResults()).isEqualTo(1);
		assertThat(result.getHits()).hasSize(1);
		assertThat(result.getHits().get(0).getId()).isEqualTo(prefix + "2");
	}

	// -------------------------------------------------------------------------
	// Section 4 — search: Full-text search
	// -------------------------------------------------------------------------

	@Test
	void searchByFullTextShouldReturnMatchingDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "article:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("title"), SchemaField.text("body"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"title".getBytes(), "Introduction to Redis".getBytes(),
				"body".getBytes(), "Redis is an in-memory data store".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"title".getBytes(), "Getting Started with Spring".getBytes(),
				"body".getBytes(), "Spring Framework tutorial for beginners".getBytes()));
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"title".getBytes(), "Redis and Spring Data".getBytes(),
				"body".getBytes(), "Learn how to use Spring Data Redis".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("Redis"));

		assertThat(result.getTotalResults()).isEqualTo(2);
		assertThat(result.getHits()).extracting(SearchHit::getId)
				.containsExactlyInAnyOrder(prefix + "1", prefix + "3");
	}

	// -------------------------------------------------------------------------
	// Section 5 — search: Geo search
	// -------------------------------------------------------------------------

	@Test
	void searchByGeoRadiusShouldReturnMatchingDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "store:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("name"), SchemaField.geo("location"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		// San Francisco
		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"name".getBytes(), "SF Store".getBytes(),
				"location".getBytes(), "-122.4194,37.7749".getBytes()));
		// Los Angeles
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"name".getBytes(), "LA Store".getBytes(),
				"location".getBytes(), "-118.2437,34.0522".getBytes()));
		// New York
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"name".getBytes(), "NY Store".getBytes(),
				"location".getBytes(), "-74.0060,40.7128".getBytes()));

		waitForIndexing();

		// Search within 100km of San Francisco
		SearchResult result = commands.search(indexName,
				SearchQuery.of("@location:[-122.4194 37.7749 100 km]"));

		assertThat(result.getTotalResults()).isEqualTo(1);
		assertThat(result.getHits().get(0).getId()).isEqualTo(prefix + "1");
	}

	// -------------------------------------------------------------------------
	// Section 6 — search: Options
	// -------------------------------------------------------------------------

	@Test
	void searchWithLimitShouldReturnLimitedResults() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "doc:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.tag("type"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		for (int i = 1; i <= 10; i++) {
			connection.hashCommands().hMSet((prefix + i).getBytes(), Map.of(
					"type".getBytes(), "test".getBytes()));
		}

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("@type:{test}"),
				SearchOptions.create().limit(0, 3));

		assertThat(result.getTotalResults()).isEqualTo(10);
		assertThat(result.getHits()).hasSize(3);
	}

	@Test
	void searchExplainScoresShouldIncludeScoresInResults() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "article:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("content"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"content".getBytes(), "hello world".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("hello"),
				SearchOptions.create().explainScore());

		assertThat(result.getHits()).hasSize(1);
		assertThat(result.getHits().get(0).getScore()).isNotNull();
		assertThat(result.getHits().get(0).getScore()).isGreaterThan(0);
	}

	@Test
	void searchAllShouldReturnAllDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "item:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("name"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName, schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"name".getBytes(), "Item One".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"name".getBytes(), "Item Two".getBytes()));
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"name".getBytes(), "Item Three".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.all());

		assertThat(result.getTotalResults()).isEqualTo(3);
		assertThat(result.getHits()).hasSize(3);
	}

	private void waitForIndexing() {
		try {
			Thread.sleep(100); // Small delay to allow Redis to index documents
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}


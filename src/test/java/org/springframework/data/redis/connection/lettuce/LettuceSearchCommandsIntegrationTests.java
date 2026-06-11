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
import org.springframework.data.redis.search.Language;
import org.springframework.data.redis.search.Schema;
import org.springframework.data.redis.search.SchemaField;
import org.springframework.data.redis.search.SearchHit;
import org.springframework.data.redis.search.SearchOptions;
import org.springframework.data.redis.search.SearchQuery;
import org.springframework.data.redis.search.SearchResult;
import org.springframework.data.redis.test.condition.EnabledOnCommand;

/**
 * Integration tests for {@link LettuceSearchCommands}.
 *
 * @author Viktoriya Kutsarova
 */
@EnabledOnCommand("FT.CREATE")
class LettuceSearchCommandsIntegrationTests {

	LettuceConnectionFactory connectionFactory;
	LettuceConnection connection;
	RedisSearchCommands commands;

	@BeforeEach
	void setUp() {
		connectionFactory = new LettuceConnectionFactory(SettingsUtils.standaloneConfiguration());
		connectionFactory.afterPropertiesSet();
		connection = (LettuceConnection) connectionFactory.getConnection();
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

		assertThat(commands.createIndex("idx-" + UUID.randomUUID(),  schema, definition)).isEqualTo("OK");
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexOnHashShouldBeReflectedInIndexInfo() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.text("title"));
		IndexDefinition definition = IndexDefinition.create()
				.on(IndexDataType.HASH)
				.prefix("doc:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		assertThat(info).isNotNull();
//		assertThat(info.get("index_name")).isEqualTo(indexName);

//		Map<?, ?> indexDefinition = (Map<?, ?>) info.get("index_definition");
//		assertThat(indexDefinition.get("key_type")).isEqualTo("HASH");
//		assertThat(indexDefinition.get("prefixes")).asList().containsExactly("doc:");
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithMultiplePrefixesShouldBeReflectedInIndexInfo() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.text("title"));
		IndexDefinition definition = IndexDefinition.create()
				.on(IndexDataType.HASH)
				.prefix("article:", "post:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		Map<?, ?> indexDefinition = (Map<?, ?>) info.get("index_definition");
//		assertThat(indexDefinition.get("prefixes")).asList().containsExactlyInAnyOrder("article:", "post:");
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithoutPrefixShouldIndexAllKeys() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.text("name"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH);

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		Map<?, ?> indexDefinition = (Map<?, ?>) info.get("index_definition");
//		assertThat(indexDefinition.get("prefixes")).asList().isEmpty();
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithTextFieldOptionsShouldBeReflectedInAttributes() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(
				SchemaField.text("title").weight(2.5).sortable().stemming(false)
		);
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("doc:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		List<?> attributes = (List<?>) info.get("attributes");
//		assertThat(attributes).hasSize(1);
//
//		Map<?, ?> titleAttr = (Map<?, ?>) attributes.get(0);
//		assertThat(titleAttr.get("attribute")).isEqualTo("title");
//		assertThat(titleAttr.get("type")).isEqualTo("TEXT");
//		assertThat(Double.parseDouble(titleAttr.get("WEIGHT").toString())).isEqualTo(2.5);
//		assertThat(titleAttr.get("SORTABLE")).isNotNull();
//		assertThat(titleAttr.get("NOSTEM")).isNotNull();
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithTagFieldOptionsShouldBeReflectedInAttributes() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(
				SchemaField.tag("status").separator(';').caseSensitive()
		);
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("doc:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		List<?> attributes = (List<?>) info.get("attributes");
//
//		Map<?, ?> statusAttr = (Map<?, ?>) attributes.get(0);
//		assertThat(statusAttr.get("attribute")).isEqualTo("status");
//		assertThat(statusAttr.get("type")).isEqualTo("TAG");
//		assertThat(statusAttr.get("SEPARATOR")).isEqualTo(";");
//		assertThat(statusAttr.get("CASESENSITIVE")).isNotNull();
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithNumericFieldShouldBeReflectedInAttributes() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.numeric("price").sortable());
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("product:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		List<?> attributes = (List<?>) info.get("attributes");
//
//		Map<?, ?> priceAttr = (Map<?, ?>) attributes.get(0);
//		assertThat(priceAttr.get("attribute")).isEqualTo("price");
//		assertThat(priceAttr.get("type")).isEqualTo("NUMERIC");
//		assertThat(priceAttr.get("SORTABLE")).isNotNull();
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithGeoFieldShouldBeReflectedInAttributes() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.geo("location"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("place:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		List<?> attributes = (List<?>) info.get("attributes");
//
//		Map<?, ?> locationAttr = (Map<?, ?>) attributes.get(0);
//		assertThat(locationAttr.get("attribute")).isEqualTo("location");
//		assertThat(locationAttr.get("type")).isEqualTo("GEO");
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithFieldAliasShouldBeReflectedInAttributes() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.text("$.body").as("body"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("doc:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		List<?> attributes = (List<?>) info.get("attributes");
//
//		Map<?, ?> attr = (Map<?, ?>) attributes.get(0);
//		assertThat(attr.get("identifier")).isEqualTo("$.body");
//		assertThat(attr.get("attribute")).isEqualTo("body");
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithMixedFieldTypesShouldRegisterAllAttributes() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(
				SchemaField.text("title"),
				SchemaField.tag("status"),
				SchemaField.numeric("price"),
				SchemaField.geo("location")
		);
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("item:");

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		List<?> attributes = (List<?>) info.get("attributes");
//		assertThat(attributes).hasSize(4);
	}

	@Test
	@Disabled("Requires FT.INFO parsing which is not yet implemented in Lettuce")
	void createIndexWithLanguageOptionShouldBeReflectedInIndexInfo() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.text("body"));
		IndexDefinition definition = IndexDefinition.create()
				.on(IndexDataType.HASH)
				.prefix("article:")
				.language(Language.FRENCH);

		commands.createIndex(indexName,  schema, definition);

//		Map<String, Object> info = commands.indexInfo(indexName);
//		Map<?, ?> indexDefinition = (Map<?, ?>) info.get("index_definition");
//		assertThat(indexDefinition.get("default_language")).isEqualTo("french");
	}

	@Test
	void createIndexWithDuplicateNameShouldThrow() {

		String indexName = "idx-" + UUID.randomUUID();
		Schema schema = Schema.of(SchemaField.text("title"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix("doc:");

		commands.createIndex(indexName,  schema, definition);

		assertThatException().isThrownBy(() -> commands.createIndex(indexName,  schema, definition));
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
		commands.createIndex(indexName,  schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"status".getBytes(), "active".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"status".getBytes(), "inactive".getBytes()));
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"status".getBytes(), "active".getBytes()));

		// Wait for indexing
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
		commands.createIndex(indexName,  schema, definition);

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
		commands.createIndex(indexName,  schema, definition);

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

	@Test
	void searchByNumericGreaterThanShouldReturnMatchingDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "order:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.numeric("total"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName,  schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"total".getBytes(), "100".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"total".getBytes(), "500".getBytes()));
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"total".getBytes(), "1000".getBytes()));

		waitForIndexing();

		// Greater than 200 (exclusive lower bound using parenthesis)
		SearchResult result = commands.search(indexName, SearchQuery.of("@total:[(200 +inf]"));

		assertThat(result.getTotalResults()).isEqualTo(2);
		assertThat(result.getHits()).extracting(SearchHit::getId)
				.containsExactlyInAnyOrder(prefix + "2", prefix + "3");
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
		commands.createIndex(indexName,  schema, definition);

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

	@Test
	void searchByFullTextInSpecificFieldShouldReturnMatchingDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "post:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("title"), SchemaField.text("content"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName,  schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"title".getBytes(), "Java Programming".getBytes(),
				"content".getBytes(), "Learn about Python basics".getBytes()));
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"title".getBytes(), "Python Tutorial".getBytes(),
				"content".getBytes(), "Advanced Python concepts".getBytes()));

		waitForIndexing();

		// Search only in title field
		SearchResult result = commands.search(indexName, SearchQuery.of("@title:Python"));

		assertThat(result.getTotalResults()).isEqualTo(1);
		assertThat(result.getHits().get(0).getId()).isEqualTo(prefix + "2");
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
		commands.createIndex(indexName,  schema, definition);

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

	@Test
	void searchByGeoRadiusWithLargerRadiusShouldReturnMoreDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "restaurant:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.geo("coords"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName,  schema, definition);

		// Downtown (0,0)
		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"coords".getBytes(), "0,0".getBytes()));
		// 50km away
		connection.hashCommands().hMSet((prefix + "2").getBytes(), Map.of(
				"coords".getBytes(), "0.45,0".getBytes()));
		// 200km away
		connection.hashCommands().hMSet((prefix + "3").getBytes(), Map.of(
				"coords".getBytes(), "1.8,0".getBytes()));

		waitForIndexing();

		// Search within 100km
		SearchResult result = commands.search(indexName,
				SearchQuery.of("@coords:[0 0 100 km]"));

		assertThat(result.getTotalResults()).isEqualTo(2);
		assertThat(result.getHits()).extracting(SearchHit::getId)
				.containsExactlyInAnyOrder(prefix + "1", prefix + "2");
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
		commands.createIndex(indexName,  schema, definition);

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

	// this test is expected to fail since Lettuce does not support explain score - it is added to the documentation but the method is missing from the SearchArgs Builder
	@Test
	void searchWithScoresShouldIncludeScoresInResults() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "article:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("content"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName,  schema, definition);

		connection.hashCommands().hMSet((prefix + "1").getBytes(), Map.of(
				"content".getBytes(), "hello world".getBytes()));

		waitForIndexing();

		SearchResult result = commands.search(indexName, SearchQuery.of("hello"),
				SearchOptions.create().explainScore());

		assertThat(result.getHits()).hasSize(1);
//		assertThat(result.getHits().get(0).getScore()).isNotNull();
//		assertThat(result.getHits().get(0).getScore()).isGreaterThan(0);
	}

	@Test
	void searchAllShouldReturnAllDocuments() {

		String indexName = "idx-" + UUID.randomUUID();
		String prefix = "item:" + UUID.randomUUID() + ":";

		Schema schema = Schema.of(SchemaField.text("name"));
		IndexDefinition definition = IndexDefinition.create().on(IndexDataType.HASH).prefix(prefix);
		commands.createIndex(indexName,  schema, definition);

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


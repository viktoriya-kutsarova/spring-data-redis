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
package org.springframework.data.redis.search;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SearchHit}.
 *
 * @author Viktoriya Kutsarova
 */
class SearchHitUnitTests {

	@Test
	void constructorSetsAllFields() {

		SearchDocument doc = new SearchDocument(Map.of("title", "Hello"));
		SearchHit hit = new SearchHit("doc:1", 0.98, "sortval", doc);

		assertThat(hit.getId()).isEqualTo("doc:1");
		assertThat(hit.getScore()).isEqualTo(0.98);
		assertThat(hit.getSortKey()).isEqualTo("sortval");
		assertThat(hit.getDocument()).isSameAs(doc);
	}

	@Test
	void nullableFieldsAreAccepted() {

		SearchDocument doc = new SearchDocument(Map.of());
		SearchHit hit = new SearchHit("doc:2", null, null, doc);

		assertThat(hit.getId()).isEqualTo("doc:2");
		assertThat(hit.getScore()).isNull();
		assertThat(hit.getSortKey()).isNull();
		assertThat(hit.getDocument()).isSameAs(doc);
	}

	@Test
	void scoreOnlyPopulatedWhenWithScoresRequested() {

		// Score is null when WITHSCORES was not set — connection layer passes null
		SearchDocument doc = new SearchDocument(Map.of());
		SearchHit hit = new SearchHit("doc:3", null, null, doc);

		assertThat(hit.getScore()).isNull();
	}

	@Test
	void sortKeyOnlyPopulatedWhenWithSortKeysRequested() {

		SearchDocument doc = new SearchDocument(Map.of());
		SearchHit hit = new SearchHit("doc:4", null, "my-sort-val", doc);

		assertThat(hit.getSortKey()).isEqualTo("my-sort-val");
	}

	@Test
	void documentHoldsFieldValues() {

		SearchDocument doc = new SearchDocument(Map.of("price", "9.99", "status", "active"));
		SearchHit hit = new SearchHit("doc:5", 1.0, null, doc);

		assertThat(hit.getDocument().getFields()).containsEntry("price", "9.99").containsEntry("status", "active");
	}
}

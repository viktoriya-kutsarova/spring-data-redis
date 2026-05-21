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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SearchResult}.
 *
 * @author Viktoriya Kutsarova
 */
class SearchResultUnitTests {

	private static SearchHit hit(String id) {
		return new SearchHit(id, null, null, new SearchDocument(Map.of()));
	}

	@Test
	void constructorSetsTotalResultsAndHits() {

		SearchHit h = hit("doc:1");
		SearchResult result = new SearchResult(100L, List.of(h));

		assertThat(result.getTotalResults()).isEqualTo(100L);
		assertThat(result.getHits()).containsExactly(h);
	}

	@Test
	void totalResultsCanExceedHitsSize() {

		// Simulates a paginated result where only 10 hits are returned out of 1000 total
		List<SearchHit> hits = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			hits.add(hit("doc:" + i));
		}

		SearchResult result = new SearchResult(1000L, hits);

		assertThat(result.getTotalResults()).isEqualTo(1000L);
		assertThat(result.getHits()).hasSize(10);
	}

	@Test
	void emptyResultIsValid() {

		SearchResult result = new SearchResult(0L, List.of());

		assertThat(result.getTotalResults()).isZero();
		assertThat(result.getHits()).isEmpty();
	}

	@Test
	void hitsListIsImmutable() {

		List<SearchHit> hits = new ArrayList<>();
		hits.add(hit("doc:1"));
		SearchResult result = new SearchResult(1L, hits);

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> result.getHits().add(hit("doc:2")));
	}

	@Test
	void mutatingOriginalListDoesNotAffectResult() {

		List<SearchHit> hits = new ArrayList<>();
		hits.add(hit("doc:1"));
		SearchResult result = new SearchResult(1L, hits);

		hits.add(hit("doc:2"));

		assertThat(result.getHits()).hasSize(1);
	}
}

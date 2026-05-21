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

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for {@link SearchOptions}.
 *
 * @author Viktoriya Kutsarova
 */
class SearchOptionsUnitTests {

	@Test
	void defaultsAreConservative() {

		SearchOptions opts = SearchOptions.create();

		assertThat(opts.isIncludeContent()).isTrue();
		assertThat(opts.isScores()).isFalse();
		assertThat(opts.isSortKeys()).isFalse();
		assertThat(opts.isVerbatim()).isFalse();
		assertThat(opts.isInOrder()).isFalse();
		assertThat(opts.isExplainScore()).isFalse();
		assertThat(opts.getSortBy()).isNull();
		assertThat(opts.getSortDirection()).isEqualTo(Sort.Direction.ASC);
		assertThat(opts.getReturnFields()).isNull();
		assertThat(opts.getLanguage()).isNull();
		assertThat(opts.getSlop()).isNull();
		assertThat(opts.getTimeout()).isNull();
		assertThat(opts.getExpander()).isNull();
		assertThat(opts.getScorer()).isNull();
		assertThat(opts.getInKeys()).isNull();
		assertThat(opts.getInFields()).isNull();
	}

	@Test
	void limitSetsOffsetAndCountAndMarksLimitAsSet() {

		SearchOptions opts = SearchOptions.create().limit(5, 20);

		// do we want such a method - isLimitSet()
//		assertThat(opts.isLimitSet()).isTrue();
		assertThat(opts.getLimitOffset()).isEqualTo(5);
		assertThat(opts.getLimitCount()).isEqualTo(20);
	}

	@Test
	void sortByFieldDefaultsToAscending() {

		SearchOptions opts = SearchOptions.create().sortBy("price");

		assertThat(opts.getSortBy()).isEqualTo("price");
		assertThat(opts.getSortDirection()).isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void sortByFieldWithExplicitDirection() {

		SearchOptions opts = SearchOptions.create().sortBy("score", Sort.Direction.DESC);

		assertThat(opts.getSortBy()).isEqualTo("score");
		assertThat(opts.getSortDirection()).isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void noContentFlagIsSet() {
		assertThat(SearchOptions.create().includeContent(false).isIncludeContent()).isFalse();
	}

	@Test
	void withScoresFlagIsSet() {
		assertThat(SearchOptions.create().scores(true).isScores()).isTrue();
	}

	@Test
	void withSortKeysFlagIsSet() {
		assertThat(SearchOptions.create().sortKeys(true).isSortKeys()).isTrue();
	}

	@Test
	void returnFieldsStoresFields() {

		SearchOptions opts = SearchOptions.create().returnFields("title", "price", "status");

		assertThat(opts.getReturnFields()).containsExactly("title", "price", "status");
	}

	@Test
	void returnFieldsListIsImmutable() {

		SearchOptions opts = SearchOptions.create().returnFields("title");

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> opts.getReturnFields().add("extra"));
	}

	@Test
	void languageIsStored() {
		assertThat(SearchOptions.create().language("french").getLanguage()).isEqualTo("french");
	}

	@Test
	void builderIsFluentAndReturnsSameInstance() {

		SearchOptions opts = SearchOptions.create();
		SearchOptions result = opts
				.limit(0, 10)
				.sortBy("score", Sort.Direction.ASC)
				.scores(true)
				.language("english");

		assertThat(result).isSameAs(opts);
	}

	// --- New options tests ---

	@Test
	void verbatimFlagIsSet() {
		assertThat(SearchOptions.create().verbatim().isVerbatim()).isTrue();
	}

	@Test
	void verbatimDefaultsToFalse() {
		assertThat(SearchOptions.create().isVerbatim()).isFalse();
	}

	@Test
	void inOrderFlagIsSet() {
		assertThat(SearchOptions.create().inOrder().isInOrder()).isTrue();
	}

	@Test
	void inOrderDefaultsToFalse() {
		assertThat(SearchOptions.create().isInOrder()).isFalse();
	}

	@Test
	void explainScoreFlagIsSet() {
		assertThat(SearchOptions.create().explainScore().isExplainScore()).isTrue();
	}

	@Test
	void explainScoreDefaultsToFalse() {
		assertThat(SearchOptions.create().isExplainScore()).isFalse();
	}

	@Test
	void slopIsStored() {
		assertThat(SearchOptions.create().slop(3).getSlop()).isEqualTo(3);
	}

	@Test
	void slopDefaultsToNull() {
		assertThat(SearchOptions.create().getSlop()).isNull();
	}

	@Test
	void slopAllowsZero() {
		assertThat(SearchOptions.create().slop(0).getSlop()).isEqualTo(0);
	}

	@Test
	void slopRejectsNegativeValue() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> SearchOptions.create().slop(-1))
				.withMessageContaining("non-negative");
	}

	@Test
	void timeoutIsStored() {
		assertThat(SearchOptions.create().timeout(5000).getTimeout()).isEqualTo(5000L);
	}

	@Test
	void timeoutDefaultsToNull() {
		assertThat(SearchOptions.create().getTimeout()).isNull();
	}

	@Test
	void timeoutRejectsZero() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> SearchOptions.create().timeout(0))
				.withMessageContaining("positive");
	}

	@Test
	void timeoutRejectsNegativeValue() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> SearchOptions.create().timeout(-100))
				.withMessageContaining("positive");
	}

	@Test
	void expanderIsStored() {
		assertThat(SearchOptions.create().expander("myExpander").getExpander()).isEqualTo("myExpander");
	}

	@Test
	void expanderDefaultsToNull() {
		assertThat(SearchOptions.create().getExpander()).isNull();
	}

	@Test
	void scorerIsStored() {
		assertThat(SearchOptions.create().scorer("BM25").getScorer()).isEqualTo("BM25");
	}

	@Test
	void scorerDefaultsToNull() {
		assertThat(SearchOptions.create().getScorer()).isNull();
	}

	@Test
	void inKeysStoresKeys() {

		SearchOptions opts = SearchOptions.create().inKeys("doc:1", "doc:2", "doc:3");

		assertThat(opts.getInKeys()).containsExactly("doc:1", "doc:2", "doc:3");
	}

	@Test
	void inKeysDefaultsToNull() {
		assertThat(SearchOptions.create().getInKeys()).isNull();
	}

	@Test
	void inKeysListIsImmutable() {

		SearchOptions opts = SearchOptions.create().inKeys("doc:1");

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> opts.getInKeys().add("doc:2"));
	}

	@Test
	void inFieldsStoresFields() {

		SearchOptions opts = SearchOptions.create().inFields("title", "body");

		assertThat(opts.getInFields()).containsExactly("title", "body");
	}

	@Test
	void inFieldsDefaultsToNull() {
		assertThat(SearchOptions.create().getInFields()).isNull();
	}

	@Test
	void inFieldsListIsImmutable() {

		SearchOptions opts = SearchOptions.create().inFields("title");

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> opts.getInFields().add("body"));
	}

	/* Think about how we want to achieve changing the default count */

	@Test
	void defaultLimitCountIsConfigurable() {

		long originalDefault = SearchOptions.DEFAULT_LIMIT_COUNT;
		try {
			SearchOptions opts = SearchOptions.create().limit(0, 500);
			assertThat(opts.getLimitCount()).isEqualTo(500);
		} finally {
//			SearchOptions.setDefaultLimitCount(originalDefault);
		}
	}

//	@Test
//	void setDefaultLimitCountRejectsZero() {
//		assertThatIllegalArgumentException()
//				.isThrownBy(() -> SearchOptions.setDefaultLimitCount(0))
//				.withMessageContaining("positive");
//	}
//
//	@Test
//	void setDefaultLimitCountRejectsNegativeValue() {
//		assertThatIllegalArgumentException()
//				.isThrownBy(() -> SearchOptions.setDefaultLimitCount(-1))
//				.withMessageContaining("positive");
//	}

	@Test
	void allNewOptionsCanBeChainedFluently() {

		SearchOptions opts = SearchOptions.create();
		SearchOptions result = opts
				.verbatim()
				.inOrder()
				.explainScore()
				.slop(2)
				.timeout(1000)
				.expander("custom")
				.scorer("BM25")
				.inKeys("key1", "key2")
				.inFields("field1", "field2");

		assertThat(result).isSameAs(opts);
		assertThat(opts.isVerbatim()).isTrue();
		assertThat(opts.isInOrder()).isTrue();
		assertThat(opts.isExplainScore()).isTrue();
		assertThat(opts.getSlop()).isEqualTo(2);
		assertThat(opts.getTimeout()).isEqualTo(1000L);
		assertThat(opts.getExpander()).isEqualTo("custom");
		assertThat(opts.getScorer()).isEqualTo("BM25");
		assertThat(opts.getInKeys()).containsExactly("key1", "key2");
		assertThat(opts.getInFields()).containsExactly("field1", "field2");
	}
}

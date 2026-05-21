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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SearchDocument}.
 *
 * @author Viktoriya Kutsarova
 */
class SearchDocumentUnitTests {

	@Test
	void constructorStoresFields() {

		Map<String, String> fields = new HashMap<>();
		fields.put("title", "Hello");
		fields.put("price", "42L");

		SearchDocument doc = new SearchDocument(fields);

		assertThat(doc.getFields()).containsEntry("title", "Hello").containsEntry("price", "42L");
	}

	@Test
	void emptyFieldsMapIsAccepted() {

		SearchDocument doc = new SearchDocument(Map.of());

		assertThat(doc.getFields()).isEmpty();
	}

	@Test
	void fieldsMapIsImmutable() {

		Map<String, String> fields = new HashMap<>();
		fields.put("k", "v");
		SearchDocument doc = new SearchDocument(fields);

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> doc.getFields().put("extra", "x"));
	}

	@Test
	void mutatingOriginalMapDoesNotAffectDocument() {

		Map<String, String> fields = new HashMap<>();
		fields.put("k", "v");
		SearchDocument doc = new SearchDocument(fields);

		fields.put("extra", "x");

		assertThat(doc.getFields()).containsOnlyKeys("k");
	}
}

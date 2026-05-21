/*
 * Copyright 2013-present the original author or authors.
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

import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.args.FlushMode;
import redis.clients.jedis.args.GeoUnit;
import redis.clients.jedis.args.ListPosition;
import redis.clients.jedis.args.SortingOrder;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.params.GeoSearchParam;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.HGetExParams;
import redis.clients.jedis.params.HSetExParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.GeoRadiusResponse;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.schemafields.GeoField;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.util.SafeEncoder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metric;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldIncrBy;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldSet;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldSubCommand;
import org.springframework.data.redis.connection.CompareCondition;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.search.IndexDataType;
import org.springframework.data.redis.search.IndexDefinition;
import org.springframework.data.redis.search.Schema;
import org.springframework.data.redis.search.SchemaField;
import org.springframework.data.redis.search.SearchDocument;
import org.springframework.data.redis.search.SearchHit;
import org.springframework.data.redis.search.SearchOptions;
import org.springframework.data.redis.search.SearchResult;
import org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs.Flag;
import org.springframework.data.redis.connection.RedisHashCommands;
import org.springframework.data.redis.connection.RedisListCommands.Position;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisServer;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.RedisStringCommands.BitOperation;
import org.springframework.data.redis.connection.RedisZSetCommands.ZAddArgs;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.connection.SortParameters;
import org.springframework.data.redis.connection.SortParameters.Order;
import org.springframework.data.redis.connection.SortParameters.Range;
import org.springframework.data.redis.connection.ValueEncoding;
import org.springframework.data.redis.connection.convert.Converters;
import org.springframework.data.redis.connection.convert.ListConverter;
import org.springframework.data.redis.connection.convert.StringToRedisClientInfoConverter;
import org.springframework.data.redis.connection.zset.DefaultTuple;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.data.redis.domain.geo.BoundingBox;
import org.springframework.data.redis.domain.geo.BoxShape;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.data.redis.domain.geo.RadiusShape;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Jedis type converters.
 *
 * @author Jennifer Hickey
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @author Jungtaek Lim
 * @author Mark Paluch
 * @author Ninad Divadkar
 * @author Guy Korland
 * @author dengliming
 * @author John Blum
 * @author Viktoriya Kutsarova
 * @author Yordan Tsintsov
 */
@SuppressWarnings("ConstantConditions")
abstract class JedisConverters extends Converters {

	public static final byte[] PLUS_BYTES;
	public static final byte[] MINUS_BYTES;
	public static final byte[] POSITIVE_INFINITY_BYTES;
	public static final byte[] NEGATIVE_INFINITY_BYTES;

	static {

		PLUS_BYTES = toBytes("+");
		MINUS_BYTES = toBytes("-");
		POSITIVE_INFINITY_BYTES = toBytes("+inf");
		NEGATIVE_INFINITY_BYTES = toBytes("-inf");

	}

	@Nullable
	static <T> Set<T> toSet(@Nullable List<T> list) {
		return list != null ? new LinkedHashSet<>(list) : null;
	}

	@SuppressWarnings("NullAway")
	public static Converter<String, @Nullable byte[]> stringToBytes() {
		return JedisConverters::toBytes;
	}

	static ListConverter<String, byte[]> stringListToByteList() {
		return new ListConverter<>(stringToBytes());
	}

	/**
	 * {@link ListConverter} converting jedis {@link redis.clients.jedis.resps.Tuple} to {@link Tuple}.
	 *
	 * @since 1.4
	 */
	static ListConverter<redis.clients.jedis.resps.Tuple, Tuple> tuplesToTuples() {
		return new ListConverter<>(JedisConverters::toTuple);
	}

	static Tuple toTuple(redis.clients.jedis.resps.Tuple source) {
		return new DefaultTuple(source.getBinaryElement(), source.getScore());
	}

	static List<Tuple> toTupleList(List<redis.clients.jedis.resps.Tuple> source) {
		return tuplesToTuples().convert(source);
	}

	/**
	 * Map a {@link Set} of {@link Tuple} by {@code value} to its {@code score}.
	 *
	 * @param tuples must not be {@literal null}.
	 * @since 2.0
	 */
	public static Map<byte[], Double> toTupleMap(Set<Tuple> tuples) {

		Assert.notNull(tuples, "Tuple set must not be null");

		Map<byte[], Double> args = new LinkedHashMap<>(tuples.size(), 1);

		for (Tuple tuple : tuples) {
			args.put(tuple.getValue(), tuple.getScore());
		}

		return args;
	}

	public static byte[] toBytes(Number source) {
		return toBytes(String.valueOf(source));
	}

	/**
	 * Convert the given {@link org.springframework.data.redis.core.Cursor.CursorId} into its binary representation.
	 *
	 * @param source must not be {@literal null}.
	 * @return the binary representation.
	 * @since 3.3
	 */
	static byte[] toBytes(Cursor.CursorId source) {
		return toBytes(source.getCursorId());
	}

	@Contract("null -> null;!null -> !null")
	public static byte @Nullable [] toBytes(@Nullable String source) {
		return source == null ? null : SafeEncoder.encode(source);
	}

	@Contract("null -> null;!null -> !null")
	public static @Nullable String toString(byte @Nullable [] source) {
		return source == null ? null : SafeEncoder.encode(source);
	}

	/**
	 * Convert the given {@code source} value to the corresponding {@link ValueEncoding}.
	 *
	 * @param source can be {@literal null}.
	 * @return the {@link ValueEncoding} for given {@code source}. Never {@literal null}.
	 * @since 2.1
	 */
	public static ValueEncoding toEncoding(byte @Nullable [] source) {
		return ValueEncoding.of(toString(source));
	}

	/**
	 * @since 1.3
	 */
	public static List<RedisClientInfo> toListOfRedisClientInformation(String source) {

		if (!StringUtils.hasText(source)) {
			return Collections.emptyList();
		}

		return StringToRedisClientInfoConverter.INSTANCE.convert(source.split("\\r?\\n"));
	}

	/**
	 * @since 1.4
	 */
	public static List<RedisServer> toListOfRedisServer(List<Map<String, String>> source) {
		return toList(it -> RedisServer.newServerFrom(Converters.toProperties(it)), source);
	}

	public static ListPosition toListPosition(Position source) {
		Assert.notNull(source, "list positions are mandatory");
		return (Position.AFTER.equals(source) ? ListPosition.AFTER : ListPosition.BEFORE);
	}

	public static byte[][] toByteArrays(Map<byte[], byte[]> source) {
		byte[][] result = new byte[source.size() * 2][];
		int index = 0;
		for (Map.Entry<byte[], byte[]> entry : source.entrySet()) {
			result[index++] = entry.getKey();
			result[index++] = entry.getValue();
		}
		return result;
	}

	@Contract("null -> null;!null -> !null")
	public static @Nullable SortingParams toSortingParams(@Nullable SortParameters params) {

		if (params == null) {
			return null;
		}

		SortingParams jedisParams = new SortingParams();
		byte[] byPattern = params.getByPattern();
		if (byPattern != null) {
			jedisParams.by(params.getByPattern());
		}
		byte[][] getPattern = params.getGetPattern();
		if (getPattern != null) {
			jedisParams.get(getPattern);
		}
		Range limit = params.getLimit();
		if (limit != null) {
			jedisParams.limit((int) limit.getStart(), (int) limit.getCount());
		}
		Order order = params.getOrder();
		if (order != null && order.equals(Order.DESC)) {
			jedisParams.desc();
		}
		Boolean isAlpha = params.isAlphabetic();
		if (isAlpha != null && isAlpha) {
			jedisParams.alpha();
		}

		return jedisParams;
	}

	public static BitOP toBitOp(BitOperation bitOp) {

		return switch (bitOp) {
			case AND -> BitOP.AND;
			case OR -> BitOP.OR;
			case NOT -> BitOP.NOT;
			case XOR -> BitOP.XOR;
			case DIFF -> BitOP.DIFF;
			case DIFF1 -> BitOP.DIFF1;
			case ANDOR -> BitOP.ANDOR;
			case ONE -> BitOP.ONE;
		};
	}

	/**
	 * Converts a given {@link org.springframework.data.domain.Range.Bound} to its binary representation suitable for
	 * {@literal ZRANGEBY*} commands, despite {@literal ZRANGEBYLEX}.
	 *
	 * @since 1.6
	 */
	public static byte[] boundaryToBytesForZRange(org.springframework.data.domain.Range.@Nullable Bound<?> boundary,
			byte[] defaultValue) {

		if (boundary == null || !boundary.isBounded()) {
			return defaultValue;
		}

		return boundaryToBytes(boundary, new byte[] {}, toBytes("("));
	}

	/**
	 * Converts a given {@link org.springframework.data.domain.Range.Bound} to its binary representation suitable for
	 * {@literal ZRANGEBYLEX} command.
	 *
	 * @since 1.6
	 */
	public static byte[] boundaryToBytesForZRangeByLex(
			org.springframework.data.domain.Range.@Nullable Bound<byte[]> boundary, byte[] defaultValue) {

		if (boundary == null || !boundary.isBounded()) {
			return defaultValue;
		}

		return boundaryToBytes(boundary, toBytes("["), toBytes("("));
	}

	/**
	 * Converts a given {@link Expiration} to the according {@code SET} command argument.
	 * <dl>
	 * <dt>{@link TimeUnit#MILLISECONDS}</dt>
	 * <dd>{@code PX}</dd>
	 * <dt>{@link TimeUnit#SECONDS}</dt>
	 * <dd>{@code EX}</dd>
	 * </dl>
	 *
	 * @param expiration must not be {@literal null}.
	 * @since 2.2
	 */
	public static SetParams toSetCommandExPxArgument(Expiration expiration) {
		return toSetCommandExPxArgument(expiration, SetParams.setParams());
	}

	/**
	 * Converts a given {@link Expiration} to the according {@code SET} command argument.
	 * <dl>
	 * <dt>{@link TimeUnit#MILLISECONDS}</dt>
	 * <dd>{@code PX}</dd>
	 * <dt>{@link TimeUnit#SECONDS}</dt>
	 * <dd>{@code EX}</dd>
	 * </dl>
	 *
	 * @param expiration must not be {@literal null}.
	 * @since 2.2
	 */
	public static SetParams toSetCommandExPxArgument(Expiration expiration, SetParams params) {

		SetParams paramsToUse = params == null ? SetParams.setParams() : params;

		if (expiration.isKeepTtl()) {
			return paramsToUse.keepttl();
		}

		if (expiration.isPersistent()) {
			return paramsToUse;
		}

		ExpirationAdapter adapter = ExpirationAdapter.of(expiration);

		if (adapter.isPrecise()) {
			return adapter.apply(paramsToUse::px, paramsToUse::pxAt);
		}

		return adapter.apply(paramsToUse::ex, paramsToUse::exAt);
	}

	/**
	 * Converts a given {@link Expiration} to the according {@code GETEX} command argument depending on
	 * {@link Expiration#isUnixTimestamp()}.
	 * <dl>
	 * <dt>{@link TimeUnit#MILLISECONDS}</dt>
	 * <dd>{@code PX|PXAT}</dd>
	 * <dt>{@link TimeUnit#SECONDS}</dt>
	 * <dd>{@code EX|EXAT}</dd>
	 * </dl>
	 *
	 * @param expiration must not be {@literal null}.
	 * @since 2.6
	 */
	static GetExParams toGetExParams(Expiration expiration) {
		return toGetExParams(expiration, new GetExParams());
	}

	static GetExParams toGetExParams(Expiration expiration, GetExParams params) {

		if (expiration.isPersistent()) {
			return params.persist();
		}

		ExpirationAdapter adapter = ExpirationAdapter.of(expiration);

		if (adapter.isPrecise()) {
			return adapter.apply(e -> params.px(e), a -> params.pxAt(a));
		}

		return adapter.apply(e -> params.ex(e), a -> params.exAt(a));
	}

	/**
	 * Converts a given {@link Expiration} and {@link SetCondition} to the according {@link SetParams}.
	 *
	 * @param expiration can be {@literal null}.
	 * @param condition can be {@literal null}.
	 * @since 4.1
	 */
	static SetParams toSetParams(Expiration expiration, SetCondition condition) {

		SetParams params = toSetCommandExPxArgument(expiration, SetParams.setParams());

		switch (condition.getKeyCondition()) {
			case UPSERT -> {}
			case IF_ABSENT -> params.nx();
			case IF_PRESENT -> params.xx();
		}

		CompareCondition compareCondition = condition.getCompareCondition();

		if (compareCondition != null) {
			params.condition(toCompareCondition(compareCondition));
		}

		return params;
	}

	/**
	 * Converts a given {@link CompareCondition} to the according {@code DELEX} command argument.
	 *
	 * @param condition must not be {@literal null}.
	 * @since 4.1
	 */
	static redis.clients.jedis.util.CompareCondition toCompareCondition(CompareCondition condition) {
		return switch (condition.getComparison()) {
			case DIGEST ->
				condition.getOperator() == org.springframework.data.redis.connection.CompareCondition.ComparisonOperator.EQUALS
						? redis.clients.jedis.util.CompareCondition.digestEq(condition.getValue().toString())
						: redis.clients.jedis.util.CompareCondition.digestNe(condition.getValue().toString());
			case VALUE ->
				condition.getOperator() == org.springframework.data.redis.connection.CompareCondition.ComparisonOperator.EQUALS
						? redis.clients.jedis.util.CompareCondition.valueEq(condition.getValue().asBytes())
						: redis.clients.jedis.util.CompareCondition.valueNe(condition.getValue().asBytes());
		};
	}

	/**
	 * Converts a given {@link Expiration} to the according {@code HGETEX} command argument depending on
	 * {@link Expiration#isUnixTimestamp()}.
	 * <dl>
	 * <dt>{@link TimeUnit#MILLISECONDS}</dt>
	 * <dd>{@code PX|PXAT}</dd>
	 * <dt>{@link TimeUnit#SECONDS}</dt>
	 * <dd>{@code EX|EXAT}</dd>
	 * </dl>
	 *
	 * @param expiration can be {@literal null}.
	 * @since 4.0
	 */
	static HGetExParams toHGetExParams(@Nullable Expiration expiration) {

		HGetExParams params = new HGetExParams();

		if (expiration == null) {
			return params;
		}

		if (expiration.isPersistent()) {
			return params.persist();
		}

		ExpirationAdapter adapter = ExpirationAdapter.of(expiration);

		if (adapter.isPrecise()) {
			return adapter.apply(e -> params.px(e), a -> params.pxAt(a));
		}

		return adapter.apply(e -> params.ex(e), a -> params.exAt(a));
	}

	/**
	 * Converts a given {@link RedisHashCommands.HashFieldSetOption} and {@link Expiration} to the according
	 * {@code HSETEX} command argument.
	 * <dl>
	 * <dt>{@link RedisHashCommands.HashFieldSetOption#ifNoneExist()}</dt>
	 * <dd>{@code FNX}</dd>
	 * <dt>{@link RedisHashCommands.HashFieldSetOption#ifAllExist()}</dt>
	 * <dd>{@code FXX}</dd>
	 * <dt>{@link RedisHashCommands.HashFieldSetOption#upsert()}</dt>
	 * <dd>no condition flag</dd>
	 * </dl>
	 * <dl>
	 * <dt>{@link TimeUnit#MILLISECONDS}</dt>
	 * <dd>{@code PX|PXAT}</dd>
	 * <dt>{@link TimeUnit#SECONDS}</dt>
	 * <dd>{@code EX|EXAT}</dd>
	 * </dl>
	 *
	 * @param condition must not be {@literal null}; use {@code UPSERT} to omit FNX/FXX.
	 * @param expiration can be {@literal null} to omit TTL.
	 * @return never {@literal null}.
	 * @since 4.0
	 */
	static HSetExParams toHSetExParams(RedisHashCommands.@NonNull HashFieldSetOption condition,
			@Nullable Expiration expiration) {

		HSetExParams params = new HSetExParams();

		switch (condition) {
			case IF_NONE_EXIST -> params.fnx();
			case IF_ALL_EXIST -> params.fxx();
		}

		if (expiration == null || expiration.isPersistent()) {
			return params;
		}

		if (expiration.isKeepTtl()) {
			return params.keepTtl();
		}

		ExpirationAdapter adapter = ExpirationAdapter.of(expiration);

		if (adapter.isPrecise()) {
			return adapter.apply(e -> params.px(e), a -> params.pxAt(a));
		}

		return adapter.apply(e -> params.ex(e), a -> params.exAt(a));
	}

	private static byte[] boundaryToBytes(org.springframework.data.domain.Range.Bound<?> boundary, byte[] inclPrefix,
			byte[] exclPrefix) {

		byte[] prefix = boundary.isInclusive() ? inclPrefix : exclPrefix;
		byte[] value;
		Object theValue = boundary.getValue().get();
		if (theValue instanceof byte[] bytes) {
			value = bytes;
		} else if (theValue instanceof Number number) {
			value = toBytes(number);
		} else if (theValue instanceof String string) {
			value = toBytes(string);
		} else {
			throw new IllegalArgumentException("Cannot convert %s to binary format".formatted(boundary.getValue()));
		}

		ByteBuffer buffer = ByteBuffer.allocate(prefix.length + value.length);
		buffer.put(prefix);
		buffer.put(value);
		return buffer.array();

	}

	/**
	 * Convert {@link ScanOptions} to Jedis {@link ScanParams}.
	 */
	public static ScanParams toScanParams(ScanOptions options) {

		ScanParams sp = new ScanParams();

		if (!options.equals(ScanOptions.NONE)) {
			if (options.getCount() != null) {
				sp.count(options.getCount().intValue());
			}
			byte[] pattern = options.getBytePattern();
			if (pattern != null) {
				sp.match(pattern);
			}
		}
		return sp;
	}

	static Long toTime(List<String> source, TimeUnit timeUnit) {

		Assert.notEmpty(source, "Received invalid result from server; Expected 2 items in collection");
		Assert.isTrue(source.size() == 2,
				"Received invalid nr of arguments from redis server; Expected 2 received " + source.size());
		Assert.notNull(timeUnit, "TimeUnit must not be null");

		return toTimeMillis(source.get(0), source.get(1), timeUnit);
	}

	/**
	 * @since 1.8
	 */
	@SuppressWarnings("NullAway")
	public static List<String> toStrings(List<byte[]> source) {
		return toList(JedisConverters::toString, source);
	}

	private static <S, T> List<T> toList(Converter<S, @Nullable T> converter, @Nullable Collection<S> source) {

		if (source == null || source.isEmpty()) {
			return Collections.emptyList();
		}

		List<T> target = new ArrayList<>(source.size());

		for (S s : source) {
			target.add(converter.convert(s));
		}

		return target;
	}

	/**
	 * @since 1.8
	 */
	@SuppressWarnings("NullAway")
	public static ListConverter<redis.clients.jedis.GeoCoordinate, @Nullable Point> geoCoordinateToPointConverter() {
		return new ListConverter<>(JedisConverters::toPoint);
	}

	/**
	 * @since 2.5
	 */
	@Nullable
	static Point toPoint(redis.clients.jedis.@Nullable GeoCoordinate geoCoordinate) {
		return geoCoordinate == null ? null : new Point(geoCoordinate.getLongitude(), geoCoordinate.getLatitude());
	}

	/**
	 * Convert {@link Point} into {@link GeoCoordinate}.
	 *
	 * @since 1.8
	 */
	public static GeoCoordinate toGeoCoordinate(Point source) {
		return new redis.clients.jedis.GeoCoordinate(source.getX(), source.getY());
	}

	/**
	 * Get a {@link Converter} capable of converting {@link GeoRadiusResponse} into {@link GeoResults}.
	 *
	 * @since 1.8
	 */
	public static Converter<List<GeoRadiusResponse>, GeoResults<GeoLocation<byte[]>>> geoRadiusResponseToGeoResultsConverter(
			Metric metric) {
		return GeoResultsConverterFactory.INSTANCE.forMetric(metric);
	}

	/**
	 * Convert {@link Metric} into {@link GeoUnit}.
	 *
	 * @since 1.8
	 */
	public static GeoUnit toGeoUnit(Metric metric) {

		Metric metricToUse = metric == null || ObjectUtils.nullSafeEquals(Metrics.NEUTRAL, metric) ? DistanceUnit.METERS
				: metric;
		return ObjectUtils.caseInsensitiveValueOf(GeoUnit.values(), metricToUse.getAbbreviation());
	}

	/**
	 * Convert {@link ZAddArgs} to {@link ZAddParams}.
	 *
	 * @param source must not be {@literal null}.
	 * @return new instance of {@link ZAddParams}.
	 * @since 2.5
	 */
	static ZAddParams toZAddParams(ZAddArgs source) {

		if (source.isEmpty()) {
			return new ZAddParams();
		}

		ZAddParams target = new ZAddParams();

		if (source.contains(ZAddArgs.Flag.GT)) {
			target.gt();
		}
		if (source.contains(ZAddArgs.Flag.LT)) {
			target.lt();
		}
		if (source.contains(ZAddArgs.Flag.XX)) {
			target.xx();
		}
		if (source.contains(ZAddArgs.Flag.NX)) {
			target.nx();
		}
		if (source.contains(ZAddArgs.Flag.CH)) {
			target.ch();
		}

		return target;
	}

	/**
	 * Convert {@link GeoRadiusCommandArgs} into {@link GeoRadiusParam}.
	 *
	 * @since 1.8
	 */
	@SuppressWarnings("NullAway")
	public static GeoRadiusParam toGeoRadiusParam(GeoRadiusCommandArgs source) {

		GeoRadiusParam param = GeoRadiusParam.geoRadiusParam();
		if (source == null) {
			return param;
		}

		if (source.hasFlags()) {
			for (Flag flag : source.getFlags()) {
				switch (flag) {
					case WITHCOORD -> param.withCoord();
					case WITHDIST -> param.withDist();
				}
			}
		}

		if (source.hasSortDirection()) {
			switch (source.getSortDirection()) {
				case ASC -> param.sortAscending();
				case DESC -> param.sortDescending();
			}
		}

		if (source.hasLimit()) {
			param.count(source.getLimit().intValue());
		}

		return param;
	}

	/**
	 * Convert a timeout to seconds using {@code double} representation including fraction of seconds.
	 *
	 * @since 2.6
	 */
	static double toSeconds(long timeout, TimeUnit unit) {

		switch (unit) {
			case MILLISECONDS, MICROSECONDS, NANOSECONDS -> {
				return unit.toMillis(timeout) / 1000d;
			}
			default -> {
				return unit.toSeconds(timeout);
			}
		}
	}

	/**
	 * Convert given {@link BitFieldSubCommands} into argument array.
	 *
	 * @return never {@literal null}.
	 * @since 1.8
	 */
	public static byte[][] toBitfieldCommandArguments(BitFieldSubCommands source) {

		List<byte[]> args = new ArrayList<>(source.getSubCommands().size() * 4);

		for (BitFieldSubCommand command : source.getSubCommands()) {

			if (command instanceof BitFieldIncrBy) {

				BitFieldIncrBy.Overflow overflow = ((BitFieldIncrBy) command).getOverflow();
				if (overflow != null) {
					args.add(JedisConverters.toBytes("OVERFLOW"));
					args.add(JedisConverters.toBytes(overflow.name()));
				}
			}

			args.add(JedisConverters.toBytes(command.getCommand()));
			args.add(JedisConverters.toBytes(command.getType().asString()));
			args.add(JedisConverters.toBytes(command.getOffset().asString()));

			if (command instanceof BitFieldSet) {
				args.add(JedisConverters.toBytes(((BitFieldSet) command).getValue()));
			} else if (command instanceof BitFieldIncrBy) {
				args.add(JedisConverters.toBytes(((BitFieldIncrBy) command).getValue()));
			}
		}

		return args.toArray(new byte[0][0]);
	}

	static FlushMode toFlushMode(RedisServerCommands.@Nullable FlushOption option) {

		if (option == null) {
			return FlushMode.SYNC;
		}

		return switch (option) {
			case ASYNC -> FlushMode.ASYNC;
			case SYNC -> FlushMode.SYNC;
		};
	}

	static GeoSearchParam toGeoSearchParams(GeoReference<byte[]> reference, GeoShape predicate,
			RedisGeoCommands.GeoCommandArgs args) {

		Assert.notNull(reference, "GeoReference must not be null");
		Assert.notNull(predicate, "GeoShape must not be null");
		Assert.notNull(args, "GeoSearchCommandArgs must not be null");

		GeoSearchParam param = GeoSearchParam.geoSearchParam();

		configureGeoReference(reference, param);

		if (args.getLimit() != null) {

			boolean hasAnyLimit = args.getFlags().contains(Flag.ANY);
			param.count(Math.toIntExact(args.getLimit()), hasAnyLimit);
		}

		if (args.getSortDirection() != null) {

			if (args.getSortDirection() == Sort.Direction.ASC) {
				param.asc();
			} else {
				param.desc();
			}
		}

		if (args.getFlags().contains(Flag.WITHDIST)) {
			param.withDist();
		}

		if (args.getFlags().contains(Flag.WITHCOORD)) {
			param.withCoord();
		}

		return getGeoSearchParam(predicate, param);
	}

	private static GeoSearchParam getGeoSearchParam(GeoShape predicate, GeoSearchParam param) {

		if (predicate instanceof RadiusShape) {

			Distance radius = ((RadiusShape) predicate).getRadius();

			param.byRadius(radius.getValue(), toGeoUnit(radius.getMetric()));

			return param;
		}

		if (predicate instanceof BoxShape boxPredicate) {

			BoundingBox boundingBox = boxPredicate.getBoundingBox();

			param.byBox(boundingBox.getWidth().getValue(), boundingBox.getHeight().getValue(),
					toGeoUnit(boxPredicate.getMetric()));

			return param;
		}

		throw new IllegalArgumentException("Cannot convert %s to Jedis GeoSearchParam".formatted(predicate));
	}

	private static void configureGeoReference(GeoReference<byte[]> reference, GeoSearchParam param) {

		if (reference instanceof GeoReference.GeoMemberReference) {

			param.fromMember(toString(((GeoReference.GeoMemberReference<byte[]>) reference).getMember()));
			return;
		}

		if (reference instanceof GeoReference.GeoCoordinateReference<?> coordinates) {

			param.fromLonLat(coordinates.getLongitude(), coordinates.getLatitude());
			return;
		}

		throw new IllegalArgumentException("Cannot extract Geo Reference from %s".formatted(reference));
	}

	public static HostAndPort toHostAndPort(RedisNode node) {
		return new HostAndPort(node.getRequiredHost(), node.getPortOr(Protocol.DEFAULT_PORT));
	}

	/**
	 * @author Christoph Strobl
	 * @since 1.8
	 */
	enum GeoResultsConverterFactory {

		INSTANCE;

		Converter<List<GeoRadiusResponse>, GeoResults<GeoLocation<byte[]>>> forMetric(Metric metric) {
			return new GeoResultsConverter(
					ObjectUtils.nullSafeEquals(Metrics.NEUTRAL, metric) ? DistanceUnit.METERS : metric);
		}

		private static class GeoResultsConverter
				implements Converter<List<GeoRadiusResponse>, GeoResults<GeoLocation<byte[]>>> {

			private final Metric metric;

			public GeoResultsConverter(Metric metric) {
				this.metric = metric;
			}

			@Override
			public GeoResults<GeoLocation<byte[]>> convert(List<GeoRadiusResponse> source) {

				List<GeoResult<GeoLocation<byte[]>>> results = new ArrayList<>(source.size());

				Converter<GeoRadiusResponse, GeoResult<GeoLocation<byte[]>>> converter = GeoResultConverterFactory.INSTANCE
						.forMetric(metric);
				for (GeoRadiusResponse result : source) {
					results.add(converter.convert(result));
				}

				return new GeoResults<>(results, metric);
			}
		}
	}

	/**
	 * @author Christoph Strobl
	 * @since 1.8
	 */
	enum GeoResultConverterFactory {

		INSTANCE;

		Converter<GeoRadiusResponse, GeoResult<GeoLocation<byte[]>>> forMetric(Metric metric) {
			return new GeoResultConverter(metric);
		}

		private static class GeoResultConverter implements Converter<GeoRadiusResponse, GeoResult<GeoLocation<byte[]>>> {

			private final Metric metric;

			public GeoResultConverter(Metric metric) {
				this.metric = metric;
			}

			@Override
			@SuppressWarnings("NullAway")
			public GeoResult<GeoLocation<byte[]>> convert(GeoRadiusResponse source) {

				Point point = JedisConverters.toPoint(source.getCoordinate());

				return new GeoResult<>(new GeoLocation<>(source.getMember(), point),
						new Distance(source.getDistance(), metric));
			}
		}
	}

	/**
	 * Adapter to apply {@link Expiration}.
	 */
	record ExpirationAdapter(Expiration expiration) {

		ExpirationAdapter {
			Assert.isTrue(!expiration.isPersistent(), "Expiration does not define an expiry");
		}

		/**
		 * Create a new ExpirationAdapter.
		 *
		 * @param expiration
		 * @return
		 */
		public static ExpirationAdapter of(Expiration expiration) {
			return new ExpirationAdapter(expiration);
		}

		public boolean isPrecise() {
			return expiration.isPrecise();
		}

		/**
		 * Apply expiration to
		 *
		 * @param expire expiration mapping function.
		 * @param expireAt expiration-at mapping function.
		 */
		public <T> T apply(LongFunction<T> expire, LongFunction<T> expireAt) {

			long ttl = isPrecise() ? expiration.getExpirationTimeInMilliseconds() : expiration.getExpirationTimeInSeconds();

			return expiration.isUnixTimestamp() ? expireAt.apply(ttl) : expire.apply(ttl);
		}

	}

	// -------------------------------------------------------------------------
	// Search Model Converters
	// -------------------------------------------------------------------------

	/**
	 * Convert an {@link IndexDefinition} to a Jedis {@link FTCreateParams}.
	 *
	 * @param definition the index definition
	 * @return the Jedis FTCreateParams
	 * @since 4.1
	 */
	public static FTCreateParams toFTCreateParams(IndexDefinition definition) {

		FTCreateParams params = FTCreateParams.createParams();

		params.on(toJedisIndexDataType(definition.getOn()));

		List<String> prefixes = definition.getPrefixes();
		if (!prefixes.isEmpty()) {
			params.prefix(prefixes.toArray(new String[0]));
		}

		String filter = definition.getFilter();
		if (filter != null) {
			params.filter(filter);
		}

		org.springframework.data.redis.search.Language language = definition.getLanguage();
		if (language != null) {
			params.language(language.getValue());
		}

		String languageField = definition.getLanguageField();
		if (languageField != null) {
			params.languageField(languageField);
		}

		Double score = definition.getScore();
		if (score != null) {
			params.score(score);
		}

		String scoreField = definition.getScoreField();
		if (scoreField != null) {
			params.scoreField(scoreField);
		}

		if (definition.isLargeSchema()) {
			params.maxTextFields();
		}

		Long expiresAfterIdleSeconds = definition.getTemporary();
		if (expiresAfterIdleSeconds != null) {
			params.temporary(expiresAfterIdleSeconds);
		}

		if (!definition.isTermOffsets()) {
			params.noOffsets();
		}

		if (definition.isHighlight()) {
			params.noHL();
		}

		if (!definition.isStoreFields()) {
			params.noFields();
		}

		if (!definition.isTermFrequencies()) {
			params.noFreqs();
		}

		List<String> stopwords = definition.getStopwords();
		if (stopwords != null) {
			params.stopwords(stopwords.toArray(new String[0]));
		}

		if (!definition.isInitialScan()) {
			params.skipInitialScan();
		}

		return params;
	}

	/**
	 * Convert a {@link Schema} to a list of Jedis {@link redis.clients.jedis.search.schemafields.SchemaField} instances.
	 *
	 * @param schema the schema
	 * @return the list of Jedis SchemaField instances
	 * @since 4.1
	 */
	public static List<redis.clients.jedis.search.schemafields.SchemaField> toSchemaFields(Schema schema) {

		List<redis.clients.jedis.search.schemafields.SchemaField> result = new ArrayList<>();

		for (SchemaField field : schema.getFields()) {
			result.add(toSchemaField(field));
		}

		return result;
	}

	private static redis.clients.jedis.search.schemafields.SchemaField toSchemaField(SchemaField field) {

		if (field instanceof org.springframework.data.redis.search.TextField tf) {
			return toTextField(tf);
		}
		if (field instanceof org.springframework.data.redis.search.TagField tf) {
			return toTagField(tf);
		}
		if (field instanceof org.springframework.data.redis.search.NumericField nf) {
			return toNumericField(nf);
		}
		if (field instanceof org.springframework.data.redis.search.GeoField gf) {
			return toGeoField(gf);
		}

		throw new IllegalArgumentException("Unsupported field type: " + field.getClass().getName());
	}

	private static TextField toTextField(org.springframework.data.redis.search.TextField source) {

		TextField field = TextField.of(source.getName());

		if (source.getAlias() != null) {
			field.as(source.getAlias());
		}
		if (!source.isIndexed()) {
			field.noIndex();
		}
		if (source.isIndexMissing()) {
			field.indexMissing();
		}
		if (!source.isNormalized()) {
			field.sortableUNF();
		} else if (source.isSortable()) {
			field.sortable();
		}
		if (source.getWeight() != 1.0) {
			field.weight(source.getWeight());
		}
		if (!source.isStemming()) {
			field.noStem();
		}
		if (source.getPhonetic() != null) {
			field.phonetic(source.getPhonetic().getValue());
		}
		if (source.isSuffixTrie()) {
			field.withSuffixTrie();
		}
		if (source.isIndexEmpty()) {
			field.indexEmpty();
		}

		return field;
	}

	private static TagField toTagField(org.springframework.data.redis.search.TagField source) {

		TagField field = TagField.of(source.getName());

		if (source.getAlias() != null) {
			field.as(source.getAlias());
		}
		if (!source.isIndexed()) {
			field.noIndex();
		}
		if (source.isIndexMissing()) {
			field.indexMissing();
		}
		if (!source.isNormalized()) {
			field.sortableUNF();
		} else if (source.isSortable()) {
			field.sortable();
		}
		if (source.getSeparator() != ',') {
			field.separator(source.getSeparator());
		}
		if (source.isCaseSensitive()) {
			field.caseSensitive();
		}
		if (source.isSuffixTrie()) {
			field.withSuffixTrie();
		}
		if (source.isIndexEmpty()) {
			field.indexEmpty();
		}

		return field;
	}

	private static NumericField toNumericField(org.springframework.data.redis.search.NumericField source) {

		NumericField field = NumericField.of(source.getName());

		if (source.getAlias() != null) {
			field.as(source.getAlias());
		}
		if (!source.isIndexed()) {
			field.noIndex();
		}
		if (source.isIndexMissing()) {
			field.indexMissing();
		}
		// TODO: Uncomment when Jedis exposes sortableUNF() on NumericField
		// if (source.isUnnormalized()) {
		// 	field.sortableUNF();
		// } else if (source.isSortable()) {
		// 	field.sortable();
		// }

		if (source.isSortable()) {
			field.sortable();
		}

		return field;
	}

	private static GeoField toGeoField(org.springframework.data.redis.search.GeoField source) {

		GeoField field = GeoField.of(source.getName());

		if (source.getAlias() != null) {
			field.as(source.getAlias());
		}
		if (!source.isIndexed()) {
			field.noIndex();
		}
		if (source.isIndexMissing()) {
			field.indexMissing();
		}
		if (source.isSortable()) {
			field.sortable();
		}

		return field;
	}

	private static redis.clients.jedis.search.IndexDataType toJedisIndexDataType(IndexDataType dataType) {
		return switch (dataType) {
			case HASH -> redis.clients.jedis.search.IndexDataType.HASH;
			case JSON -> redis.clients.jedis.search.IndexDataType.JSON;
		};
	}

	/**
	 * Convert {@link SearchOptions} to a Jedis {@link FTSearchParams}.
	 *
	 * @param options the search options
	 * @return the Jedis FTSearchParams
	 * @since 4.1
	 */
	public static FTSearchParams toFTSearchParams(SearchOptions options) {

		FTSearchParams params = FTSearchParams.searchParams();

		params.limit(Math.toIntExact(options.getLimitOffset()), Math.toIntExact(options.getLimitCount()));

		if (!options.isIncludeContent()) {
			params.noContent();
		}

		if (options.isScores()) {
			params.withScores();
		}

		// Note: WITHSORTKEYS is not exposed by Jedis FTSearchParams

		if (options.isVerbatim()) {
			params.verbatim();
		}

		if (options.isInOrder()) {
			params.inOrder();
		}

		// Note: EXPLAINSCORE is not exposed by Jedis FTSearchParams

		if (options.getSlop() != null) {
			params.slop(options.getSlop());
		}

		if (options.getTimeout() != null) {
			params.timeout(options.getTimeout());
		}

		// There is no setter for expander in Jedis FTSearchParams

//		if (options.getExpander() != null) {
//			params.expander(options.getExpander());
//		}

		if (options.getScorer() != null) {
			params.scorer(options.getScorer());
		}

		if (options.getSortBy() != null) {
			SortingOrder order = options.getSortDirection() == Sort.Direction.DESC ? SortingOrder.DESC : SortingOrder.ASC;
			params.sortBy(options.getSortBy(), order);
		}

		List<String> returnFields = options.getReturnFields();
		if (returnFields != null && !returnFields.isEmpty()) {
			params.returnFields(returnFields.toArray(new String[0]));
		}

		if (options.getLanguage() != null) {
			params.language(options.getLanguage());
		}

		List<String> inKeys = options.getInKeys();
		if (inKeys != null && !inKeys.isEmpty()) {
			params.inKeys(inKeys);
		}

		List<String> inFields = options.getInFields();
		if (inFields != null && !inFields.isEmpty()) {
			params.inFields(inFields);
		}

		return params;
	}

	/**
	 * Convert a Jedis {@link redis.clients.jedis.search.SearchResult} to Spring Data {@link SearchResult}.
	 *
	 * @param source the Jedis search result
	 * @param options the search options used for the query
	 * @return the Spring Data SearchResult
	 * @since 4.1
	 */
	public static SearchResult toSearchResult(redis.clients.jedis.search.SearchResult source, SearchOptions options) {

		long totalResults = source.getTotalResults();
		List<Document> documents = source.getDocuments();
		List<SearchHit> hits = new ArrayList<>(documents.size());

		for (Document document : documents) {

			String id = document.getId();
			Double score = options.isScores() ? document.getScore() : null;

			Map<String, String> fields = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : document.getProperties()) {
				String value = toFieldValue(entry.getValue());
				if (value != null) {
					fields.put(entry.getKey(), value);
				}
			}

			hits.add(new SearchHit(id, score, null, new SearchDocument(fields)));
		}

		return new SearchResult(totalResults, hits);
	}

	private static @Nullable String toFieldValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof byte[] b) {
			return new String(b, StandardCharsets.UTF_8);
		}
		return value.toString();
	}

}

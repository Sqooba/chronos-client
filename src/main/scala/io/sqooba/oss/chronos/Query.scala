package io.sqooba.oss.chronos

import java.time.Instant
import io.sqooba.oss.chronos.Query.{ getStep, Qid, TransformFunction }
import io.sqooba.oss.promql
import io.sqooba.oss.timeseries.TimeSeries

import scala.concurrent.duration._
import io.sqooba.oss.timeseries.entity.TsId
import io.sqooba.oss.timeseries.immutable.EmptyTimeSeries
import zio.IO

// Very ugly way of defining the scaladoc link to the original transform function.
// Google scaladoc to see why this has no other option than to be so ugly.
// scalastyle:off line.size.limit
/**
 * @define transformLink [[io.sqooba.oss.chronos.Query!.transform(key:io\.sqooba\.oss\.chronos\.QueryKey,start:java\.time\.Instant,end:java\.time\.Instant,step*]]
 */
// scalastyle:on line.size.limit

// Scala style doesn't like the + operator.
// scalastyle:off method.name

/** Represents an abstract query for a time series result. */
sealed trait Query { self =>

  /** Group this with the other query. */
  def +(other: Query): Query = Query.Group(Seq(self, other))

  /** Group this with the other query. */
  def +(other: IO[InvalidQueryError, Query]): IO[InvalidQueryError, Query] =
    other.map(self + _)

  /**
   * Transform this query with the given transform function and set its new label, start
   * and end.
   *
   * @param key   that identifies the transformed query
   * @param start of the transformed query
   * @param end   of the transformed query
   * @param step  sampling step to take, think sampling frequency
   * @param f     function that transforms the result of this query to the transformed
   *              query. It is also passed the query properties [[io.sqooba.oss.chronos.Query$.Qid!]]
   *              for the transformed query.
   */
  // Note: this is very verbose, can we have fewer arguments? For example, do we want
  //   the step to be given here? Otherwise, move it out of Qid.
  def transform(
    key: QueryKey,
    start: Instant,
    end: Instant,
    step: FiniteDuration
  )(f: TransformFunction): Query =
    Query.Transform(Qid(key, start, end, step), self, f)

  /** See $$transformLink, with inferred step. */
  def transform(
    key: QueryKey,
    start: Instant,
    end: Instant
  )(f: TransformFunction): Query =
    transform(key, start, end, getStep(key.key))(f)

  /** See $$transformLink, with a raw string label. */
  def transform(
    label: String,
    start: Instant,
    end: Instant,
    step: FiniteDuration
  )(f: TransformFunction): Query =
    transform(QueryKey(label, Map()), start, end, step)(f)

  /** See $$transformLink, with a raw string label and inferred step. */
  def transform(
    label: String,
    start: Instant,
    end: Instant
  )(f: TransformFunction): Query =
    transform(QueryKey(label, Map()), start, end)(f)

  /** See $$transformLink, with a new tsId. */
  def transform[I <: ChronosEntityId](
    tsId: TsId[I],
    start: Instant,
    end: Instant,
    step: FiniteDuration
  )(f: TransformFunction): Query =
    transform(QueryKey.fromTsId(tsId), start, end, step)(f)

  /** See $$transformLink, with a new tsId and inferred step. */
  def transform[I <: ChronosEntityId](
    tsId: TsId[I],
    start: Instant,
    end: Instant
  )(f: TransformFunction): Query =
    transform(QueryKey.fromTsId(tsId), start, end)(f)
}

object Query {

  /**
   * Implicit class that decorates `IO[InvalidQueryError, Query]` with our query
   * combinator methods.
   */
  implicit class IOQuery(self: IO[InvalidQueryError, Query]) {

    /** Combine this with the other query. */
    def +(other: IO[InvalidQueryError, Query]): IO[InvalidQueryError, Query] =
      for { s <- self; o <- other } yield s + o

    /** Combine this with the other query. */
    def +(other: Query): IO[InvalidQueryError, Query] =
      self.map(_ + other)

    /** See $$transformLink. */
    def transform(
      key: QueryKey,
      start: Instant,
      end: Instant,
      step: FiniteDuration
    )(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(key, start, end, step)(f))

    /** See $$transformLink, with inferred step. */
    def transform(
      key: QueryKey,
      start: Instant,
      end: Instant
    )(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(key, start, end)(f))

    /** See $$transformLink,  with a raw string label. */
    def transform(
      label: String,
      start: Instant,
      end: Instant,
      step: FiniteDuration
    )(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(label, start, end, step)(f))

    /** See $$transformLink, with a raw string label and inferred step. */
    def transform(
      label: String,
      start: Instant,
      end: Instant
    )(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(label, start, end)(f))

    /** See $$transformLink, with a new tsId. */
    def transform[I <: ChronosEntityId](
      tsId: TsId[I],
      start: Instant,
      end: Instant,
      step: FiniteDuration
    )(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(tsId, start, end, step)(f))

    /** See $$transformLink, with a new tsId and inferred step. */
    def transform[I <: ChronosEntityId](
      tsId: TsId[I],
      start: Instant,
      end: Instant
    )(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(tsId, start, end)(f))
  }

  /**
   * Implicit class that decorates `IO[InvalidQueryError, Query]` with our query
   * combinator methods.
   */
  implicit class IORangeQuery(self: IO[InvalidQueryError, Query.Range]) {

    /** See [[io.sqooba.oss.chronos.Query.Range!.transform(key*]]. */
    def transform(key: QueryKey)(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(key)(f))

    /** See [[io.sqooba.oss.chronos.Query.Range!.transform(key*]], with a raw string label. */
    def transform(label: String)(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(label)(f))

    /** See [[io.sqooba.oss.chronos.Query.Range!.transform(key*]], with a new tsId. */
    def transform[I <: ChronosEntityId](tsId: TsId[I])(f: TransformFunction): IO[InvalidQueryError, Query] =
      self.map(_.transform(tsId)(f))
  }

  /** Represents the result of a query. */
  final case class Result(map: Map[QueryKey, TimeSeries[Double]]) {

    /** Get a time series from the result, indexed by matching TsId. */
    def getByTsId(tsId: TsId[_]): Option[TimeSeries[Double]] =
      map.find(_._1.matches(tsId)).map(_._2)

    /**
     * Get a time series from the result, indexed by matching TsId. Returns an empty
     * time series if there is no data for the given TsId.
     */
    def getByTsIdOrEmpty(tsId: TsId[_]): TimeSeries[Double] =
      getByTsId(tsId).getOrElse(EmptyTimeSeries)

    // Why is there no `byTsId` method that returns a `Map[TsId, TimeSeries[Double]]`?
    //
    // The reason is that this library cannot create `ChronosEntityId`s. It only defines
    // the trait but the users provide the implementation. Now, in order to create a
    // TsId from just the information we get returned by a query, we cannot recreate the
    // entity id that will equal (in its type) the one of the user.
    //
    // If, in the future, this becomes a problem, one could keep TsIds around with which
    // the queries are created, then match those to the returned data and use them. But
    // unless, the entity Id design significantly changes, it will stay impossible to
    // create entity ids from scratch in the library.

    /** Get a time series from the result, indexed by string key. */
    def getByQueryKey(key: String): Option[TimeSeries[Double]] =
      map.find(_._1.toPromQuery == key).map(_._2)

    /**
     * Get a time series from the result, indexed by string key. Returns an empty
     * time series if there is no data for the given TsId.
     */
    def getByQueryKeyOrEmpty(key: String): TimeSeries[Double] =
      getByQueryKey(key).getOrElse(EmptyTimeSeries)

    def byQueryKey: Map[String, TimeSeries[Double]] =
      map.map { case (key, value) => (key.toPromQuery, value) }

    def +(other: Result): Result = Result(map ++ other.map)
  }

  /**
   * Internal identifier for queries. This serves to deduplicate queries at execution,
   * see [[ChronosClient!.query]].
   *
   * @param key   the query key
   * @param start of the query
   * @param end   of the query
   * @param step  sampling rate for this query
   */
  final case class Qid(key: QueryKey, start: Instant, end: Instant, step: FiniteDuration)

  /** the empty query */
  final case object Empty extends Query

  /** Groups multiple queries together, they may be executed in parallel. */
  final case class Group private[chronos] (queries: Seq[Query]) extends Query

  object Group {

    /** Build a group via a var arg */
    def of(one: Query, two: Query, more: Query*): Group = Group(Seq(one, two) ++ more)
  }

  /** the base type for queries, fetches a time range of a given query key */
  final case class Range(
    id: Qid,
    timeout: Option[FiniteDuration]
  ) extends Query {

    /** Convert this query to an underlying PromQL range query. */
    def toPromQl: promql.RangeQuery =
      promql.RangeQuery(
        id.key.toPromQuery,
        id.start,
        id.end,
        id.step,
        timeout
      )

    /**
     * Transform this query with the given transform function and set its new label.
     *
     * @param key  that identifies the transformed query
     * @param f    function that transforms the result of this query to the transformed
     *             query. It is also passed the query properties [[Qid]] of the transformed
     *             query.
     */
    def transform(key: QueryKey)(f: TransformFunction): Query =
      this.transform(key, id.start, id.end, id.step)(f)

    /** See [[Range!.transform(key*]], with a raw string label. */
    def transform(label: String)(f: TransformFunction): Query =
      this.transform(label, id.start, id.end, id.step)(f)

    /** See [[Range!.transform(key*]], with a new tsId. */
    def transform[I <: ChronosEntityId](tsId: TsId[I])(f: TransformFunction): Query =
      this.transform(tsId, id.start, id.end, id.step)(f)
  }

  type IntermediateResult = Map[Qid, Result]
  type TransformFunction  = (Result, Qid) => TimeSeries[Double]

  /** Transforms an underlying query with the given function. */
  final case class Transform(
    id: Qid,
    underlying: Query,
    f: TransformFunction
  ) extends Query

  /**
   * Create a chronos query from an existing promql query
   *
   * @param query An underlying prometheus query
   * @return a chronos RangeQuery
   */
  def from(query: promql.RangeQuery): IO[InvalidQueryError, Range] =
    fromString(
      query.query,
      query.start,
      query.end,
      Some(query.step),
      query.timeout
    )

  /**
   * Create a chronos query from the given parameters.
   *
   * If no step/sampling rate is given, it will try to infer the sampling rate from the
   * label name of the tsId (default to 1 minute).
   *
   * @param tsId    time series identifier to run the query for
   * @param start   of the query
   * @param end     of the query
   * @param step    optional sampling rate for this query, otherwise the rate
   *                is inferred or defaults to 1 minute
   * @param timeout optional timeout for the query
   * @return a range query
   */
  def fromTsId[I <: ChronosEntityId](
    tsId: TsId[I],
    start: Instant,
    end: Instant,
    step: Option[FiniteDuration] = None,
    timeout: Option[FiniteDuration] = None
  ): Range =
    Range(
      Qid(
        QueryKey.fromTsId(tsId),
        start,
        end,
        step.getOrElse(getStep(tsId.label.value))
      ),
      timeout
    )

  /**
   * Create a chronos query from the given parameters.
   *
   * If no step/sampling rate is given, it will try to infer the sampling rate from raw
   * query string (default to 1 minute).
   *
   * @param query   a raw prometheus query
   * @param start   of the query
   * @param end     of the query
   * @param step    optional sampling rate for this query, in seconds,
   *                otherwise the rate is inferred or defaults to 1 minute
   * @param timeout optional timeout for the query
   * @return A range query
   */
  def fromString(
    query: String,
    start: Instant,
    end: Instant,
    step: Option[FiniteDuration] = None,
    timeout: Option[FiniteDuration] = None
  ): IO[InvalidQueryError, Range] =
    QueryKey.fromPromQuery(query).map { key =>
      Range(
        Qid(
          key,
          start,
          end,
          step.getOrElse(getStep(query))
        ),
        timeout
      )
    }

  def apply(queries: Query*): Query = group(queries)

  def group(queries: Seq[Query]): Query = queries match {
    case Seq()     => Empty
    case Seq(head) => head
    case _         => Group(queries)
  }

  /**
   * Create a query from a list of queries
   * @param queries a list of queries to compute
   * @return a chronos query
   */
  def group(queries: Seq[IO[InvalidQueryError, Query]]): IO[InvalidQueryError, Query] =
    IO.collectAll(queries).map(group)

  // Note: This deserves improvement (adding more units or make this more sophisticated with regexes?)
  private val stepPatterns = Seq(("_10m", 10.minutes), ("_1m", 1.minute))

  /**
   * Parse the sampling rate of a query from the label.
   *
   * @return the parsed sampling rate or 1 minute per default.
   */
  def getStep(label: String): FiniteDuration =
    stepPatterns.find {
      case (pattern, _) => label.contains(pattern)
    }.fold(1.minute)(_._2)

}

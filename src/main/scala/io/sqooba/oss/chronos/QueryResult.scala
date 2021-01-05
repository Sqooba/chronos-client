package io.sqooba.oss.chronos

import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.entity.TsId
import io.sqooba.oss.timeseries.immutable.EmptyTimeSeries

/** Represents the result of a query. */
final case class QueryResult(map: Map[QueryKey, TimeSeries[Double]]) extends AnyVal {

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
    map.find(_._1.matches(key)).map(_._2)

  /**
   * Get a time series from the result, indexed by string key. Returns an empty
   * time series if there is no data for the given TsId.
   */
  def getByQueryKeyOrEmpty(key: String): TimeSeries[Double] =
    getByQueryKey(key).getOrElse(EmptyTimeSeries)

  def byQueryKey: Map[String, TimeSeries[Double]] =
    map.map { case (key, value) => (key.toPromQuery, value) }

  // Scala style doesn't like the + operator.
  // scalastyle:off method.name

  def ++(other: QueryResult): QueryResult = QueryResult(map ++ other.map)
}

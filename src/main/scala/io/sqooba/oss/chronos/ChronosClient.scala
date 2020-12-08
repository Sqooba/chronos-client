package io.sqooba.oss.chronos

import com.typesafe.config.Config
import io.sqooba.oss.chronos.Chronos.ChronosService
import io.sqooba.oss.chronos.Query.{ IntermediateResult, Qid, Range, Result }
import io.sqooba.oss.promql.PrometheusService.PrometheusService
import io.sqooba.oss.promql.metrics.MatrixMetric
import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.immutable.TSEntry
import zio.{ IO, Task, TaskLayer, ULayer, URLayer, ZLayer }
import io.sqooba.oss.promql.{ MatrixResponseData, PrometheusClient, PrometheusService }

import scala.concurrent.duration._

/**
 * Implementation of the [[Chronos.ChronosService]]. Performs query deduplication in a
 * single query tree for queries that have the same key, start, end and sampling step.
 */
class ChronosClient(
  promService: PrometheusService
) extends Chronos.Service {

  // scalastyle:off import.grouping
  import ChronosClient._

  def query(
    query: Query
  ): IO[ChronosError, Result] = {

    // Recursively parses the query tree and executes leaf range queries and transforms.
    // Results are returned in the intermediate-results map which accumulates all range
    // and transform results that have already been executed once. This allows to
    // perform query deduplication by Qid.
    def loop(result: IntermediateResult, query: Query): IO[ChronosError, IntermediateResult] =
      query match {
        // Query groups are just sequentially executed for the moment.
        case Query.Group(head +: tail) =>
          tail.foldLeft(loop(result, head)) {
            case (acc, next) =>
              for {
                first  <- acc
                second <- loop(result ++ first, next)
              } yield first ++ second
          }

        // Leaf range queries are deduplicated by their Qid. If the query has already
        // been executed once in this tree, the result is directly reused.
        case query @ Query.Range(id, _) =>
          result
            .get(id)
            .fold(executeRangeQuery(query)) { response =>
              IO.succeed(Map(query.id -> response))
            }

        // First execute the underlying, then apply the transform function.
        case Query.Transform(id, underlying, f) =>
          loop(result, underlying).map(underlyingResult => f(underlyingResult, id))

        case Query.Empty => IO.succeed(result)
      }

    // Start the recursion with an empty intermediate-results map.
    // At then end, flatten the map to the result type.
    loop(Map(), query).map(i => Result(i.values.flatMap(_.map).toMap))
  }

  private def executeRangeQuery(
    query: Range
  ): IO[ChronosError, IntermediateResult] = (
    for {
      data <- PrometheusService
                .query(query.toPromQl)
                .mapError(e => UnderlyingClientError(e))
                .collect(TimeSeriesDataError("Expecting matrix")) {
                  case d: MatrixResponseData => d
                }

      tsData <- toTs(query.id, data)
    } yield Map(query.id -> tsData)
  ).provide(promService)

}

object ChronosClient {

  /**
   * Converts matrix metric data to time series data. First, it tries to parse a TsId
   * from the `__name__` tag in the response. Then it converts the data.
   *
   * @return a TsId along with its time series data or fails if the id cannot be parsed.
   */
  private[chronos] def toTs(
    queryId: Qid,
    matrixMetric: MatrixMetric
  ): IO[ChronosError, (QueryKey, TimeSeries[Double])] =
    for {
      key <- QueryKey.fromMatrixMetric(matrixMetric)
      data <- Task {
                TimeSeries
                  .ofOrderedEntriesSafe(
                    matrixMetric.values.map {
                      case (ts, value) =>
                        TSEntry(ts.toEpochMilli, value.toDouble, queryId.step.seconds.toMillis)
                    }
                  )
                  .trimRight(queryId.end.toEpochMilli)
              }.orElseFail(
                TimeSeriesDataError(s"Could not parse time series data from ${matrixMetric.values}")
              )
    } yield key -> data

  /**
   * See [[ChronosClient.toTs(q, m: MatrixMetric)]].
   */
  private[chronos] def toTs(queryId: Qid, results: MatrixResponseData): IO[ChronosError, Result] =
    IO.foreach(results.result)(m => toTs(queryId, m))
      .map(r => Result(r.toMap))

  def live(
    prometheusService: PrometheusService
  ): ULayer[ChronosService] = ZLayer.succeed(new ChronosClient(prometheusService))

  def live: URLayer[PrometheusService, ChronosService] =
    ZLayer.fromFunction { prom: PrometheusService =>
      new ChronosClient(prom)
    }

  /**
   * Returns a layer with a ChronosClient that is built with a default
   * PrometheusService. This allows clients to not depend on the  PrometheusService and
   * just use the ChronosService alone.
   */
  def liveDefault(): TaskLayer[ChronosService] =
    PrometheusClient.liveDefault >>> ChronosClient.live

  def liveFromConfig(config: Config): TaskLayer[ChronosService] =
    PrometheusClient.liveFromConfig(config) >>> ChronosClient.live

}

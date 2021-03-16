package io.sqooba.oss.chronos

import com.typesafe.config.Config
import io.sqooba.oss.chronos.Chronos.ChronosService
import io.sqooba.oss.chronos.Query.{ ExecutableQuery, IntermediateResult, Qid }
import io.sqooba.oss.promql.PrometheusService.PrometheusService
import io.sqooba.oss.promql.metrics.MatrixMetric
import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.immutable.TSEntry
import zio.{ Has, IO, RLayer, Task, TaskLayer, ULayer, URLayer, ZLayer }
import io.sqooba.oss.promql.{ MatrixResponseData, PrometheusClient, PrometheusService }

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
  ): IO[ChronosError, QueryResult] = {

    // Recursively parses the query tree and executes leaf range queries and transforms.
    // Results are returned in the intermediate-results map which accumulates all range
    // and transform results that have already been executed once. This allows to
    // perform query deduplication by Qid.
    def loop(intermediateResult: IntermediateResult, query: Query): IO[ChronosError, IntermediateResult] =
      query match {
        // Query groups are just sequentially executed for the moment.
        case Query.Group(head +: tail) =>
          tail.foldLeft(loop(intermediateResult, head)) {
            case (acc, next) =>
              for {
                first  <- acc
                second <- loop(intermediateResult ++ first, next)
              } yield first ++ second
          }

        // Leaf range/function queries are deduplicated by their Qid. If the query has
        // already been executed once in this tree, the result is directly reused.
        case query: ExecutableQuery =>
          intermediateResult
            .get(query.id)
            .fold(executeQuery(query)) { response =>
              IO.succeed(Map(query.id -> response))
            }

        // For a Transform, we **always also return the underlying queries' result**.
        //
        // Normally, first the underlying are run, then the transform is applied and
        // both are returned. If the underlying is already cached (in intermediate
        // result), it will be taken from there in the recursion.
        //
        // If the result of the transform is also already in cache, we still recurse,
        // assuming that the underlying is also cached, this will perform the same
        // fast lookup as described above.
        case Query.Transform(id, underlying, f) =>
          // Execute OR lookup underlying from cache.
          loop(intermediateResult, underlying).map { underlyingResult =>
            // Lookup transform result from cache, otherwise compute it.
            val transformResult = intermediateResult.getOrElse(
              id, {
                // The transform is represented by Result => TimeSeries[Double].
                // We flatten the intermediate results that we got so far as well and
                // insert the newly computed time series into the intermediate results
                // for future usage.
                val flattenedResults = underlyingResult.values.foldLeft(QueryResult(Map()))(_ ++ _)
                QueryResult(Map(id.key -> f(flattenedResults, id)))
              }
            )
            underlyingResult ++ Map(id -> transformResult)
          }

        case Query.Empty => IO.succeed(intermediateResult)
      }

    // Start the recursion with an empty intermediate-results map.
    // At then end, flatten the map to the result type.
    loop(Map(), query).map(i => i.values.foldLeft(QueryResult(Map()))(_ ++ _))
  }

  private def executeQuery(query: ExecutableQuery): IO[ChronosError, IntermediateResult] = (
    for {
      data <- PrometheusService
                .query(query.toPromQl)
                .mapError(e => UnderlyingClientError(e))
                .collect(TimeSeriesDataError("Expecting matrix")) {
                  case d: MatrixResponseData => d
                }

      queryResult <- toTs(query.id, data)
    } yield Map(query.id -> queryResult)
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
      // If the query has a function, then the name is not returned and we fall back to
      // the key of the original query.
      key <- QueryKey.fromMatrixMetric(matrixMetric).catchSome {
               case IdParsingError(_) => IO.succeed(queryId.key)
             }
      data <- Task {
                TimeSeries
                  .ofOrderedEntriesSafe(
                    matrixMetric.values.map {
                      case (ts, value) =>
                        TSEntry(ts.toEpochMilli, value.toDouble, queryId.step.toMillis)
                    }
                  )
                  .slice(queryId.start.toEpochMilli, queryId.end.toEpochMilli)
              }.orElseFail(
                TimeSeriesDataError(s"Could not parse data for ${queryId}. Got ${matrixMetric.values}")
              )
    } yield key -> data

  /**
   * See [[ChronosClient.toTs(q, m: MatrixMetric)]].
   */
  private[chronos] def toTs(queryId: Qid, results: MatrixResponseData): IO[ChronosError, QueryResult] =
    IO.foreach(results.result)(m => toTs(queryId, m))
      .map(r => QueryResult(r.toMap))

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

  /**
   * Returns a layer with a ChronosClient. If you have the config as a regular object,
   * you can always do the following:
   *
   * {{{
   *   zio.Task(config).toLayer >>> ChronosClient.liveFromConfig
   * }}}
   */
  def liveFromConfig: RLayer[Has[Config], ChronosService] =
    PrometheusClient.liveFromConfig >>> ChronosClient.live

}

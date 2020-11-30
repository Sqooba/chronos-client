package io.sqooba.oss.chronos

import io.sqooba.oss.chronos.Query.Result
import zio.{ Has, IO, ZIO }

/**
 * Top-level interface of the Chronos Client – your time series database view of
 * Prometheus backend.
 */
object Chronos {
  type ChronosService = Has[Chronos.Service]

  trait Service {

    /**
     * Executes the given query. Implementations may optionally perform some
     * optimisations like query deduplication or execution in parallel. Check the
     * implementation you are using for more details.
     *
     * See [[Query]] for details on how to construct queries.
     */
    def query(query: Query): IO[ChronosError, Result]
  }

  /**
   * Executes the given query. Implementations may optionally perform some
   * optimisations like query deduplication or execution in parallel. Check the
   * implementation you are using for more details.
   *
   * See [[Query]] for details on how to construct queries.
   */
  def query(query: Query): ZIO[ChronosService, ChronosError, Result] =
    ZIO.accessM(_.get.query(query))
}

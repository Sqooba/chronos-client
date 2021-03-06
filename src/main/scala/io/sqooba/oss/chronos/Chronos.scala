package io.sqooba.oss.chronos

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
    def query(query: Query): IO[ChronosError, QueryResult]
  }

  /**
   * Executes the given query. Implementations may optionally perform some
   * optimisations like query deduplication or execution in parallel. Check the
   * implementation you are using for more details.
   *
   * See [[Query]] for details on how to construct queries.
   */
  def query(query: Query): ZIO[ChronosService, ChronosError, QueryResult] =
    ZIO.accessM(_.get.query(query))
}

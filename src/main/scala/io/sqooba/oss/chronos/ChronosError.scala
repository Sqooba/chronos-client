package io.sqooba.oss.chronos

import io.sqooba.oss.promql.{ PrometheusClientError, PrometheusError, PrometheusErrorResponse }

/* Wrapper type for errors that Chronos may emit.
 */
sealed trait ChronosError extends Exception

final case class UnderlyingClientError(prom: PrometheusError) extends ChronosError {

  override def toString: String = prom match {
    case PrometheusClientError(msg) =>
      s"UnderlyingClientError($msg)"
    case PrometheusErrorResponse(errorType, error, warnings) =>
      s"UnderlyingClientError($errorType, $error, $warnings)"
  }
}

final case class IdParsingError(msg: String)      extends ChronosError
final case class TimeSeriesDataError(msg: String) extends ChronosError

/** The query is either unsupported or incorrectly formed. */
final case class InvalidQueryError(msg: String) extends ChronosError

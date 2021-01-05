package io.sqooba.oss.chronos

/**
 * Represents a [[https://prometheus.io/docs/prometheus/latest/querying/functions/
 * PromQL query function]].
 *
 * @param name of the function, as per the documentation.
 */
sealed abstract class QueryFunction(val name: String) {

  override def toString: String = name
}

object QueryFunction {

  case object AvgOverTime    extends QueryFunction("avg_over_time")
  case object MinOverTime    extends QueryFunction("min_over_time")
  case object MaxOverTime    extends QueryFunction("max_over_time")
  case object SumOverTime    extends QueryFunction("sum_over_time")
  case object CountOverTime  extends QueryFunction("count_over_time")
  case object StddevOverTime extends QueryFunction("stddev_over_time")
  case object StdvarOverTime extends QueryFunction("stdvar_over_time")

}

package io.sqooba.oss.chronos

import scala.concurrent.duration.FiniteDuration

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

  // permits you to call any promQL function that doesn't need a range-vector
  case class UserFunction(override val name: String) extends QueryFunction(name)

  abstract class AggregateOverTime(name: String) extends QueryFunction(name) {
    def range: FiniteDuration
  }
  case class UserRangeFunction(func: String, range: FiniteDuration) extends AggregateOverTime(func)
  case class AvgOverTime(range: FiniteDuration)                     extends AggregateOverTime("avg_over_time")
  case class MinOverTime(range: FiniteDuration)                     extends AggregateOverTime("min_over_time")
  case class MaxOverTime(range: FiniteDuration)                     extends AggregateOverTime("max_over_time")
  case class SumOverTime(range: FiniteDuration)                     extends AggregateOverTime("sum_over_time")
  case class CountOverTime(range: FiniteDuration)                   extends AggregateOverTime("count_over_time")
  case class StddevOverTime(range: FiniteDuration)                  extends AggregateOverTime("stddev_over_time")
  case class StdvarOverTime(range: FiniteDuration)                  extends AggregateOverTime("stdvar_over_time")

}

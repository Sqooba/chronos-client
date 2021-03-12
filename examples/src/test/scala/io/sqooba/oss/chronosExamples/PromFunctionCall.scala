package io.sqooba.oss.chronosExamples

import zio.test._
import java.time.Instant
import zio.test.Assertion._
import scala.concurrent.duration._
import io.sqooba.oss.utils.Utils._
import io.sqooba.oss.utils.ChronosRunnable
import io.sqooba.oss.timeseries.entity.TsLabel
import io.sqooba.oss.timeseries.immutable.TSEntry
import io.sqooba.oss.chronos.{Chronos, ChronosEntityId, Query, QueryFunction}
import io.sqooba.oss.timeseries.TimeSeries
import org.junit.runner.RunWith
import zio.test.junit.ZTestJUnitRunner

// scalastyle:off magic.number

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class PromFunctionCall extends ChronosRunnable {

  val spec: ChronosRunnable = suite("VictoriaMetrics Integration")(
    testM("aggregation function called on entity") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end = start.plusSeconds(5.minutes.toSeconds)

      final case class Workstation(id: Long) extends ChronosEntityId {

        override def tags: Map[String, String] =
          Map("type" -> "workstation", "id" -> id.toString)

      }
      val step = 10.seconds
      val workstation = Workstation(1)
      val tsId = workstation.buildTsId(TsLabel("cpu"))
      val avgLabel = "avg_cpu"
      val avgQuery = Query
        .fromTsId(tsId, start, end, step = Some(step))
        .function(avgLabel, QueryFunction.AvgOverTime(step))

      val insertDataPoints = insertFakePercentage(start, end, Map("__name__" -> "cpu") ++ workstation.tags, step)
      val queries = insertDataPoints <*> Chronos.query(query = avgQuery)

      for {
        (metrics, result) <- queries
      } yield assert(result.getByQueryKey(avgLabel))(
        isSome(equalTo(TimeSeries.ofOrderedEntriesSafe(metrics.timestamps.zip(metrics.values).map {
          case (ts, v) => TSEntry(ts, v, step.toMillis)
        })))
      )
    },
    testM("aggregation function called on entity with time duration") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end = start.plusSeconds(5.minutes.toSeconds)

      final case class Workstation(id: Long) extends ChronosEntityId {

        override def tags: Map[String, String] =
          Map("type" -> "workstation", "id" -> id.toString)

      }
      val step = 10.seconds
      val samplingFactor = 6 // Number of "passed step" to retrieve for each point
      val workstation = Workstation(1)
      val tsId = workstation.buildTsId(TsLabel("cpu"))
      val avgLabel = "avg_cpu"
      val avgQuery = Query
        .fromTsId(tsId, start, end, step = Some(step))
        .function(avgLabel, QueryFunction.AvgOverTime(samplingFactor * step))

      val insertDataPoints = insertFakePercentage(start, end, Map("__name__" -> "cpu") ++ workstation.tags, step)
      val queries = insertDataPoints <*> Chronos.query(query = avgQuery)
      /*
       This is a more complexe querying scenario, we force the step to 10s, so we will retrieve a datapoint for
       each 10 seconds between start and end.
       However the timeDuration is set to be 60s, so for each datapoint, we will get the AvgOverTime of the five preceding datapoint

       If we insert 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
       We will end up with the following result
       1 #  Because we don't have 5 previous points
       1+2 / 2  # We only have one datapoint before
       1+2+3 / 3 # ...
       1+2+3+4 / 4
       1+2+3+4+5 / 5
       1+2+3+4+5+6 / 6
       2+3+4+5+6+7 / 6 # We slide to get the 5 previous datapoint
       3+4+5+6+7+8 / 6 # and so on
       ...

       Writing the results of the first five "corner cases" is not readable and confusing.
       This is why in this test we skip the first 5 entries
       */

      for {
        (metrics, result) <- queries
      } yield assert(
        result.getByQueryKey(avgLabel)
      )(
        isSome(
          equalTo(
            TimeSeries.ofOrderedEntriesSafe(
              metrics.timestamps
              // The first part of the list is used to generate the "corner cases" of the average
                .zip(
                  (1 until samplingFactor).map(x => metrics.values.take(x)) ++ metrics.values.sliding(samplingFactor)
                )
                .map {
                  case (ts, v) => TSEntry(ts, v.sum / v.length, step.toMillis)
                }
            )
          )
        )
      )
    }
  )

}

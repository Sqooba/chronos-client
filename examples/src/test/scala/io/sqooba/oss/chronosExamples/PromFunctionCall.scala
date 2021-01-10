package io.sqooba.oss.chronosExamples

import zio.test._
import java.time.Instant
import zio.test.Assertion._
import scala.concurrent.duration._
import io.sqooba.oss.utils.Utils._
import io.sqooba.oss.utils.ChronosRunnable
import io.sqooba.oss.timeseries.entity.TsLabel
import io.sqooba.oss.timeseries.immutable.TSEntry
import io.sqooba.oss.chronos.{ Chronos, ChronosEntityId, Query, QueryFunction }
import io.sqooba.oss.timeseries.TimeSeries

object PromFunctionCall extends ChronosRunnable {

  val spec: ChronosRunnable = suite("VictoriaMetrics Integration")(
    testM("aggregation function called on entity") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end   = start.plusSeconds(5.minutes.toSeconds)

      final case class Workstation(id: Long) extends ChronosEntityId {

        override def tags: Map[String, String] =
          Map("type" -> "workstation", "id" -> id.toString)

      }
      val step        = 10.seconds
      val workstation = Workstation(1)
      val tsId        = workstation.buildTsId(TsLabel("cpu"))
      val avgLabel    = "avg_cpu"
      val avgQuery = Query
        .fromTsId(tsId, start, end, step = Some(step))
        .function(avgLabel, QueryFunction.AvgOverTime)

      val insertDataPoints = insertFakePercentage(start, end, Map("__name__" -> "cpu") ++ workstation.tags, step)
      val queries          = insertDataPoints <*> Chronos.query(query = avgQuery)

      for {
        (metrics, result) <- queries
      } yield assert(result.getByQueryKey(avgLabel))(
        isSome(equalTo(TimeSeries.ofOrderedEntriesSafe(metrics.timestamps.zip(metrics.values).map {
          case (ts, v) => TSEntry(ts, v, step.toMillis)
        })))
      )
    }
  )

}

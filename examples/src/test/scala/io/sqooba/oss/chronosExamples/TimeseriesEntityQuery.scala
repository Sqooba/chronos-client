package io.sqooba.oss.chronosExamples

import zio.test._
import java.time.Instant
import zio.test.Assertion._
import scala.concurrent.duration._
import io.sqooba.oss.utils.Utils._
import io.sqooba.oss.utils.ChronosRunnable
import io.sqooba.oss.chronos.{ Chronos, ChronosEntityId, Query }
import io.sqooba.oss.timeseries.entity.TsLabel

object TimeseriesEntityQuery extends ChronosRunnable {

  val spec: ChronosRunnable = suite("VictoriaMetrics Integration")(
    testM("Querying from a timeseries identifier (TsId) works") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end   = start.plusSeconds(5.minutes.toSeconds)
      val step  = 10.seconds
      val label = "cpu"

      final case class Workstation(id: Long) extends ChronosEntityId {

        override def tags: Map[String, String] =
          Map("type" -> "workstation", "id" -> id.toString)

      }
      val workstation   = Workstation(1)
      val tsId          = workstation.buildTsId(TsLabel(label))
      val queryFromTsId = Query.fromTsId(tsId, start, end, step = Some(step))

      val insertDataPoints = insertFakePercentage(start, end, Map("__name__" -> label) ++ workstation.tags, step)
      val queries          = insertDataPoints *> Chronos.query(query = queryFromTsId)

      for {
        result <- queries
      } yield assert(result.map)(isNonEmpty)
    },
    testM("Querying from a timeseries identifier (TsId) works with time duration") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end   = start.plusSeconds(5.minutes.toSeconds)
      val step  = 10.seconds
      val label = "cpu"

      final case class Workstation(id: Long) extends ChronosEntityId {

        override def tags: Map[String, String] =
          Map("type" -> "workstation", "id" -> id.toString)

      }
      val workstation   = Workstation(1)
      val tsId          = workstation.buildTsId(TsLabel(label))
      val queryFromTsId = Query.fromTsId(tsId, start, end, step = Some(step))

      val insertDataPoints = insertFakePercentage(start, end, Map("__name__" -> label) ++ workstation.tags, step)
      val queries          = insertDataPoints *> Chronos.query(query = queryFromTsId) <*> Chronos.query(query = queryFromTsId)

      for {
        (old, result) <- queries
      } yield assert(result.map)(equalTo(old.map))
    }
  )

}

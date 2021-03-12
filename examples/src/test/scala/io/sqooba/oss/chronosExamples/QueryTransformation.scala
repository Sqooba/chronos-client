package io.sqooba.oss.chronosExamples

import zio._
import zio.test._
import java.time.Instant
import zio.test.Assertion._
import scala.concurrent.duration._
import io.sqooba.oss.utils.Utils._
import io.sqooba.oss.utils.ChronosRunnable
import io.sqooba.oss.chronos.{Chronos, ChronosEntityId, Query}
import io.sqooba.oss.timeseries.entity.TsLabel
import io.sqooba.oss.timeseries.immutable.EmptyTimeSeries
import io.sqooba.oss.timeseries.TimeSeries
import org.junit.runner.RunWith
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class QueryTransformation extends ChronosRunnable {

  val spec: ChronosRunnable = suite("VictoriaMetrics Integration")(
    testM("Grouping and transforming a query should add a new timeseries") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end = start.plusSeconds(5.minutes.toSeconds)
      val label = TsLabel("cpu")
      val step = 10.seconds

      final case class Workstation(id: Long) extends ChronosEntityId {
        override def tags: Map[String, String] =
          Map("type" -> "workstation", "id" -> id.toString)
      }

      final case class Room(name: String, workstations: Seq[Workstation]) extends ChronosEntityId {
        override def tags: Map[String, String] =
          Map("type" -> "room", "name" -> name)
      }

      val office = Room("office", (1L to 10).map(Workstation.apply))

      val workStationQueries = office.workstations
        .map(_.buildTsId(label))
        .map(tsId => Query.fromTsId(tsId, start, end, step = Some(step)))

      val groupedQueries = Query.group(workStationQueries)
      val transformedQueries = groupedQueries.transform(
        office.buildTsId(label),
        start,
        end,
        step = step
      ) {
        case (ir, _) =>
          office.workstations
            .map(_.buildTsId(label))
            .map(tsid => ir.getByTsId(tsid))
            .collect { case Some(ts) => ts }
            .foldLeft(EmptyTimeSeries: TimeSeries[Double])(
              _.plus(_, strict = false)
            )
      }

      val fakeDataPoints = ZIO.foreachPar_(office.workstations)(ws => insertFakePercentage(start, end, Map("__name__" -> label.value) ++ ws.tags, step))
      val queries = fakeDataPoints *> Chronos.query(query = transformedQueries)

      for {
        result <- queries
      } yield assert(result.map)(isNonEmpty) && assert(result.getByTsId(office.buildTsId(label)))(isSome)
    }
  )

}

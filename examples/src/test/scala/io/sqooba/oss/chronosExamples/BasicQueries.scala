package io.sqooba.oss.chronosExamples

import zio._
import zio.test._
import java.time.Instant
import zio.test.Assertion._
import scala.concurrent.duration._
import io.sqooba.oss.utils.Utils._
import io.sqooba.oss.promql.RangeQuery
import io.sqooba.oss.utils.ChronosRunnable
import io.sqooba.oss.chronos.{Chronos, InvalidQueryError, Query}
import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.immutable.TSEntry
import org.junit.runner.RunWith
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class BasicQueries extends ChronosRunnable {

  val spec: ChronosRunnable = suite("VictoriaMetrics Integration")(
    testM("String query and promql query are identical") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end = start.plusSeconds(5.minutes.toSeconds)
      val step = 10.seconds
      val label = "cpu"
      val query = f"""$label{type="workstation"}"""

      val queryFromString: IO[InvalidQueryError, Query] =
        Query.fromString(
          query,
          start,
          end,
          step = Some(step)
        )
      val queryFromProm: IO[InvalidQueryError, Query] =
        Query.from(
          RangeQuery(
            query,
            start,
            end,
            step = step,
            timeout = None
          )
        )
      val insertDataPoints =
        insertFakePercentage(start, end, Map("__name__" -> label, "type" -> "workstation"), step)

      val queries = insertDataPoints *> (queryFromProm <*> queryFromString).flatMap {
              case (first, second) =>
                Chronos.query(first) <*> Chronos.query(second)
            }

      for {
        (result1, result2) <- queries
      } yield assert(result1.map)(isNonEmpty) && assert(result1)(equalTo(result2))
    },
    testM("Should retrieve the inserted points") {
      val start = Instant.parse("2020-12-12T00:00:00.000Z")
      val end = start.plusSeconds(5.minutes.toSeconds)
      val step = 10.seconds
      val label = "cpu"
      val query = f"""$label{type="workstation"}"""

      val queryFromProm: IO[InvalidQueryError, Query] =
        Query.from(
          RangeQuery(
            query,
            start,
            end,
            step = step,
            timeout = None
          )
        )
      val insertDataPoints =
        insertFakePercentage(start, end, Map("__name__" -> label, "type" -> "workstation"), step)

      val queries = insertDataPoints <*> queryFromProm.flatMap(Chronos.query)

      for {
        (metrics, result) <- queries
      } yield assert(result.getByQueryKey(label))(
        isSome(
          equalTo(
            TimeSeries.ofOrderedEntriesSafe(metrics.timestamps.zip(metrics.values).map {
              case (ts, v) =>
                TSEntry(ts, v, step.toMillis)
            })
          )
        )
      )
    }
  )

}

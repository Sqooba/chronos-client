package io.sqooba.oss.chronos

import zio.test.Assertion._
import io.sqooba.oss.chronos.TestUtils._
import io.sqooba.oss.chronos.Query._
import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.immutable.EmptyTimeSeries
import zio._
import zio.test._
import sttp.client.asynchttpclient.zio.stubbing._
import sttp.client.Response
import sttp.model.StatusCode

import scala.io.Source
import scala.concurrent.duration._
import sttp.client.StringBody
import sttp.client.RequestT
import java.net.URLDecoder
import io.sqooba.oss.timeseries.immutable.TSEntry

object ChronosSpec extends DefaultRunnableSpec {

  private val label1 = """Measure_10m_Avg{t_id="122"}"""
  private val label2 = """Measure_10m_Avg{t_id="117"}"""
  private val label3 = """Measure_10m_Avg{t_id="45"}"""

  private val interval10mMs = 10.minutes.toMillis

  val simpleMetricResult =
    TimeSeries.ofOrderedEntriesSafe(
      Seq(
        TSEntry(1598443215000L, 28000.0, interval10mMs),
        TSEntry(1598443215000L + interval10mMs, 50.0, interval10mMs),
        TSEntry(1598443215000L + 2 * interval10mMs, 29000.0, interval10mMs)
      ),
      true
    )

  def debug(content: String): List[(String, String)] =
    URLDecoder.decode(content, TKT-XXX).split("&").toList.map { param =>
      val kv = param.split("=")
      (kv.head, kv.tail.mkString("="))
    }

  def createResponse(content: String): QueryKey =
    Runtime.default.unsafeRun(
      QueryKey
        .fromPromQuery(content)
    )

  val spec = suite("Chronos – end-to-end")(
    testM("Timeseries should be continuous and compressed")(
      assertM(
        for {
          _ <- whenAnyRequest.thenRespond {
                 Response.ok(TestUtils.responseFor("Measure_10m_Avg", Map("t_id" -> "122")))
               }
          query  <- testQuery(label1)
          result <- Chronos.query(query)
        } yield result
      )(
        hasField("map", (r: Result) => r.map(QueryKey("Measure_10m_Avg", Map("t_id" -> "122"))).isCompressed, isTrue) &&
          hasField(
            "map",
            (r: Result) => r.map(QueryKey("Measure_10m_Avg", Map("t_id" -> "122"))).isDomainContinuous,
            isTrue
          )
      )
    ),
    testM("combining queries") {
      assertM(
        for {
          _ <- whenRequestMatchesPartial {
                 case RequestT(_, _, StringBody(content, _, _), _, _, _, _) =>
                   val key = createResponse(debug(content).find(_._1 == "query").get._2)
                   Response.ok(
                     TestUtils.responseFor(key.key, key.tags)
                   )
               }
          query  <- testQuery(label1) + testQuery(label2) + testQuery(label3)
          result <- Chronos.query(query)
        } yield result
      )(
        equalTo(
          Result(
            Map(
              QueryKey("Measure_10m_Avg", Map("t_id" -> "122")) -> simpleMetricResult,
              QueryKey("Measure_10m_Avg", Map("t_id" -> "117")) -> simpleMetricResult,
              QueryKey("Measure_10m_Avg", Map("t_id" -> "45")) -> simpleMetricResult
            )
          )
        )
      )
    },
    testM("deduplicate queries") {
      val query = (
        (testQuery(label1) + testQuery(label2)) +
          (testQuery(label1) + testQuery(label2))
      ) + (
        (testQuery(label3) + testQuery(label2)) +
          (testQuery(label1) + testQuery(label2))
      )

      val validResponse = Response.ok(
        TestUtils.loadFile("responses/singleMetric.json")
      )

      // fail after three responses to make sure that at most 3 are performed.
      val failResponse = Response("", StatusCode.TooManyRequests)

      assertM(
        for {
          _ <- whenAnyRequest.thenRespondCyclicResponses(
                 Seq(
                   Response.ok(TestUtils.responseFor("Measure_10m_Avg", Map("t_id" -> "122"))),
                   Response.ok(TestUtils.responseFor("Measure_10m_Avg", Map("t_id" -> "117"))),
                   Response.ok(TestUtils.responseFor("Measure_10m_Avg", Map("t_id" -> "45")))
                 ) ++ Seq.fill(1000)(failResponse): _*
               )

          result <- query >>= Chronos.query
        } yield result
      )(
        equalTo(
          Result(
            Map(
              QueryKey("Measure_10m_Avg", Map("t_id" -> "122")) -> simpleMetricResult,
              QueryKey("Measure_10m_Avg", Map("t_id" -> "117")) -> simpleMetricResult,
              QueryKey("Measure_10m_Avg", Map("t_id" -> "45")) -> simpleMetricResult
            )
          )
        )
      )
    },
    testM("working with time series") {
      val query = (
        (testQuery(label1) + testQuery(label2)) +
          (testQuery(label1) + testQuery(label2))
      ) + (
        (testQuery(label3) + testQuery(label2)) +
          (testQuery(label1) + testQuery(label2))
      )

      assertM((for {
        _ <- whenAnyRequest.thenRespond(
               Source
                 .fromResource("responses/singleMetric.json")
                 .mkString
             )

        result     <- query >>= Chronos.query
        timeseries <- ZIO.fromOption(result.getByQueryKey(label1))
      } yield timeseries).run)(succeeds(anything))
    },
    testM("advanced mapping") {
      val aggregationLabel = """Measure_SUM"""

      val query = ((testQuery(label1) + testQuery(label2)) + testQuery(label3)) + (
        (testQuery(label3) + testQuery(label2)) +
          (testQuery(label1) + testQuery(label2))
      )

      val transformedQuery = query
        .transform(aggregationLabel, start, end, 1.minute.toSeconds.toInt) {
          case (intermediate, qid) =>
            val sum = intermediate.values
              .flatMap(_.map)
              .filter(_._1.key.matches("Measure_10m_Avg"))
              .map(_._2)
              .foldLeft(EmptyTimeSeries: TimeSeries[Double])(
                // strict = false because the base time series is an EmptyTimeSeries
                _.plus(_, strict = false)
              )

            intermediate + (qid -> Result(Map(qid.key -> sum)))
        }

      assertM(
        (for {
          _ <- whenAnyRequest.thenRespond(
                 Source
                   .fromResource("responses/multipleMetrics.json")
                   .mkString
               )
          result <- transformedQuery >>= Chronos.query
        } yield result).run
      )(succeeds(anything))
    }
  ).provideLayer(chronosClient)
}

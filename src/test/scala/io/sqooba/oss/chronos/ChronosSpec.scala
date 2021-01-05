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
import java.time.Instant

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
        hasField(
          "map",
          (r: QueryResult) => r.map(QueryKey("Measure_10m_Avg", Map("t_id" -> "122"))).isCompressed,
          isTrue
        ) &&
          hasField(
            "map",
            (r: QueryResult) => r.map(QueryKey("Measure_10m_Avg", Map("t_id" -> "122"))).isDomainContinuous,
            isTrue
          )
      )
    ),
    testM("FAIL: multiple ranges") {
      /*
        Here we have an issue, as QueryKey do not store the starting or ending time of a query, we can't distinguish to queries for the same
        label if they have a different start/end/step.
        The expected map should have both entry with the same key: this is not possible
       */
      val query =
        testQuery(label1) + testQuery(label1).map(query =>
          query.copy(id = query.id.copy(start = start.minusSeconds(6000)))
        )

      assertM(for {
        _ <- whenRequestMatchesPartial {
               case RequestT(_, _, StringBody(content, _, _), _, _, _, _) =>
                 val start = Instant.parse(debug(content).find(_._1 == "start").get._2)
                 // Create a response with a single datapoint set to the beginning of the query
                 Response.ok(f"""
                      {
                        "status": "success",
                        "data": {
                          "resultType": "matrix",
                          "result": [
                            {
                              "metric": {
                                "__name__": "Measure_10m_Avg",
                                "t_id": "122"
                              },
                              "values": [
                                [
                                  ${start.toEpochMilli() / 1000},
                                  "28000"
                                ]
                              ]
                            }
                          ]
                        }
                      }

                 """)
             }
        result <- query >>= Chronos.query
      } yield result.map.toList)(
        equalTo(
          List[(QueryKey, TimeSeries[Double])](
            (
              QueryKey("Measure_10m_Avg", Map("t_id" -> "122")),
              TSEntry(
                start.minusSeconds(6000).toEpochMilli,
                28000.0,
                interval10mMs
              )
            ),
            (QueryKey("Measure_10m_Avg", Map("t_id" -> "122")), TSEntry(start.toEpochMilli, 28000.0, interval10mMs))
          )
        ).negate
      )
    },
    testM("combining queries") {
      assertM(
        for {
          _ <- whenRequestMatchesPartial {
                 case RequestT(_, _, StringBody(content, _, _), _, _, _, _) =>
                   val key = createResponse(debug(content).find(_._1 == "query").get._2)
                   Response.ok(
                     TestUtils.responseFor(key.name, key.tags)
                   )
               }
          query  <- testQuery(label1) + testQuery(label2) + testQuery(label3)
          result <- Chronos.query(query)
        } yield result
      )(
        equalTo(
          QueryResult(
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
          QueryResult(
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
    testM("FAIL: multiple ranges in transform") {
      /*
          Here we have the same issue with a single query beeing executed with different start/end/step parameters
          As the result are flattened when given as input to the transform function, we can't distinguish them and the
          created "SELECTED" timeseries might contain the result from one or another
       */
      val aggregationLabel = """SELECTED"""
      val query =
        testQuery(label1) + testQuery(label1).map(query =>
          query.copy(id = query.id.copy(start = start.minusSeconds(6000)))
        )

      val transformedQuery = query
        .transform(aggregationLabel, start, end, 1.minute) {
          case (intermediate, _) =>
            intermediate
              .getByQueryKey(label1)
              .get
        }

      assertM(for {
        _ <- whenRequestMatchesPartial {
               case RequestT(_, _, StringBody(content, _, _), _, _, _, _) =>
                 val start = Instant.parse(debug(content).find(_._1 == "start").get._2)
                 // Create a response with a single datapoint set to the beginning of the query
                 Response.ok(f"""
                      {
                        "status": "success",
                        "data": {
                          "resultType": "matrix",
                          "result": [
                            {
                              "metric": {
                                "__name__": "Measure_10m_Avg",
                                "t_id": "122"
                              },
                              "values": [
                                [
                                  ${start.toEpochMilli() / 1000},
                                  "28000"
                                ]
                              ]
                            }
                          ]
                        }
                      }

                 """)
             }
        result <- transformedQuery >>= Chronos.query
      } yield result.map.toList)(
        equalTo(
          List[(QueryKey, TimeSeries[Double])](
            (QueryKey("Measure_10m_Avg", Map("t_id" -> "122")), TSEntry(start.toEpochMilli(), 28000, interval10mMs)),
            (
              QueryKey("Measure_10m_Avg", Map("t_id" -> "122")),
              TSEntry(start.minusSeconds(6000).toEpochMilli(), 28000, interval10mMs)
            ),
            (QueryKey("SELECTED", Map("t_id" -> "122")), TSEntry(start.toEpochMilli(), 28000, interval10mMs))
          )
        ).negate
      )
    },
    testM("advanced mapping") {
      val aggregationLabel = """Measure_SUM"""

      val query = testQuery(label1) + testQuery(label2) + testQuery(label3)

      val transformedQuery = query
        .transform(aggregationLabel, start, end, 1.minute) {
          case (intermediate, _) =>
            List(label1, label2, label3)
              .map(label => intermediate.getByQueryKey(label))
              .collect {
                case Some(ts) => ts
              }
              // strict = false because the base time series is an EmptyTimeSeries
              .foldLeft(EmptyTimeSeries: TimeSeries[Double])(_.plus(_, strict = false))
        }

      assertM(
        for {
          _ <- whenRequestMatchesPartial {
                 case RequestT(_, _, StringBody(content, _, _), _, _, _, _) =>
                   val key = createResponse(debug(content).find(_._1 == "query").get._2)
                   Response.ok(TestUtils.responseFor(key.name, key.tags))
               }
          result <- transformedQuery >>= Chronos.query
        } yield result
      )(
        equalTo(
          QueryResult(
            Map(
              QueryKey("Measure_10m_Avg", Map("t_id" -> "122")) -> simpleMetricResult,
              QueryKey("Measure_10m_Avg", Map("t_id" -> "117")) -> simpleMetricResult,
              QueryKey("Measure_10m_Avg", Map("t_id" -> "45")) -> simpleMetricResult,
              QueryKey("Measure_SUM", Map[String, String]()) -> simpleMetricResult
                .plus(simpleMetricResult)
                .plus(simpleMetricResult)
            )
          )
        )
      )
    }
  ).provideLayer(chronosClient)
}

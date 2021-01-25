package io.sqooba.oss.chronos

import zio.test._
import zio.test.Assertion._

import scala.concurrent.duration._
import io.sqooba.oss.promql.MatrixResponseData
import io.sqooba.oss.promql.metrics.MatrixMetric
import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.immutable.{ EmptyTimeSeries, TSEntry }
import io.sqooba.oss.chronos.TestUtils.chronosClient
import sttp.client.asynchttpclient.zio.stubbing._

import scala.io.Source
import zio.ZIO

object ChronosClientSpec extends DefaultRunnableSpec {

  val spec = suite("ChronosClient")(
    suite("query")(
      testM("should return empty on an empty query") {
        assertM(
          Chronos.query(Query.Empty)
        )(
          equalTo(QueryResult(Map()))
        )
      },
      testM("Should correctly convert an empty response") {
        val scenario = for {
          response <- ZIO.succeed(
                        Source
                          .fromResource("responses/emptyResponse.json")
                          .mkString
                      )
          _     <- whenAnyRequest.thenRespond(response)
          query <- TestUtils.testQuery("label")
          resp  <- Chronos.query(query)
        } yield resp
        assertM(scenario)(equalTo(QueryResult(Map())))
      },
      testM("Should correctly convert response for a single metric") {
        val scenario = for {
          response <- ZIO.succeed(
                        Source
                          .fromResource("responses/singleMetric.json")
                          .mkString
                      )
          _     <- whenAnyRequest.thenRespond(response)
          query <- TestUtils.testQuery("""Measure_10m_Avg{t_id="122"}""")
          resp  <- Chronos.query(query)
        } yield resp
        assertM(scenario)(
          equalTo(
            QueryResult(
              Map(
                QueryKey("Measure_10m_Avg", Map("t_id" -> "122")) ->
                  TimeSeries(
                    Seq(
                      TSEntry((1598443215000L, 28000.0, 15000)),
                      TSEntry((1598443230000L, 50.0, 15000)),
                      TSEntry((1598443245000L, 29000.0, 10.minutes.toMillis))
                    )
                  )
              )
            )
          )
        )
      },
      testM("Should correctly convert response for multiple metrics") {
        val scenario = for {
          response <- ZIO.succeed(
                        Source
                          .fromResource("responses/multipleMetrics.json")
                          .mkString
                      )
          _     <- whenAnyRequest.thenRespond(response)
          query <- TestUtils.testQuery("Measure_10m_Avg")
          resp  <- Chronos.query(query)
        } yield resp
        assertM(scenario)(
          equalTo(
            QueryResult(
              Map(
                QueryKey("Measure_10m_Avg", Map("t_id" -> "122")) ->
                  TimeSeries(
                    Seq(
                      TSEntry((1598443215000L, 28000.0, 15000)),
                      TSEntry((1598443230000L, 12000.0, 15000)),
                      TSEntry((1598443245000L, -3.0, 10.minutes.toMillis))
                    )
                  ),
                QueryKey("Measure_10m_Avg", Map("t_id" -> "117")) ->
                  TimeSeries(
                    Seq(
                      TSEntry((1598443200000L, 27000.0, 15000)),
                      TSEntry((1598443215000L, 1.0, 10.minutes.toMillis))
                    )
                  )
              )
            )
          )
        )
      }
    ),
    suite("timeseries conversion")(
      suite("matrix response data to ts")(
        testM("Convert empty data") {
          val data = MatrixResponseData(List(MatrixMetric(Map(), List())))
          assertM(
            for {
              query <- TestUtils.testQuery("LABEL", 10.minutes)
              ts    <- ChronosClient.toTs(query.id, data)
            } yield ts
          )(
            equalTo(
              QueryResult(Map(QueryKey("LABEL", Map()) -> EmptyTimeSeries))
            )
          )
        },
        testM("Convert a single data result") {
          val sampling = 1.minute
          val data = MatrixResponseData(
            List(
              MatrixMetric(
                Map("__name__" -> "LABEL"),
                List(
                  (TestUtils.start, "1"),
                  (TestUtils.start.plusSeconds(sampling.toSeconds), "2")
                )
              )
            )
          )
          val result = QueryResult(
            Map(
              QueryKey("LABEL", Map()) -> TimeSeries(
                Seq(
                  TSEntry(TestUtils.start.toEpochMilli, 1.0, sampling.toMillis),
                  TSEntry(TestUtils.start.plusSeconds(sampling.toSeconds).toEpochMilli, 2.0, sampling.toMillis)
                )
              )
            )
          )
          assertM(
            for {
              query <- TestUtils.testQuery("LABEL", sampling)
              ts    <- ChronosClient.toTs(query.id, data)
            } yield ts
          )(equalTo(result))
        },
        testM("Correctly extract the label and overwrite the tags") {
          val sampling = 1.minute
          val data = MatrixResponseData(
            List(
              MatrixMetric(
                Map("__name__" -> "label", "returnedTag" -> "returnedValue"),
                List(
                  (TestUtils.start, "1"),
                  (TestUtils.start.plusSeconds(sampling.toSeconds), "2")
                )
              )
            )
          )
          val result = QueryResult(
            Map(
              QueryKey("label", Map("returnedTag" -> "returnedValue")) ->
                TimeSeries(
                  Seq(
                    TSEntry(TestUtils.start.toEpochMilli, 1.0, sampling.toMillis),
                    TSEntry(TestUtils.start.plusSeconds(sampling.toSeconds).toEpochMilli, 2.0, sampling.toMillis)
                  )
                )
            )
          )
          assertM(
            for {
              query <- TestUtils.testQuery("""label{tag="value"}""", sampling)
              ts    <- ChronosClient.toTs(query.id, data)
            } yield ts
          )(equalTo(result))
        },
        testM("Raise an error if unsupported query") {
          val sampling = 1.minute
          val data     = MatrixResponseData(List(MatrixMetric(Map(), List())))

          assertM((for {
            query <- TestUtils.testQuery("""sum(label{tag="value"})""", sampling)
            ts    <- ChronosClient.toTs(query.id, data)
          } yield ts).run)(
            fails(isSubtype[ChronosError](anything))
          )
        },
        testM("Correctly convert multiple results from a range query") {
          val sampling = 1.minute
          val data = MatrixResponseData(
            List(
              MatrixMetric(
                Map("__name__" -> "label", "tag" -> "value1"),
                List(
                  (TestUtils.start, "1"),
                  (TestUtils.start.plusSeconds(sampling.toSeconds), "2")
                )
              ),
              MatrixMetric(
                Map("__name__" -> "label", "tag" -> "value2"),
                List(
                  (TestUtils.start, "3"),
                  (TestUtils.start.plusSeconds(sampling.toSeconds), "4")
                )
              )
            )
          )

          val result = QueryResult(
            Map(
              QueryKey("label", Map("tag" -> "value1")) ->
                TimeSeries(
                  Seq(
                    TSEntry(TestUtils.start.toEpochMilli, 1.0, sampling.toMillis),
                    TSEntry(TestUtils.start.plusSeconds(sampling.toSeconds).toEpochMilli, 2.0, sampling.toMillis)
                  )
                ),
              QueryKey("label", Map("tag" -> "value2")) ->
                TimeSeries(
                  Seq(
                    TSEntry(TestUtils.start.toEpochMilli, 3.0, sampling.toMillis),
                    TSEntry(TestUtils.start.plusSeconds(sampling.toSeconds).toEpochMilli, 4.0, sampling.toMillis)
                  )
                )
            )
          )
          assertM(
            for {
              query <- TestUtils.testQuery("""label{tag=~"value1|value2"}""", sampling)
              ts    <- ChronosClient.toTs(query.id, data)
            } yield ts
          )(equalTo(result))
        }
      )
    )
  ).provideLayer(chronosClient)
}

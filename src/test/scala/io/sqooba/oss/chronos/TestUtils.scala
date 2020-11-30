package io.sqooba.oss.chronos

import java.time.Instant

import io.sqooba.oss.chronos.Chronos.ChronosService
import io.sqooba.oss.promql.{ PrometheusClient, PrometheusClientConfig }
import sttp.client.asynchttpclient.zio.{ AsyncHttpClientZioBackend, SttpClientStubbing }
import zio.{ IO, ULayer, ZLayer }
import scala.io.Source

object TestUtils {

  // scalastyle:off magic.number
  private val config =
    PrometheusClientConfig(
      "test",
      port = 12,
      maxPointsPerTimeseries = 1000,
      retryNumber = 1,
      parallelRequests = 5
    )

  def chronosClient: ULayer[SttpClientStubbing with ChronosService] =
    (
      (ZLayer.succeed(config) ++ AsyncHttpClientZioBackend.stubLayer) >+>
        PrometheusClient.live >+> ChronosClient.live()
    ).orDie

  val start: Instant = Instant.parse("2020-10-12T14:00:00Z")
  val end: Instant   = Instant.parse("2020-10-12T15:23:00Z")

  def testQuery(label: String): IO[InvalidQueryError, Query.Range] =
    Query.fromString(
      label,
      start,
      end
    )

  def testQuery(label: String, sampling: Int): IO[InvalidQueryError, Query.Range] =
    Query.fromString(
      label,
      start,
      end,
      Some(sampling),
      None
    )

  def loadFile(filePath: String): String =
    Source
      .fromResource(filePath)
      .mkString

  def responseFor(name: String, tags: Map[String, String] = Map()): String = {
    val formattedTags = (tags + ("__name__" -> name)).map { case (k, v) => f""""$k": "$v"""" }.mkString(",")
    f"""
      {
        "status": "success",
        "data": {
          "resultType": "matrix",
          "result": [
            {
              "metric": {
                $formattedTags
              },
              "values": [
                [
                  1598443215,
                  "28000"
                ],
                [
                  1598443815,
                  "50"
                ],
                [
                  1598444415,
                  "29000"
                ]
              ]
            }
          ]
        }
      }
  """

  }
}
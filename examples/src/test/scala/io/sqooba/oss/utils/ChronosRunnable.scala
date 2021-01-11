package io.sqooba.oss.utils

import zio._
import zio.test._
import zio.duration._
import ChronosRunnable._
import zio.blocking.Blocking
import zio.test.environment._
import io.sqooba.oss.chronos.Chronos._
import io.sqooba.oss.chronos.ChronosClient
import io.sqooba.oss.promql.PrometheusClient
import io.sqooba.oss.promql.PrometheusService._
import io.sqooba.oss.promql.PrometheusClientConfig
import com.dimafeng.testcontainers.GenericContainer
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend

object ChronosRunnable {
  type ChronosEnv = TestEnvironment with PrometheusService with ChronosService
}

/**
 *  Extends ZIO-test default runner to automatically provide a VictoriaMetric instance to tests
 *  The components of the layer provided by this class can be used directly in the tests extending ChronosRunnable.
 */
abstract class ChronosRunnable extends RunnableSpec[ChronosEnv, Any] {

  type ChronosRunnable = ZSpec[ChronosEnv, Any]

  override def aspects: List[TestAspect[Nothing, ChronosEnv, Nothing, Any]] =
    List(TestAspect.timeout(60.seconds))

  override def runner: TestRunner[ChronosEnv, Any] =
    TestRunner(TestExecutor.default(chronosLayer))

  private def promConfigFromContainer = ZLayer.fromService[GenericContainer, PrometheusClientConfig] { container =>
    // scalastyle:off magic.number
    PrometheusClientConfig(
      container.container.getContainerIpAddress(),
      container.container.getFirstMappedPort(),
      maxPointsPerTimeseries = 30000,
      retryNumber = 3,
      parallelRequests = 3
    )
    // scalastyle:on magic.number
  }

  /**
   * Create a test environment by spawning a VictoriaMetrics container, building a client configuration
   * as well as a ChronosClient to be used by the tests
   */
  val chronosLayer: ULayer[ChronosEnv] = {
    val victoriaMetrics = Blocking.live >>> Containers.victoriaMetrics()
    val promConfig      = victoriaMetrics >>> promConfigFromContainer
    val promClient      = (promConfig ++ AsyncHttpClientZioBackend.layer()) >>> PrometheusClient.live
    testEnvironment ++ promClient >+> ChronosClient.live
  }.orDie

}

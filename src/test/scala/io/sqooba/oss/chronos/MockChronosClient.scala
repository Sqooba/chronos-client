package io.sqooba.oss.chronos

import io.sqooba.oss.chronos.Chronos.ChronosService
import io.sqooba.oss.chronos.Query._
import io.sqooba.oss.timeseries.immutable.TSEntry
import zio.{ IO, ULayer, ZLayer }
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment

import java.time.Instant
import org.junit.runner.RunWith
import zio.test.junit.ZTestJUnitRunner

/**
 * A mock implementation of a ChronosService/Client.
 * See [[io.sqooba.oss.chronos.MockChronosClient.apply*]]
 */
object MockChronosClient {

  /**
   * Create a mock implementation of a ChronosService/Client that has all the results
   * it can return handed to it in a map. This is useful for testing large query trees in
   * your application, just hand in all the queries that should be returned by the client for
   * their labels.
   *
   * @param mockedResult a map from labels to results that the mock can return
   * @return a layer of a ChronosService
   */
  def apply(mockedResult: QueryResult): ULayer[ChronosService] = ZLayer.succeed(
    new Chronos.Service {

      override def query(query: Query): IO[ChronosError, QueryResult] = {

        def loop(acc: QueryResult, query: Query): IO[ChronosError, QueryResult] = query match {
          case Transform(id, underlying, f) =>
            loop(acc, underlying).map { res =>
              // Check if transform result is already in accumulator, otherwise calculate it.
              val (k, ts) = acc.map.find(_._1 == id.key).getOrElse {
                id.key -> f(res, id)
              }
              res ++ QueryResult(Map(k -> ts))
            }

          case q: ExecutableQuery =>
            IO.succeed(QueryResult(acc.map.filter(_._1 == q.id.key)))

          case Group(queries) =>
            queries.map(loop(acc, _)).foldLeft(IO.succeed(QueryResult(Map()))) {
              // getting rid of the ChronosError with 'orDie'
              case (accIO, resIO) => for { a <- accIO; res <- resIO.orDie } yield a ++ res
            }

          case Empty => IO.succeed(QueryResult(Map()))
        }

        loop(mockedResult, query)
      }
    }
  )
}

// scalastyle:off magic.number
// scalastyle:off multiple.string.literals

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class MockChronosClientSpec extends DefaultRunnableSpec {
  private val start = Instant.now().minusSeconds(1000)
  private val end   = Instant.now()

  private val key      = QueryKey("label", Map())
  private val otherKey = QueryKey("other_label", Map())

  private val mockedResult = QueryResult(
    Map(
      key      -> TSEntry(1, 2, 3),
      otherKey -> TSEntry(4, 5, 6)
    )
  )

  val spec: ZSpec[TestEnvironment, Any] = suite("MockChronoClient")(
    testM("Empty Query") {
      assertM(
        Chronos
          .query(Query.Empty)
          .provideLayer(MockChronosClient(mockedResult))
      )(
        equalTo(QueryResult(Map()))
      )
    },
    testM("Range Query") {
      assertM(
        (Query.fromString("label", start, end) >>= Chronos.query)
          .provideLayer(MockChronosClient(mockedResult))
      )(
        equalTo(
          QueryResult(Map(key -> TSEntry(1, 2, 3)))
        )
      )
    },
    testM("Group Query with two simple range queries") {
      assertM(
        (
          Query.fromString("label", start, end) +
            Query.fromString("other_label", start, end) >>= Chronos.query
        ).provideLayer(MockChronosClient(mockedResult))
      )(
        equalTo(mockedResult)
      )
    },
    testM("Transform query with results already computed") {
      val mr = mockedResult ++ QueryResult(
        Map(
          QueryKey("transform_label", Map()) -> TSEntry(7, 8, 9)
        )
      )
      assertM(
        (
          Query
            .fromString("other_label", start, end)
            .transform("transform_label")((_, _) => TSEntry(7, 8, 9)) >>= Chronos.query
        ).provideLayer(MockChronosClient(mr))
      )(
        equalTo(
          QueryResult(
            Map(
              QueryKey("transform_label", Map()) -> TSEntry(7, 8, 9),
              otherKey                           -> TSEntry(4, 5, 6)
            )
          )
        )
      )
    },
    testM("Transform query with results not computed") {
      assertM(
        (
          Query
            .fromString("other_label", start, end)
            .transform("transform_label")((_, _) => TSEntry(7, 8, 9)) >>= Chronos.query
        ).provideLayer(MockChronosClient(mockedResult))
      )(
        equalTo(
          QueryResult(
            Map(
              QueryKey("transform_label", Map()) -> TSEntry(7, 8, 9),
              otherKey                           -> TSEntry(4, 5, 6)
            )
          )
        )
      )
    },
    testM("Group with transform query with results not computed") {
      val baseQuery = Query.fromString("other_label", start, end)
      assertM(
        (
          baseQuery.transform("transform_label")((_, _) => TSEntry(7, 8, 9)) +
            baseQuery >>= Chronos.query
        ).provideLayer(MockChronosClient(mockedResult))
      )(
        equalTo(
          QueryResult(
            Map(
              otherKey                           -> TSEntry(4, 5, 6),
              QueryKey("transform_label", Map()) -> TSEntry(7, 8, 9)
            )
          )
        )
      )
    }
  )
}

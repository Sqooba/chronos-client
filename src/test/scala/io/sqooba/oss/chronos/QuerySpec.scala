package io.sqooba.oss.chronos

import zio.test._
import zio.test.Assertion._
import TestUtils._
import Query._
import io.sqooba.oss.timeseries.entity.{ TsId, TsLabel }
import io.sqooba.oss.timeseries.immutable.TSEntry

import scala.concurrent.duration._

object QuerySpec extends DefaultRunnableSpec {

  case class Id(id: Int) extends ChronosEntityId {
    def tags: Map[String, String] = Map("id" -> id.toString)
  }

  val spec = suite("QuerySpec")(
    suite("And combinator")(
      test("+ should link two queries together") {
        val query = Empty + Empty
        assert(query)(equalTo(Group.of(Empty, Empty)))
      },
      test("from must combine a list of query") {
        val query = group(List(Empty, Empty, Empty))
        assert(query)(equalTo(Group.of(Empty, Empty, Empty)))
      },
      testM("+ shoud be left associative") {
        val first  = testQuery("A")
        val second = testQuery("B")
        val third  = testQuery("C")
        val query  = first + second + third

        assertM(query)(
          equalTo(
            Group.of(
              Group.of(
                Range(Qid(QueryKey("A", Map()), start, end, 60.seconds), None),
                Range(Qid(QueryKey("B", Map()), start, end, 60.seconds), None)
              ),
              Range(Qid(QueryKey("C", Map()), start, end, 60.seconds), None)
            )
          )
        )
      }
    ),
    suite("Groups")(
      test("Should only be built for two or more queries") {
        assert(Query.apply())(equalTo(Empty)) &&
        assert(Query.apply(Empty))(equalTo(Empty)) &&
        assert(Query.apply(Empty, Empty))(equalTo(Group(Seq(Empty, Empty))))
      }
    ),
    suite("Result")(
      test("should be retrieved for matching tsid with a subset of tags") {
        val tsId = TsId(Id(123), TsLabel("label"))
        val ts   = TSEntry(1, 1.23, 1)
        assert(
          Query
            .Result(
              Map(
                QueryKey("label", Map("id" -> "123", "additionalTag" -> "returnedByBackend")) -> ts
              )
            )
            .getByTsId(tsId)
        )(equalTo(Some(ts)))
      },
      test("should be retrieved for matching key with a subset of tags") {
        val ts = TSEntry(1, 1.23, 1)
        assert(
          Query
            .Result(
              Map(
                QueryKey("label", Map("id" -> "123", "additionalTag" -> "returnedByBackend")) -> ts
              )
            )
            .getByQueryKey("""label{id="123"}""")
        )(equalTo(Some(ts)))
      }
    )
  )
}

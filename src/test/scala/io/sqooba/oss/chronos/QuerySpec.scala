package io.sqooba.oss.chronos

import zio.test._
import zio.test.Assertion._
import TestUtils._
import Query._
import scala.concurrent.duration._

object QuerySpec extends DefaultRunnableSpec {
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
    )
  )
}

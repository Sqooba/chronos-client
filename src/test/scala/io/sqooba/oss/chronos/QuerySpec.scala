package io.sqooba.oss.chronos

import zio.test._
import zio.test.Assertion._
import TestUtils._
import Query._
import io.sqooba.oss.timeseries.entity.{ TsId, TsLabel }
import io.sqooba.oss.timeseries.immutable.TSEntry

import java.time.Instant
import scala.concurrent.duration._
import io.sqooba.oss.timeseries.TimeSeries

import scala.collection.immutable.HashSet
import org.junit.runner.RunWith

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class QuerySpec extends DefaultRunnableSpec {

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
    suite("Query Functions")(
      test("should be equals") {
        val start = Instant.now().minusSeconds(1000)
        val end   = Instant.now()
        assert(
          Query
            .fromTsId(
              TsId(TestId(1234), TsLabel("complicate_label_123A")),
              start,
              end
            )
            .function("new_label", QueryFunction.AvgOverTime(10.minutes))
        )(
          equalTo(
            Query
              .fromTsId(
                TsId(TestId(1234), TsLabel("complicate_label_123A")),
                start,
                end
              )
              .function("new_label", QueryFunction.AvgOverTime(10.minutes))
          )
        )
      },
      test("correct average query string") {
        assert(
          Query
            .fromTsId(
              TsId(TestId(1234), TsLabel("complicate_label_123A")),
              Instant.now().minusSeconds(1000),
              Instant.now()
            )
            .function("new_label", QueryFunction.AvgOverTime(10.minutes))
            .toPromQl
            .query
        )(equalTo("""avg_over_time(complicate_label_123A{id="1234"}[600s])"""))
      },
      testM("correct average query string with labels") {
        for {
          query <- Query
                     .fromString(
                       """ABCD_Dir_degrees{tag="abc", tag2="def"}""",
                       Instant.now().minusSeconds(1000),
                       Instant.now(),
                       step = Some(5.minutes)
                     )
                     .function("new_label", QueryFunction.StddevOverTime(5.minutes))

        } yield assert(
          query.toPromQl.query
        )(equalTo("""stddev_over_time(ABCD_Dir_degrees{tag="abc",tag2="def"}[300s])"""))
      },
      test("correctly combines with other queries") {
        val from = Instant.now().minusSeconds(1000)
        val to   = Instant.now()

        val f: TransformFunction = (_, _) => TSEntry(98, 76.54, 321)
        assert(
          Query
            .fromTsId(
              TsId(TestId(1234), TsLabel("complicate_label_123A")),
              from,
              to,
              step = Some(7.minutes)
            )
            .function(
              TsId(TestId(99), TsLabel("intermediate_label")),
              QueryFunction.MaxOverTime(7.minutes)
            )
            .transform(
              TsId(TestId(78), TsLabel("new_label_for_result"))
            )(f)
        )(
          equalTo(
            Query.Transform(
              Qid(
                QueryKey("new_label_for_result", Map("id" -> "78")),
                from,
                to,
                7.minutes
              ),
              Query.Function(
                Qid(
                  QueryKey("intermediate_label", Map("id" -> "99")),
                  from,
                  to,
                  7.minutes
                ),
                Query.Range(
                  Qid(
                    QueryKey("complicate_label_123A", Map("id" -> "1234")),
                    from,
                    to,
                    7.minutes
                  ),
                  None
                ),
                QueryFunction.MaxOverTime(7.minutes)
              ),
              f
            )
          )
        )
      }
    ),
    suite("Query transform")(
      //  This test ensures that the TransformFunction is not taken into account when
      //  comparing two Transform objects. There is no way to test if the lambdas are
      //  equivalent and make the `equals` of the case class useless in most cases.
      test("should be equal") {
        val start = Instant.now().minusSeconds(1000)
        val end   = Instant.now()
        assert(
          Query
            .fromTsId(
              TsId(TestId(1234), TsLabel("complicate_label_123A")),
              start,
              end
            )
            .transform("test", start, end) { case (_, _) => TimeSeries.ofOrderedEntriesSafe(List()) }
        )(
          equalTo(
            Query
              .fromTsId(
                TsId(TestId(1234), TsLabel("complicate_label_123A")),
                start,
                end
              )
              .transform("test", start, end) {
                case (_, _) => TimeSeries.ofOrderedEntriesSafe(List(TSEntry(start.toEpochMilli(), 1.0, 1000)))
              }
          )
        )
      },
      test("used with a HashSet") {
        val start = Instant.now().minusSeconds(1000)
        val end   = Instant.now()

        assert(
          HashSet(
            (1 to 10).map(i =>
              Query
                .fromTsId(TsId(TestId(1234), TsLabel("label")), start, end)
                .transform("new_label")((_, _) => TSEntry(1, i.toDouble, 1))
            )
          )
        )(
          equalTo(
            HashSet(
              (1 to 10).map(i =>
                Query
                  .fromTsId(TsId(TestId(1234), TsLabel("label")), start, end)
                  .transform("new_label")((_, _) => TSEntry(1, i.toDouble, 1))
              )
            )
          )
        )
      }
    )
  )
}

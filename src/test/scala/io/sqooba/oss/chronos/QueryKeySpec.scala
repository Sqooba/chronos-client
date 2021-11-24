package io.sqooba.oss.chronos

import zio.test._
import zio.test.Assertion._
import io.sqooba.oss.promql.metrics.MatrixMetric
import org.junit.runner.RunWith

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class QueryKeySpec extends DefaultRunnableSpec {

  val spec: Spec[Any, TestFailure[ChronosError], TestSuccess] = suite("QueryKey")(
    suite("fromPromQuery")(
      testM("Should support query with no tags") {
        assertM(
          QueryKey.fromPromQuery("CustomLabel")
        )(equalTo(QueryKey("CustomLabel", Map())))
      },
      testM("should support query with a single tag") {
        assertM(
          QueryKey.fromPromQuery("""CustomLabel{tag="value"}""")
        )(
          equalTo(QueryKey("CustomLabel", Map("tag" -> "value")))
        )
      },
      testM("should support query with a multiple tags") {
        assertM(
          QueryKey.fromPromQuery("""CustomLabel{tag="value",tag2="value2"}""")
        )(
          equalTo(QueryKey("CustomLabel", Map("tag" -> "value", "tag2" -> "value2")))
        )
      }
    ),
    suite("toPromQuery")(
      testM("Should support query with no tags") {
        val query = "CustomLabel"
        assertM(
          QueryKey.fromPromQuery(query).map(_.toPromQuery)
        )(equalTo(query))
      },
      testM("Should support query with a single tag") {
        val query = """CustomLabel{tag="value"}"""
        assertM(
          QueryKey.fromPromQuery(query).map(_.toPromQuery)
        )(equalTo(query))
      },
      testM("Should support query with multiple tags") {
        val query = """CustomLabel{tag="value",tag2="value2"}"""
        assertM(
          QueryKey.fromPromQuery(query).map(_.toPromQuery)
        )(equalTo(query))
      },
      testM("Should support query with multiple pattern matching tags") {
        val query = """CustomLabel{tag=~"value",tag2!="value2",tag3!~"value3"}"""
        assertM(
          QueryKey.fromPromQuery(query).map(_.toPromQuery)
        )(equalTo(query))
      },
      testM("Should support query with multiple pattern matching tags") {
        val query = """CustomLabel{tag=~"value",tag2!="value2",tag3!~"value31|value32"}"""
        assertM(
          QueryKey.fromPromQuery(query).map(_.toPromQuery)
        )(equalTo(query))
      },
      testM("Should ignore tags with illegal operator in query") {
        val query = """CustomLabel{tag?="value",tag2==="value2,tag3="value3""}"""
        assertM(
          QueryKey.fromPromQuery(query)
        )(equalTo(QueryKey("CustomLabel", Map("tag3" -> "value3"))))
      }
    ),
    suite("fromMatrixMetric")(
      testM("should parse from the tags") {
        @annotation.nowarn("msg=deprecated")
        def in =
          QueryKey.fromMatrixMetric(
            MatrixMetric(
              metric = Map("__name__" -> "label", "tag1" -> "v1", "tag2" -> "v2"),
              List()
            )
          )
        assertM(
          in
        )(
          equalTo(QueryKey("label", Map("tag1" -> "v1", "tag2" -> "v2")))
        )
      },
      testM("should prefer given Label over name parsed from the tags") {
        assertM(
          QueryKey.fromMatrixMetric(
            "givenLabel",
            MatrixMetric(
              metric = Map("__name__" -> "label", "tag1" -> "v1", "tag2" -> "v2"),
              List()
            )
          )
        )(
          equalTo(QueryKey("givenLabel", Map("tag1" -> "v1", "tag2" -> "v2")))
        )
      },
      testM("should parse label from tags when no label is given") {
        assertM(
          QueryKey.fromMatrixMetric(
            "",
            MatrixMetric(
              metric = Map("__name__" -> "label", "tag1" -> "v1", "tag2" -> "v2"),
              List()
            )
          )
        )(
          equalTo(QueryKey("label", Map("tag1" -> "v1", "tag2" -> "v2")))
        )
      }
    ),
    suite("tagsToPromQuery")(
      test("Should serialize with no tags") {
        assert(QueryKey.tagsToPromQuery(Map()))(equalTo(""))
      },
      test("Should serialize with one tag") {
        assert(QueryKey.tagsToPromQuery(Map("tag" -> "value")))(equalTo("""{tag="value"}"""))
      },
      test("Should serialize with tags") {
        assert(
          QueryKey.tagsToPromQuery(Map("tag" -> "value", "t" -> "v"))
        )(equalTo("""{tag="value",t="v"}"""))
      }
    )
  )
}

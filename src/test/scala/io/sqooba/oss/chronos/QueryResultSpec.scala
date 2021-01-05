package io.sqooba.oss.chronos

import io.sqooba.oss.chronos.TestUtils.TestId
import io.sqooba.oss.timeseries.entity.{ TsId, TsLabel }
import io.sqooba.oss.timeseries.immutable.TSEntry
import zio.test.Assertion._
import zio.test._

object QueryResultSpec extends DefaultRunnableSpec {

  val spec = suite("QueryResult")(
    test("should be retrieved for matching tsid with a subset of tags") {
      val tsId = TsId(TestId(123), TsLabel("label"))
      val ts   = TSEntry(1, 1.23, 1)
      assert(
        QueryResult(
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
        QueryResult(
          Map(
            QueryKey("label", Map("id" -> "123", "additionalTag" -> "returnedByBackend")) -> ts
          )
        )
          .getByQueryKey("""label{id="123"}""")
      )(equalTo(Some(ts)))
    }
  )

}

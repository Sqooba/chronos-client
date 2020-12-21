# Scala Chronos Client

_Chronos – Χρόνος, personification of time in ancient Greece._

> Ever-ageing Time teaches all things.
>
> _Prometheus to Hermes in Aeschylus, Prometheus Bound, 982._

**Chronos** is a PromQL client that makes your Prometheus store look like a time series
database for retrieving complex queries. Leveraging our [PromQL client][2] underneath,
it doesn't bother you with PromQL details and just returns simple, easy-to-work-with
Scala [`TimeSeries`][2] data.

It is in a draft state at the moment: we will avoid deep API changes if possible, but
can't exclude them.

See the [changelog](CHANGELOG.md) for more details.

# Installation

The library is available on sonatype, to use it in an SBT project add the following line:

```scala
libraryDependencies += "io.sqooba.oss" %% "chronos-client" % "0.1.0"
```

For maven:

```xml
<dependency>
    <groupId>io.sqooba.oss</groupId>
    <artifactId>chronos-client_2.13</artifactId>
    <version>0.1.0</version>
</dependency>
```

# Usage

## Configuration

In order to work properly, the client needs a valid PromQL-client configuration.
This configuration is required to create a layer containing a `ChronosService` using one of the multiple helpers available in `ChronosClient`.

## Queries

The main query type of Chronos are `Range` queries, they can be grouped together or transformed using the operations available in
[`Query.scala`](src/main/scala/io/sqooba/oss/chronos/Query.scala).

Queries can be constructed as follows:

```scala
import java.time.Instant
import io.sqooba.oss.promql.RangeQuery
import io.sqooba.oss.chronos.{ChronosClient, Chronos, InvalidQueryError, Query}
import zio.IO

object Main extends zio.App {

  def run(args: List[String]) = {
    val start = Instant.now.minusSeconds(60)
    val end = Instant.now

    val layer = ChronosClient.liveDefault

    val queryFromString: IO[InvalidQueryError, Query] =
      Query.fromString(
        """cpu{type="workstation"}""",
        start,
        end,
        step = Some(10)
      )
    val queryFromProm: IO[InvalidQueryError, Query] =
      Query.from(
        RangeQuery(
          """cpu{type="workstation"}""",
          start,
          end,
          step = 10,
          timeout = None
        )
      )
    IO.unit.exitCode
  }

}
```

It is also possible to create a chronos query for a given [TsId][2]. Chronos introduced a new type `ChronosEntityId` that represents an entity compatible with Chronos.

```scala
import java.time.Instant
import io.sqooba.oss.chronos.{
  ChronosEntityId,
  ChronosClient,
  Chronos,
  InvalidQueryError,
  Query
}
import zio.IO
import io.sqooba.oss.timeseries.entity.TsLabel

object Main extends zio.App {

  def run(args: List[String]) = {
    val start = Instant.now.minusSeconds(60)
    val end = Instant.now

    val layer = ChronosClient.liveDefault

    final case class Workstation(id: Long) extends ChronosEntityId {

      override def tags: Map[String, String] =
        Map("type" -> "workstation", "id" -> id.toString)

    }
    val tsId = Workstation(1).buildTsId(TsLabel("cpu"))

    val queryFromTsId: Query = Query.fromTsId(tsId, start, end, step = Some(10))
    Chronos.query(query = queryFromTsId).provideLayer(layer).exitCode
  }

}
```

This code will run the following query against the backend: `cpu{type="workstation", id="1"}`.

More advanced queries can be built by using the `Group` and `Transform` features:

```scala
import java.time.Instant
import io.sqooba.oss.chronos.{
  ChronosEntityId,
  ChronosClient,
  Chronos,
  InvalidQueryError,
  Query
}
import zio.IO
import io.sqooba.oss.timeseries.entity.TsLabel
import io.sqooba.oss.timeseries.TimeSeries
import io.sqooba.oss.timeseries.immutable.EmptyTimeSeries

object Main extends zio.App {

  def run(args: List[String]) = {
    val start = Instant.now.minusSeconds(60 * 60 * 24 * 60)
    val end = Instant.now
    val label = TsLabel("cpu")

    val layer = ChronosClient.liveDefault

    final case class Workstation(id: Long) extends ChronosEntityId {
      override def tags: Map[String, String] =
        Map("type" -> "workstation", "id" -> id.toString)
    }

    final case class Room(name: String, workstations: Seq[Workstation])
        extends ChronosEntityId {
      override def tags: Map[String, String] =
        Map("type" -> "room", "name" -> name)
    }

    val office = Room("office", (1L to 10).map(Workstation.apply))

    val workStationQueries = office.workstations
      .map(_.buildTsId(label))
      .map(tsId => Query.fromTsId(tsId, start, end, step = Some(10)))

    val groupedQueries = Query.group(workStationQueries: _*)
    val transformedQueries = groupedQueries.transform(
      office.buildTsId(label),
      start,
      end,
      step = 10
    ) { case (ir, _) =>
      office.workstations
        .map(_.buildTsId(label))
        .map(tsid => ir.getByTsId(tsid))
        .collect { case Some(ts) => ts }
        .foldLeft(EmptyTimeSeries: TimeSeries[Double])(
          _.plus(_, strict = false)
        )
    }

    Chronos
      .query(query = transformedQueries)
      .provideLayer(layer)
      .exitCode
  }

}
```

In this example, we are summing all `cpu` metrics from the workstation to create a new metric `cpu{type="room", name="office"}`.

# Versions and releases

[1]: https://github.com/Sqooba/scala-promql-client
[2]: https://github.com/Sqooba/scala-timeseries-lib

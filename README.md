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
libraryDependencies += "io.sqooba.oss" %% "chronos-client" % "0.3.2"
```

For maven:

```xml
<dependency>
    <groupId>io.sqooba.oss</groupId>
    <artifactId>chronos-client_2.13</artifactId>
    <version>0.3.2</version>
</dependency>
```

# Usage

## Configuration

In order to work properly, the client needs a valid PromQL-client configuration.
This configuration is required to create a layer containing a `ChronosService` using one of the multiple helpers available in `ChronosClient`.

## Examples

Runnable examples are available under the `examples` directory in the `io.sqooba.oss.chronosExamples` package.

In order to run those examples, a docker daemon is required. They can be run using `sbt examples/test`.

## Queries

The main query type of Chronos are `Range` queries, they can be grouped together or transformed using the operations available in
[`Query.scala`](src/main/scala/io/sqooba/oss/chronos/Query.scala). Those snippets are taken from files available inside the `examples` project.

Queries can be constructed as follows (see [BasicQueries](examples/src/test/scala/io/sqooba/oss/chronosExamples/BasicQueries.scala)):

```scala
val queryFromString: IO[InvalidQueryError, Query] =
Query.fromString(
  """cpu{type="workstation"}""",
  start,
  end,
  step
)
val queryFromProm: IO[InvalidQueryError, Query] =
Query.from(
  RangeQuery(
    """cpu{type="workstation"}""",
    start,
    end,
    timeout = None
  )
)
```

It is also possible to create a chronos query for a given [TsId][2].
Chronos introduces a new type `ChronosEntityId` that represents an entity compatible with Chronos.
A basic example can be found in [TimeSeriesEntityQuery](examples/src/test/scala/io/sqooba/oss/chronosExamples/TimeseriesEntityQuery.scala).

```scala
final case class Workstation(id: Long) extends ChronosEntityId {
  override def tags: Map[String, String] =
  Map("type" -> "workstation", "id" -> id.toString)
}
val tsId = Workstation(1).buildTsId(TsLabel("cpu"))
val queryFromTsId: Query = Query.fromTsId(tsId, start, end, step)
```

This code will run the following query against the backend: `cpu{type="workstation", id="1"}`.

More advanced queries can be built by using the `Group` and `Transform` features,
as shown in [QueryTransformation](examples/src/test/scala/io/sqooba/oss/chronosExamples/QueryTransformation.scala):

```scala
final case class Room(name: String, workstations: Seq[Workstation]) extends ChronosEntityId {
  override def tags: Map[String, String] =
    Map("type" -> "room", "name" -> name)
}

val office = Room("office", (1L to 10).map(Workstation.apply))

val workStationQueries = office.workstations
  .map(_.buildTsId(label))
  .map(tsId => Query.fromTsId(tsId, start, end))

val groupedQueries = Query.group(workStationQueries: _*)
val transformedQueries = groupedQueries.transform(
  office.buildTsId(label),
  start,
  end,
) { case (ir, _) =>
  office.workstations
    .map(_.buildTsId(label))
    .map(tsid => ir.getByTsId(tsid))
    .collect { case Some(ts) => ts }
    .foldLeft(EmptyTimeSeries: TimeSeries[Double])(
      _.plus(_, strict = false)
    )
}
```

In this example, we are summing all `cpu` metrics from the workstation to create a new metric `cpu{type="room", name="office"}`.

It is also possible to call _some_ of Prometheus' [query functions](https://prometheus.io/docs/prometheus/latest/querying/functions/).
The currenly supported functions are defined in [`QueryFunction.scala`](src/main/scala/io/sqooba/oss/chronos/QueryFunction.scala).
This is illustrated in [PromFunctionCall](examples/src/test/scala/io/sqooba/oss/chronosExamples/PromFunctionCall.scala):

```scala
val tsId        = workstation.buildTsId(TsLabel("cpu"))
val avgLabel    = "avg_cpu"
val avgQuery = Query
  .fromTsId(tsId, start, end, step = Some(step))
  .function(avgLabel, QueryFunction.AvgOverTime)
```

# Versions and releases

[1]: https://github.com/Sqooba/scala-promql-client
[2]: https://github.com/Sqooba/scala-timeseries-lib

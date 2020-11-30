# Scala Chronos Client

_Chronos – Χρόνος, personification of time in ancient Greece._

> Ever-ageing Time teaches all things.
>
>_Prometheus to Hermes in Aeschylus, Prometheus Bound, 982._

**Chronos** is a PromQL client that makes your Prometheus store look like a time series
database for retrieving complex queries. Leveraging our [PromQL client][2] underneath,
it doesn't bother you with PromQL details and just returns simple, easy-to-work-with
Scala [`TimeSeries`][2] data.

It is in a draft state at the moment: we will avoid deep API changes if possible, but
can't exclude them.

See the [changelog](CHANGELOG.md) for more details.

## Documentation

It has been ordered for Christmas, along with some additional tests.


[1]: https://github.com/Sqooba/scala-promql-client
[2]: https://github.com/Sqooba/scala-timeseries-lib

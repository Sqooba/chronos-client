package io.sqooba.oss.chronos

import io.sqooba.oss.promql.metrics.MatrixMetric
import io.sqooba.oss.timeseries.entity.{ TsId, TsLabel }
import zio.IO

/**
 * Identifies a PromQL query string. I.e. a combination of a label and tags (or possibly
 * an aggregate query) but no timestamps, delimiters etc.
 */
final case class QueryKey(key: String, tags: Map[String, String]) {

  /**
   * Checks whether this Key corresponds to the given TsId.
   *
   * For this, the label has to match the key-string and the tags of the entityId need
   * to be a subset of the tags of the queryKey.
   */
  def matches(tsId: TsId[_]): Boolean = tsId match {
    case TsId(entityId: ChronosEntityId, TsLabel(label)) =>
      label == key && (entityId.tags.toSet -- tags).isEmpty
    case _ => false
  }

  def toPromQuery: String = s"""$key${QueryKey.tagsToPromQuery(tags)}"""
}

object QueryKey {
  private val pattern        = """([A-Za-z0-9_]+)(\{[^\}]+\})?""".r
  private val tagsExtractors = """([0-9A-Za-z_="]+)="([^\}|"}]+)",?""".r

  def fromPromQuery(raw: String): IO[InvalidQueryError, QueryKey] =
    raw match {
      // scalafmt: off
      case pattern(label, null) =>
        IO.succeed(QueryKey(label, Map()))
      // scalafmt: on

      case pattern(label, tags) =>
        val rawTags = tagsExtractors.findAllMatchIn(tags).map(m => (m.group(1), m.group(2))).toMap
        IO.succeed(QueryKey(label, rawTags))

      case _ => IO.fail(InvalidQueryError(s"Unable to extract a key and tags from $raw"))
    }

  def fromTsId[I <: ChronosEntityId](tsId: TsId[I]): QueryKey =
    QueryKey(tsId.label.value, tsId.entityId.tags)

  private val nameKey = "__name__"

  def fromMatrixMetric(matrixMetric: MatrixMetric): IO[IdParsingError, QueryKey] =
    IO.fromOption(
      matrixMetric.metric
        .get(nameKey)
        .map(label => QueryKey(label, matrixMetric.metric - nameKey))
    ).orElseFail(
      IdParsingError(s"Unable to extract a label and tags from ${matrixMetric.metric}")
    )

  def tagsToPromQuery(tags: Map[String, String]): String =
    if (tags.isEmpty) {
      ""
    } else {
      tags.map { case (k, v) => s"""$k="$v"""" }.mkString("{", ",", "}")
    }
}

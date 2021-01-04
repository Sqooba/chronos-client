package io.sqooba.oss.chronos

import io.sqooba.oss.chronos.QueryKey.optionFromPromQuery
import io.sqooba.oss.promql.metrics.MatrixMetric
import io.sqooba.oss.timeseries.entity.{ TsId, TsLabel }
import zio.IO

/**
 * Identifies a PromQL query string. I.e. a combination of a label/name and tags (or
 * possibly an aggregate query) but no timestamps, delimiters etc.
 */
final case class QueryKey(name: String, tags: Map[String, String]) {

  /**
   * Checks whether this QueryKey matches the given TsId. The names have to be equal and
   * the given tsId's tags need to be a subset of the tags of this QueryKey's tags.
   */
  def matches(tsId: TsId[_]): Boolean = tsId match {
    case TsId(entityId: ChronosEntityId, TsLabel(label)) =>
      matches(QueryKey(label, entityId.tags))
    case _ => false
  }

  /**
   * Checks whether this QueryKey matches the given raw key. The names have to be equal and
   * the given key's tags need to be a subset of the tags of this QueryKey's tags.
   */
  def matches(key: String): Boolean =
    optionFromPromQuery(key).exists(matches)

  /**
   * Checks whether this QueryKey matches the given key. The names have to be equal and
   * the given key's tags need to be a subset of the tags of this QueryKey's tags.
   */
  def matches(other: QueryKey): Boolean =
    other.name == this.name && (other.tags.toSet -- this.tags).isEmpty

  def toPromQuery: String = s"""$name${QueryKey.tagsToPromQuery(tags)}"""
}

object QueryKey {
  private val pattern        = """([A-Za-z0-9_]+)(\{[^\}]+\})?""".r
  private val tagsExtractors = """([0-9A-Za-z_="]+)="([^\}|"}]+)",?""".r

  def fromPromQuery(raw: String): IO[InvalidQueryError, QueryKey] =
    IO.fromOption(optionFromPromQuery(raw))
      .orElseFail(InvalidQueryError(f"Unable to extract a key and tags from $raw"))

  def optionFromPromQuery(raw: String): Option[QueryKey] =
    raw match {
      // scalastyle:off
      case pattern(name, null) =>
        Some(QueryKey(name, Map()))
      // scalastyle:on

      case pattern(name, tags) =>
        val rawTags = tagsExtractors.findAllMatchIn(tags).map(m => (m.group(1), m.group(2))).toMap
        Some(QueryKey(name, rawTags))

      case _ => None
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

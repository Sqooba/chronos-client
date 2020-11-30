package io.sqooba.oss.chronos

import io.sqooba.oss.timeseries.entity.TimeSeriesEntityId

/**
 * Clients that want to use the TsId interface of Chronos need to implement this
 * sub-trait of the TimeSeriesEntityId for their entities.
 */
trait ChronosEntityId extends TimeSeriesEntityId {

  def tags: Map[String, String]

  override def toString: String = QueryKey.tagsToPromQuery(tags)
}

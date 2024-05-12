package com.stoufexis.lib.consumer

import cats.syntax.all._
import fs2.Chunk
import fs2.kafka._

case class ProcessedBatch[F[_], Key, State, Value](
  records: Map[Key, (State, Chunk[Value])],
  offset:  CommittableOffset[F]
) {
  def statesMap: Map[Key, State]            = records.fmap(_._1)
  def values:    Chunk[(Key, Chunk[Value])] = Chunk.from(records.fmap(_._2))
}

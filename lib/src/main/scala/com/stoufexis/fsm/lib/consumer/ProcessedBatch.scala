package com.stoufexis.fsm.lib.consumer

import cats.syntax.all._
import fs2.Chunk
import fs2.kafka._

case class ProcessedBatch[F[_], InstanceId, State, Value](
  records: Map[InstanceId, (State, Chunk[Value])],
  offset:  CommittableOffset[F]
) {
  def statesMap: Map[InstanceId, State] = records.fmap(_._1)
  def values:    Chunk[Value]           = Chunk.from(records).flatMap(_._2._2)
}

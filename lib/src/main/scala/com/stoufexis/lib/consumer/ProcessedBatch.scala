package com.stoufexis.lib.consumer

import fs2.Chunk
import fs2.kafka._
import scala.annotation.unused

case class ProcessedBatch[F[_], Key, Value](
  records: Map[Key, Value],
  offset:  CommittableOffset[F]
) {
  def toProducerRecords(topic: String): CommittableProducerRecords[F, Key, Value] = {
    val pRecords: Chunk[ProducerRecord[Key, Value]] =
      Chunk.from(records.map { case (key, value) => ProducerRecord(topic, key, value) })

    CommittableProducerRecords(pRecords, offset)
  }

  def split[A, B](f: Value => (A, B)): (Chunk[(Key, A)], ProcessedBatch[F, Key, B]) =
    ???

  def compact[A](implicit @unused ev: Value <:< Chunk[A]): ProcessedBatch[F, Key, A] =
    ???
}

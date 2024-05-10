package com.stoufexis.lib.kafka

import cats._
import cats.effect.implicits._
import cats.effect._
import cats.implicits._
import fs2._
import fs2.kafka._
import scala.collection.mutable

case class Batch[F[_]: Concurrent, K, V](
  chunks:  List[(K, Chunk[V])],
  offsets: CommittableOffsetBatch[F]
){

  /** The chunks corresponding to different keys can be processed in parallel. This does not
    * preserve the order of the output. Each Key is supposed to represent an independent
    * "session" of the state machine, so no cross-key relationship is assumed or guaranteed.
    */
  def parProcess[Out](f: (K, Chunk[V]) => F[Out]): F[List[(K, Out)]] =
    chunks.parUnorderedTraverse { case (k, chunk) =>
      f(k, chunk) map ((k, _))
    }
}

object Batch {

  /** Assumes that chunk is ordered by (topicpartition, offset).
    */
  def apply[F[_]: Concurrent, Key, Value](
    chunk: Chunk[CommittableConsumerRecord[F, Key, Value]]
  ): Batch[F, Key, Value] = {
    var offsetBatch: CommittableOffsetBatch[F] =
      CommittableOffsetBatch.empty[F]

    val recordsBatchMut: mutable.Map[Key, mutable.ListBuffer[Value]] =
      mutable.Map.empty

    chunk foreach { commitableRecord =>
      val key:       Key                       = commitableRecord.record.key
      val value:     Value                     = commitableRecord.record.value
      val newOffset: CommittableOffsetBatch[F] = offsetBatch.updated(commitableRecord.offset)

      offsetBatch = newOffset

      recordsBatchMut.get(commitableRecord.record.key) match {
        case None      => recordsBatchMut += (key -> mutable.ListBuffer(value))
        case Some(buf) => buf.append(value)
      }
    }

    val recordsBatch: List[(Key, Chunk[Value])] =
      recordsBatchMut.view.map { case (k, v) => (k, Chunk.from(v)) }.toList

    Batch(recordsBatch, offsetBatch)
  }
}

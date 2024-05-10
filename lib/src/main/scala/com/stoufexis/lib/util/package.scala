package com.stoufexis.lib

import cats.Applicative
import com.stoufexis.lib.kafka.Batch
import fs2._
import fs2.kafka._
import scala.collection.mutable

/** Contains utilities for (very) specialized transformations, usually for a single user
  * function. They often use mutation for better performance and are also critical pieces of
  * the core logic. This means they require significant testing efforts. Extracting them and
  * documenting them seperately also provides better readability for other functions.
  */
package object util {

  /** Assumes that chunk is ordered by (topicpartition, offset).
    */
  def chunkToBatch[F[_]: Applicative, Key, Value](
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

  def listToStateAndChunk[Key, State, Out](
    list: List[(Key, (State, Chunk[Out]))]
  ): (Map[Key, State], Chunk[(Key, Out)]) = {
    val view      = list.view
    val newStates = view.map { case (key, (st, _)) => (key, st) }.toMap
    val outputs   = Chunk.concat(view.map { case (key, (_, o)) => o.map((key, _)) }.toList)

    newStates -> outputs
  }
}

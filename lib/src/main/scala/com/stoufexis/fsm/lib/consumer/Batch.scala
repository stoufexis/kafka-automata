package com.stoufexis.fsm.lib.consumer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.kafka._

/**
  * A batch of to-be-processed records.
  */
trait Batch[F[_], InstanceId, Value] {
  def process[Out](
    f: (InstanceId, Chunk[Value]) => F[Out]
  ): F[(Map[InstanceId, Out], CommittableOffset[F])]
}

object Batch {

  /** Assumes that chunk contains only one topic-partition and is ordered by offset. Returns None if
    * input chunk was empty.
    */
  def apply[F[_]: Concurrent, InstanceId, V](
    chunk: Chunk[CommittableConsumerRecord[F, InstanceId, V]]
  ): Option[Batch[F, InstanceId, V]] =
    // Checks if empty and also gives us the last which is needed later
    chunk.last map { last =>
      new Batch[F, InstanceId, V] {
        val records: Map[InstanceId, Vector[V]] =
          chunk.foldLeft(Map[InstanceId, Vector[V]]()) {
            case (map, record) =>
              val key   = record.record.key
              val value = record.record.value

              map.updatedWith(key) {
                case None      => Some(Vector(value))
                case Some(acc) => Some(acc :+ value)
              }
          }

        def chunkToMap[A](chunk: Chunk[(InstanceId, A)]): Map[InstanceId, A] =
          chunk
            .iterator
            .map { case (inst, a) => (inst, a) }
            .toMap

        // I don't like all the iterations that are necessary in this step
        // TODO: improve
        override def process[Out](f: (InstanceId, Chunk[V]) => F[Out])
          : F[(Map[InstanceId, Out], CommittableOffset[F])] =
          Chunk
            .from(records.fmap(Chunk.from))
            .parUnorderedTraverse {
              case (k, vs) => f(k, vs) map ((k, _))
            }
            .map { processedRecords =>
              (chunkToMap(processedRecords), last.offset)
            }
      }

    }
}

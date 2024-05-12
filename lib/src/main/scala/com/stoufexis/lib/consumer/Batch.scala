package com.stoufexis.lib.consumer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.stoufexis.lib.util.chunkToMap
import fs2._
import fs2.kafka._

trait Batch[F[_], Key, Value] {
  def process[Out](f: (Key, Chunk[Value]) => F[Out]): F[(Map[Key, Out], CommittableOffset[F])]
}

object Batch {

  /** Assumes that chunk contains only one topic-partition and is ordered by offset. Returns
    * None if input chunk was empty.
    */
  def apply[F[_]: Concurrent, K, V](
    chunk: Chunk[CommittableConsumerRecord[F, K, V]]
  ): Option[Batch[F, K, V]] =
    // Checks if empty and also gives us the last which is needed later
    chunk.last map { last =>
      new Batch[F, K, V] {
        val records: Map[K, Vector[V]] =
          chunk.foldLeft(Map[K, Vector[V]]()) {
            case (map, record) =>
              val key   = record.record.key
              val value = record.record.value

              map.updatedWith(key) {
                case None      => Some(Vector(value))
                case Some(acc) => Some(acc :+ value)
              }
          }

        // I don't like all the iterations that are necessary in this step
        // TODO: improve
        override def process[Out](f: (K, Chunk[V]) => F[Out])
          : F[(Map[K, Out], CommittableOffset[F])] =
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

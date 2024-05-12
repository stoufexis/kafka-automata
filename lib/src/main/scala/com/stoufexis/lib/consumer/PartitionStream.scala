package com.stoufexis.lib.consumer

import cats.Functor
import cats.effect._
import cats.implicits._
import com.stoufexis.lib.typeclass.Empty
import com.stoufexis.lib.util.chunkToMap
import fs2._
import fs2.kafka.CommittableConsumerRecord
import fs2.kafka.consumer._
import org.apache.kafka.common.TopicPartition
import scala.concurrent.duration.FiniteDuration

trait PartitionStream[F[_], Key, Value] {
  val topicPartition: TopicPartition

  def process[State: Empty, Out](
    init: Map[Key, State],
    f:    (State, Chunk[Value]) => F[(State, Chunk[Out])]
  ): Stream[F, (Map[Key, State], ProcessedBatch[F, Key, Out])]

}

object PartitionStream {
  def fromConsume[F[_]: Temporal, K, V](
    consumer:   KafkaConsume[F, K, V],
    batchEvery: FiniteDuration
  ): Stream[F, PartitionStream[F, K, V]] =
    consumer.partitionsMapStream.flatMap {
      partitions: Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, K, V]]] =>
        Stream.iterable {
          partitions.map { case (topicPartition, records) =>
            val batches: Stream[F, Batch[F, K, V]] =
              records
                .groupWithin(Int.MaxValue, batchEvery)
                .mapFilter(Batch(_))

            fromBatches(topicPartition, batches)
          }
        }
    }

  def fromBatches[F[_]: Functor, K, V](
    partition: TopicPartition,
    batches:   Stream[F, Batch[F, K, V]]
  ): PartitionStream[F, K, V] =
    new PartitionStream[F, K, V] {

      override val topicPartition: TopicPartition =
        partition

      override def process[State: Empty, Out](
        init: Map[K, State],
        f:    (State, Chunk[V]) => F[(State, Chunk[Out])]
      ): Stream[F, (Map[K, State], ProcessedBatch[F, K, Out])] =
        batches.evalMapAccumulate(init) { (states, batch) =>
          batch
            .process { case (key, inputs) =>
              f(states.getOrElse(key, Empty[State].empty), inputs)
            }
            .map { processed: ProcessedBatch[F, K, (State, Chunk[Out])] =>
              val (newStates, out) =
                processed.split(identity)

              (states ++ chunkToMap(newStates), out.compact)
            }
        }
    }
}

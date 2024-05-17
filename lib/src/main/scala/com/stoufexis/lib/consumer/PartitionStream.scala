package com.stoufexis.lib.consumer

import cats.Functor
import cats.effect._
import cats.implicits._
import com.stoufexis.lib.config.ConsumerConfig
import com.stoufexis.lib.typeclass.Empty
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import scala.concurrent.duration.FiniteDuration

trait PartitionStream[F[_], Key, Value] {

  val topicPartition: TopicPartition

  def process[State: Empty, Out](
    init: Map[Key, State],
    f:    (State, Chunk[Value]) => F[(State, Chunk[Out])]
  ): Stream[F, ProcessedBatch[F, Key, State, Out]]

}

object PartitionStream {
  def fromConsumer[F[_]: Temporal, K, V](
    consumerConfig: ConsumerConfig[F, K, V],
    groupId:        String,
    topic:          String,
    batchEvery:     FiniteDuration
  ): Stream[F, PartitionStream[F, K, V]] =
    for {
      // TODO: Log consumer creation
      consumer: KafkaConsumer[F, K, V] <-
        consumerConfig
          .makeConsumer(topic, Some(groupId), ConsumerConfig.Seek.None)

      partitions: Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, K, V]]] <-
        consumer.partitionsMapStream

      (topicPartition, records) <-
        Stream.iterable(partitions)

      batches: Stream[F, Batch[F, K, V]] =
        records
          .groupWithin(Int.MaxValue, batchEvery)
          .mapFilter(Batch(_))

    } yield fromBatches(topicPartition, batches)

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
      ): Stream[F, ProcessedBatch[F, K, State, Out]] =
        batches.evalMapAccumulate(init) { (states, batch) =>
          batch
            .process { case (key, inputs) =>
              f(states.getOrElse(key, Empty[State].empty), inputs)
            }
            .map {
              case (processed: Map[K, (State, Chunk[Out])], offset: CommittableOffset[F]) =>
                val outBatch: ProcessedBatch[F, K, State, Out] =
                  ProcessedBatch(processed, offset)

                (states ++ outBatch.statesMap, outBatch)
            }
        }.map(_._2)
    }
}

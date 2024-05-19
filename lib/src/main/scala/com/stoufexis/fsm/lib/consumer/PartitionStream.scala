package com.stoufexis.fsm.lib.consumer

import cats.Functor
import cats.effect._
import cats.implicits._
import com.stoufexis.fsm.lib.config.ConsumerConfig
import com.stoufexis.fsm.lib.fsm.FSM
import com.stoufexis.fsm.lib.typeclass._
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import scala.concurrent.duration.FiniteDuration

trait PartitionStream[F[_], InstanceId, Value] {

  val topicPartition: TopicPartition

  def process[State, Out](
    init: Map[InstanceId, State],
    f:    FSM[F, InstanceId, State, Value, Out]
  ): Stream[F, ProcessedBatch[F, InstanceId, State, Out]]

}

object PartitionStream {
  def fromConsumer[F[_]: Async, K: Deserializer[F, *], V: FromRecord[F, K, *]](
    consumerConfig: ConsumerConfig,
    groupId:        String,
    topic:          String,
    batchEvery:     FiniteDuration
  ): Stream[F, PartitionStream[F, K, V]] =
    for {
      // TODO: Log consumer creation
      consumer: KafkaConsumer[F, K, Array[Byte]] <-
        consumerConfig
          .makeConsumer[F, K, Array[Byte]](topic, Some(groupId), ConsumerConfig.Seek.None)

      partitions: Map[
        TopicPartition,
        Stream[F, CommittableConsumerRecord[F, K, Array[Byte]]]
      ] <-
        consumer.partitionsMapStream

      (topicPartition, records) <-
        Stream.iterable(partitions)

      batches: Stream[F, Batch[F, K, V]] =
        records
          // log failed deserialization
          .evalMapFilter { ccr =>
            FromRecord[F, K, V].apply(ccr.record).map {
              _.map { v => ccr.map(_ => v) }
            }
          }
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

      override def process[State, Out](
        init: Map[K, State],
        f:    FSM[F, K, State, V, Out]
      ): Stream[F, ProcessedBatch[F, K, State, Out]] =
        batches.evalMapAccumulate(init) { (states, batch) =>
          batch
            .process { case (key, inputs) =>
              f(key)(states.get(key), inputs)
            }
            .map {
              case (out: Map[K, (Option[State], Chunk[Out])], ofs: CommittableOffset[F]) =>
                val outBatch: ProcessedBatch[F, K, State, Out] =
                  ProcessedBatch(out, ofs)

                val outStates: Map[K, Option[State]] =
                  outBatch.statesMap

                val newStates: Map[K, State] =
                  outStates.foldLeft(states) {
                    case (st, (k, v)) => st.updatedWith(k)(_ => v)
                  }

                (newStates, outBatch)
            }
        }.map(_._2)
    }
}

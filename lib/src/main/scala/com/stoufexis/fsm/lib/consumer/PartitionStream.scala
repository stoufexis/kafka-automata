package com.stoufexis.fsm.lib.consumer

import cats.Functor
import cats.effect._
import cats.implicits._
import com.stoufexis.fsm.lib.config.ConsumerConfig
import com.stoufexis.fsm.lib.fsm.FSM
import com.stoufexis.fsm.lib.util.Result
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.FiniteDuration

trait PartitionStream[F[_], InstanceId, Value] {

  val topicPartition: TopicPartition

  def process[State, Out](
    init: Map[InstanceId, State],
    f:    FSM[F, InstanceId, State, Value, Out]
  ): Stream[F, ProcessedBatch[F, InstanceId, State, Out]]

}

object PartitionStream {
  // TODO Error handling for serialization errors
  // TODO logging
  def fromConsumer[F[_], K: Deserializer[F, *], V: Deserializer[F, *]](
    consumerConfig: ConsumerConfig,
    groupId:        String,
    topic:          String,
    batchEvery:     FiniteDuration
  )(implicit
    log: Logger[F],
    F:   Async[F]
  ): Stream[F, PartitionStream[F, K, V]] = {
    def filterDeserializationFailures(
      records: Stream[F, CommittableConsumerRecord[F, Result[K], Result[V]]]
    ): Stream[F, CommittableConsumerRecord[F, K, V]] =
      records.evalMapFilter { in =>
        in.record.bisequence match {
          case Left(err) =>
            log
              .warn(s"Rejecting input record due to deserialization error: ${err}")
              .as(Option.empty[CommittableConsumerRecord[F, K, V]])

          case Right(record) =>
            CommittableConsumerRecord[F, K, V](record, in.offset)
              .some
              .pure[F]
        }
      }

    def logNewPartition(tp: TopicPartition): Stream[F, Unit] =
      Stream.eval(log.info(s"Got assigned to partition $tp"))

    def completeStream(tp: TopicPartition): F[Unit] =
      log.info(s"Closing stream for $tp")

    for {
      consumer: KafkaConsumer[F, Result[K], Result[V]] <-
        Stream.eval(log.info("Create consumer")) >>
        consumerConfig
          .makeConsumer[F, Result[K], Result[V]](topic, Some(groupId), ConsumerConfig.Seek.None)

      pm: Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, Result[K], Result[V]]]] <-
        consumer.partitionsMapStream

      (topicPartition, records) <-
        Stream.iterable(pm)

      _ <-
        logNewPartition(topicPartition)

      batches: Stream[F, Batch[F, K, V]] =
        records
          .through(filterDeserializationFailures)
          .groupWithin(Int.MaxValue, batchEvery)
          .mapFilter(Batch(_))
          .onFinalize(completeStream(topicPartition))

    } yield fromBatches(topicPartition, batches)
  }

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
            .process { (key, inputs) =>
              f.batch(key)(states.get(key), inputs)
            }
            .map {
              case (out: Map[K, (Option[State], Chunk[Out])], ofs: CommittableOffset[F]) =>
                // TODO: Filter out None states if input state was None too.
                //       Outputting a null state to the topic would be useless then.

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

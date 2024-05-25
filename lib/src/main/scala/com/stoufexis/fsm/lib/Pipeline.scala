package com.stoufexis.fsm.lib

import cats.effect._
import cats.implicits._
import com.stoufexis.fsm.lib.config._
import com.stoufexis.fsm.lib.consumer._
import com.stoufexis.fsm.lib.fsm._
import com.stoufexis.fsm.lib.sink._
import com.stoufexis.fsm.lib.typeclass._
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.FiniteDuration

trait Pipeline[F[_], InstanceId, State, In, Out] {
  def process(f: FSM[F, InstanceId, State, In, Out]): Stream[F, Unit]
}

object Pipeline {

  /** @param bootstrapServers
    * @param producerLinger
    *   Configures the linger.ms of the producer
    * @param producerBatchSize
    *   Configures the batch.size of the producer
    * @param consumerGroupId
    * @param consumerBatchEvery
    *   Waits this amount of time collecting incoming records before processing
    * @param topicIn
    * @param stateTopic
    *   The topic to which state snapshots are emitted. It is recommended that the topic is
    *   compacted.
    * @param stateTopicPartitions
    *   The partitions of the state topic. Should remain constant unless proper care is taken
    * @param topicOut
    *   Pick the output topic based on the value to be produced
    * @return
    */
  def apply[F[_], InstanceId, State, In, Out](
    bootstrapServers:     String,
    producerLinger:       FiniteDuration,
    producerBatchSize:    Int,
    consumerGroupId:      String,
    consumerBatchEvery:   FiniteDuration,
    topicIn:              String,
    stateTopic:           String,
    stateTopicPartitions: Int,
    toRecords:            ToRecords[F, Out]
  )(implicit
    ev1: Async[F],
    ev2: Serializer[F, InstanceId],
    ev3: Deserializer[F, InstanceId],
    ev5: Serializer[F, State],
    ev6: Deserializer[F, State],
    ev7: Deserializer[F, In]
  ): F[Pipeline[F, InstanceId, State, In, Out]] =
    Slf4jLogger.fromClass[F](Pipeline.getClass()).map { implicit log =>
      val pConfig: ProducerConfig =
        ProducerConfig(
          bootstrapServers = bootstrapServers,
          linger           = producerLinger,
          batchSize        = producerBatchSize
        )

      val stateConsumerConfig: ConsumerConfig =
        ConsumerConfig(bootstrapServers)

      val valueConsumerConfig: ConsumerConfig =
        ConsumerConfig(bootstrapServers)

      val partitionStreams: Stream[F, PartitionStream[F, InstanceId, In]] =
        PartitionStream.fromConsumer(
          consumerConfig = valueConsumerConfig,
          groupId        = consumerGroupId,
          topic          = topicIn,
          batchEvery     = consumerBatchEvery
        )

      val sink: Sink[F, InstanceId, State, Out] =
        Sink.fromKafka(
          producerConfig       = pConfig,
          consumeStateConfig   = stateConsumerConfig,
          stateTopic           = stateTopic,
          stateTopicPartitions = stateTopicPartitions,
          toRecords            = toRecords
        )

      apply(partitionStreams, sink)
    }

  def apply[F[_]: Concurrent, InstanceId, State, In, Out](
    partitionStreams: Stream[F, PartitionStream[F, InstanceId, In]],
    sink:             Sink[F, InstanceId, State, Out]
  )(implicit
    log: Logger[F]
  ): Pipeline[F, InstanceId, State, In, Out] =
    new Pipeline[F, InstanceId, State, In, Out] {
      def logAndRethrow[A](t: Throwable, tp: TopicPartition): Stream[F, A] =
        Stream.eval(log.error(t)(s"Could not rebuild state for $tp")) >>
          Stream.raiseError[F](t)

      override def process(f: FSM[F, InstanceId, State, In, Out]): Stream[F, Unit] = {
        for {
          stream: PartitionStream[F, InstanceId, In] <-
            partitionStreams

          // Failing to recover state for a partition is considered fatal for now
          // so we leave the error unhandled
          sinkForPartition: sink.ForPartition <-
            sink
              .forPartition(stream.topicPartition)
              .handleErrorWith(logAndRethrow(_, stream.topicPartition))

          // Should remain a stream of streams here
          processed: Stream[F, Unit] =
            stream
              .process(sinkForPartition.latestState, f)
              .evalMap(sinkForPartition.emit)

        } yield processed

      }.parJoinUnbounded
    }
}

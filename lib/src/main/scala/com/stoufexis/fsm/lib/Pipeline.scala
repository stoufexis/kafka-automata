package com.stoufexis.fsm.lib

import cats.effect._
import com.stoufexis.fsm.lib.config._
import com.stoufexis.fsm.lib.consumer._
import com.stoufexis.fsm.lib.fsm._
import com.stoufexis.fsm.lib.sink._
import com.stoufexis.fsm.lib.typeclass._
import fs2._
import fs2.kafka._
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
    stateTopicPartitions: Int
  )(implicit
    ev1: Async[F],
    ev2: Serializer[F, InstanceId],
    ev3: Deserializer[F, InstanceId],
    ev5: Serializer[F, State],
    ev6: Deserializer[F, State],
    ev7: Deserializer[F, In],
    ev8: ToRecords[F, Out]
  ): Pipeline[F, InstanceId, State, In, Out] = {
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
        stateTopicPartitions = stateTopicPartitions
      )

    apply(partitionStreams, sink)
  }

  def apply[F[_]: Concurrent, InstanceId, State, In, Out](
    partitionStreams: Stream[F, PartitionStream[F, InstanceId, In]],
    sink:             Sink[F, InstanceId, State, Out]
  ): Pipeline[F, InstanceId, State, In, Out] =
    new Pipeline[F, InstanceId, State, In, Out] {
      override def process(f: FSM[F, InstanceId, State, In, Out]): Stream[F, Unit] = {
        for {
          stream: PartitionStream[F, InstanceId, In] <-
            partitionStreams

          sinkForPartition: sink.ForPartition <-
            sink.forPartition(stream.topicPartition)

          // Should remain a stream of streams here
          processed: Stream[F, Unit] =
            stream
              .process(sinkForPartition.latestState, f)
              .evalMap(sinkForPartition.emit)

        } yield processed

      }.parJoinUnbounded
    }
}

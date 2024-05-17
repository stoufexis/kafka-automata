package com.stoufexis.lib

import cats.effect._
import com.stoufexis.lib.config._
import com.stoufexis.lib.consumer._
import com.stoufexis.lib.sink.Sink
import com.stoufexis.lib.typeclass.Empty
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import scala.concurrent.duration.FiniteDuration

trait Pipeline[F[_], Key, State, In, Out] {
  def process(f: (State, Chunk[In]) => F[(State, Chunk[Out])]): Stream[F, Unit]
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
  def apply[F[_], Key, State, In, Out](
    bootstrapServers:     String,
    producerLinger:       FiniteDuration,
    producerBatchSize:    Int,
    consumerGroupId:      String,
    consumerBatchEvery:   FiniteDuration,
    topicIn:              String,
    stateTopic:           String,
    stateTopicPartitions: Int,
    topicOut:             Out => String
  )(implicit
    ev1: Async[F],
    ev2: Serializer[F, Key],
    ev3: Deserializer[F, Key],
    ev4: Empty[State],
    ev5: Serializer[F, State],
    ev6: Deserializer[F, State],
    ev7: Deserializer[F, In],
    ev8: Serializer[F, Out]
  ): Pipeline[F, Key, State, In, Out] = {
    val pConfig: ProducerConfig[F, Key, Array[Byte]] =
      ProducerConfig(
        bootstrapServers = bootstrapServers,
        linger           = producerLinger,
        batchSize        = producerBatchSize
      )

    val stateConsumerConfig: ConsumerConfig[F, Key, State] =
      ConsumerConfig(bootstrapServers)

    val valueConsumerConfig: ConsumerConfig[F, Key, In] =
      ConsumerConfig(bootstrapServers)

    val partitionStreams: Stream[F, PartitionStream[F, Key, In]] =
      PartitionStream.fromConsumer(
        consumerConfig = valueConsumerConfig,
        groupId        = consumerGroupId,
        topic          = topicIn,
        batchEvery     = consumerBatchEvery
      )

    val sink: Sink[F, Key, State, Out] =
      Sink.fromKafka(
        producerConfig       = pConfig,
        consumeStateConfig   = stateConsumerConfig,
        stateTopic           = stateTopic,
        stateTopicPartitions = stateTopicPartitions,
        topicOut             = topicOut
      )

    apply(partitionStreams, sink)
  }

  def apply[F[_]: Concurrent, Key, State: Empty, In, Out](
    partitionStreams: Stream[F, PartitionStream[F, Key, In]],
    sink:             Sink[F, Key, State, Out]
  ): Pipeline[F, Key, State, In, Out] =
    new Pipeline[F, Key, State, In, Out] {
      override def process(
        f: (State, Chunk[In]) => F[(State, Chunk[Out])]
      ): Stream[F, Unit] = {
        for {
          stream: PartitionStream[F, Key, In] <-
            partitionStreams

          topicPartition: TopicPartition =
            stream.topicPartition

          sinkForPartition: sink.ForPartition <-
            Stream.resource(sink.forPartition(topicPartition))

          statesForPartition: Map[Key, State] <-
            Stream.eval(sinkForPartition.latestState)

          // Should remain a stream of streams here
          s = for {
            batch: ProcessedBatch[F, Key, State, Out] <-
              stream.process(statesForPartition, f)

            _ <-
              Stream.eval(sinkForPartition.emit(batch))

          } yield ()

        } yield s

      }.parJoinUnbounded
    }
}

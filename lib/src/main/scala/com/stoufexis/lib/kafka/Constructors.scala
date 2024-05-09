package com.stoufexis.lib.kafka

import cats.effect.kernel.Async
import fs2.Stream
import fs2.kafka._
import org.apache.kafka.common.TopicPartition

/** Module for easing creation and for preconfiguration of kafka Consumers
  * and Producers
  */
object ConsumerStream {
  type PartitionedStream[F[_], K, V] =
    Stream[
      F,
      Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, K, V]]]
    ]

  def streamTopic[
    F[_]: Async,
    K:    Deserializer[F, *],
    V:    Deserializer[F, *]
  ](
    topicName:    String,
    kafkaServers: String,
    groupId:      String
  ): PartitionedStream[F, K, V] = {
    val settings: ConsumerSettings[F, K, V] =
      ConsumerSettings[F, K, V]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(kafkaServers)
        .withGroupId(groupId)

    KafkaConsumer
      .stream(settings)
      .subscribeTo(topicName)
      .flatMap(_.partitionsMapStream)
  }
}

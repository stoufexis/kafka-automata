package com.stoufexis.lib.kafka

import cats.effect._
import cats.implicits._
import com.stoufexis.lib.state.Snapshot
import com.stoufexis.lib.typeclass.Empty
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import scala.concurrent.duration.FiniteDuration

case class AssignedPartitions[F[_], Key, Value](
  map: Map[TopicPartition, PartitionStream[F, Key, Value]]
) {
  val (partitions: Vector[Int], partitionStreams: Vector[PartitionStream[F, Key, Value]]) =
    map
      .view
      .map { case (partition, stream) => (partition.partition, stream) }
      .toVector
      .unzip

  def process[State: Empty, Out](
    snapshot: Snapshot[F, Key, State],
    topicOut: String,
    f:        (State, Chunk[Value]) => F[(State, Chunk[Out])]
  ): Stream[F, Stream[F, CommittableProducerRecords[F, Key, Out]]] =
    for {
      statesForPartitions: Map[Key, State] <-
        Stream.eval(snapshot.latest(partitions))

      partitionStream <-
        Stream.iterable(partitionStreams)

    } yield partitionStream.process(statesForPartitions, f).map {
      case (chunk, offset) =>
        val records: Chunk[ProducerRecord[Key,Out]] =
          chunk.map { case (key, out) => ProducerRecord(topicOut, key, out) }

        CommittableProducerRecords(records, offset)
    }

}

object AssignedPartitions {
  def apply[
    F[_]: Async,
    K:    Deserializer[F, *],
    V:    Deserializer[F, *]
  ](
    topicName:    String,
    kafkaServers: String,
    groupId:      String,
    pollInterval: FiniteDuration
  ): Stream[F, AssignedPartitions[F, K, V]] = {
    val settings: ConsumerSettings[F, K, V] =
      ConsumerSettings[F, K, V]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withIsolationLevel(IsolationLevel.ReadCommitted)
        .withBootstrapServers(kafkaServers)
        .withGroupId(groupId)
        .withPollInterval(pollInterval)

    KafkaConsumer
      .stream(settings)
      .subscribeTo(topicName)
      .flatMap(_.partitionsMapStream)
      .map(x => AssignedPartitions(x.fmap(PartitionStream(pollInterval, _))))
  }
}

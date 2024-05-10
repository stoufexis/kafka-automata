package com.stoufexis.lib.kafka

import cats.effect._
import cats.implicits._
import fs2._
import fs2.kafka._
import org.apache.kafka.common.TopicPartition
import scala.concurrent.duration.FiniteDuration

case class NewlyAssignedPartitions[F[_], K, V](
  map: Map[TopicPartition, PartitionStream[F, K, V]]
) {
  def unzip: (Vector[Int], Vector[PartitionStream[F, K, V]]) =
    map
      .view
      .map { case (partition, stream) => (partition.partition, stream) }
      .toVector
      .unzip
}

object NewlyAssignedPartitions {
  def apply[F[_]: Temporal, K, V](
    groupWithin: FiniteDuration,
    map:         Map[TopicPartition, Stream[F, CommittableConsumerRecord[F, K, V]]]
  ): NewlyAssignedPartitions[F, K, V] =
    NewlyAssignedPartitions(map.fmap(PartitionStream(groupWithin, _)))
}

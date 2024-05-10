package com.stoufexis.lib.kafka

import cats.effect._
import fs2._
import fs2.kafka._
import scala.concurrent.duration.FiniteDuration

/** Batches from a single partition
  */
case class PartitionStream[F[_], K, V](stream: Stream[F, Batch[F, K, V]])

object PartitionStream {
  def apply[F[_]: Temporal, K, V](
    groupWithin: FiniteDuration,
    inStream:    Stream[F, CommittableConsumerRecord[F, K, V]]
  ): PartitionStream[F, K, V] =
    PartitionStream {
      inStream
        .groupWithin(Int.MaxValue, groupWithin)
        .map(Batch(_))
    }
}

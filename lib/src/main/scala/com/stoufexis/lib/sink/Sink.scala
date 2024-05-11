package com.stoufexis.lib.sink

import com.stoufexis.lib.consumer.ProcessedBatch
import org.apache.kafka.common.TopicPartition

trait Sink[F[_], Key, State, Out] {
  def forPartition(partition: TopicPartition): F[Sink.ForPartition[F, Key, State, Out]]

}

object Sink {
  trait ForPartition[F[_], Key, State, Out] {
    def apply(states: Map[Key, State], outs: ProcessedBatch[F, Key, Out]): F[Unit]
  }
}

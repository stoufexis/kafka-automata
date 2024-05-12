package com.stoufexis.lib

import cats.effect._
import com.stoufexis.lib.consumer._
import com.stoufexis.lib.sink.Sink
import com.stoufexis.lib.state.Snapshot
import com.stoufexis.lib.typeclass.Empty
import fs2._
import org.apache.kafka.common.TopicPartition

trait Pipeline[F[_], Key, State, In, Out] {
  def process(f: (State, Chunk[In]) => F[(State, Chunk[Out])]): Stream[F, Unit]
}

object Pipeline {
  def apply[F[_]: Concurrent, Key, State: Empty, In, Out](
    partitionStreams: Stream[F, PartitionStream[F, Key, In]],
    snapshot:         Snapshot[F, Key, State],
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

          statesForPartitions: Map[Key, State] <-
            Stream.eval(snapshot.latest(topicPartition.partition))

          // Should remain a stream of streams here
          s = for {
            (newStates: Map[Key, State], batch: ProcessedBatch[F, Key, Out]) <-
              stream.process(statesForPartitions, f)

            sinkForPartition: Sink.ForPartition[F, Key, State, Out] <-
              Stream.eval(sink.forPartition(topicPartition))

            _ <-
              Stream.eval(sinkForPartition(newStates, batch))

          } yield ()
        } yield s
      }.parJoinUnbounded
    }
}

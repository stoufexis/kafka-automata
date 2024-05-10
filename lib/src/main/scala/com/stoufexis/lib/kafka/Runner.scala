package com.stoufexis.lib.kafka

import cats._
import cats.effect._
import cats.implicits._
import com.stoufexis.lib.fsm.FSM
import com.stoufexis.lib.state.Snapshot
import com.stoufexis.lib.typeclass.Empty
import fs2._
import fs2.kafka._
import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

object Runner {
  // def statefulTopicToTopic[F[_], Key, Input, Output, State: Empty](
  //   consumer: Stream[F, AssignedPartitions[F, Key, Input]],
  //   snapshot: Snapshot[F, Key, State],
  //   fsm:      FSM[F, State, Input, Output]
  // ): Stream[F, Stream[F, (Chunk[(Key, Output)], CommittableOffsetBatch[F])]] = {

  //   for {
  //     partitionMap: AssignedPartitions[F, Key, Input] <-
  //       consumer

  //     (partitions: Vector[Int], partitionStreams: Vector[PartitionStream[F, Key, Input]]) =
  //       partitionMap.partitions

  //     statesForPartitions: Map[Key, State] <-
  //       Stream.eval(snapshot.latest(partitions))

  //     partitionStream <-
  //       Stream.iterable(partitionStreams)

  //   } yield partitionStream.process(statesForPartitions, fsm.raw)
  // }
}

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
  def statefulTopicToTopic[F[_], Key, Input, Output: ClassTag, State: Empty](
    consumer: Stream[F, NewlyAssignedPartitions[F, Key, Input]],
    snapshot: Snapshot[F, Key, State],
    fsm:      FSM[F, State, Input, Output]
  )(implicit
    @unused ev1: Parallel.Aux[F, F],
    @unused ev2: CommutativeApplicative[F],
    E:           Empty[State]
  ): Stream[F, Stream[F, (Chunk[(Key, Output)], CommittableOffsetBatch[F])]] = {

    for {
      partitionMap: NewlyAssignedPartitions[F, Key, Input] <-
        consumer

      (partitions: Vector[Int], partitionStreams: Vector[PartitionStream[F, Key, Input]]) =
        partitionMap.unzip

      statesForPartitions: Map[Key, State] <-
        Stream.eval(snapshot.latest(partitions))

      partitionStream <-
        Stream.iterable(partitionStreams)

    } yield partitionStream.stream.evalMapAccumulate(statesForPartitions) {
      (states: Map[Key, State], batch: Batch[F, Key, Input]) =>
        val out: F[List[(Key, State, Chunk[Output])]] =
          batch.parProcess { case (key: Key, inputs: Chunk[Input]) =>
            val state: State =
              states.getOrElse(key, E.empty)

            fsm.raw(state, inputs) map {
              case (newState: State, outputs: Chunk[Output]) =>
                (key, newState, outputs)
            }
          }

        out.map { list: List[(Key, State, Chunk[Output])] =>
          val view      = list.view
          val newStates = view.map { case (key, st, _) => (key, st) }.toMap
          val outputs   = Chunk.concat(view.map { case (key, _, o) => o.map((key, _)) }.toList)

          (states ++ newStates, (outputs, batch.offsets))
        }
    }.map(_._2)
  }

  def streamTopic[
    F[_]: Async,
    K:    Deserializer[F, *],
    V:    Deserializer[F, *]
  ](
    topicName:    String,
    kafkaServers: String,
    groupId:      String,
    pollInterval: FiniteDuration
  ): Stream[F, NewlyAssignedPartitions[F, K, V]] = {
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
      .map(NewlyAssignedPartitions(pollInterval, _))
  }
}

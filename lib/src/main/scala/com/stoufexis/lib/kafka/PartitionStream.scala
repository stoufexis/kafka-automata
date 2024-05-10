package com.stoufexis.lib.kafka

import cats._
import cats.effect._
import cats.implicits._
import com.stoufexis.lib.typeclass.Empty
import com.stoufexis.lib.util.listToStateAndChunk
import fs2._
import fs2.kafka._
import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration

/** Batches from a single partition
  */
case class PartitionStream[F[_], Key, Value](stream: Stream[F, Batch[F, Key, Value]]) {

  /** Batches must be processed sequentially to preserve the order in which messages for a
    * given key are observed.
    */
  def process[State, Out](
    init: Map[Key, State],
    f:    (State, Chunk[Value]) => F[(State, Chunk[Out])]
  )(implicit
    @unused ev1: Parallel.Aux[F, F],
    @unused ev2: CommutativeApplicative[F],
    E:           Empty[State]
  ): Stream[F, (Chunk[(Key, Out)], CommittableOffsetBatch[F])] =
    stream.evalMapAccumulate(init) { (states: Map[Key, State], batch: Batch[F, Key, Value]) =>
      batch
        .parProcess { (key, inputs) =>
          f(states.getOrElse(key, E.empty), inputs) 
        }
        .map { list: List[(Key, (State, Chunk[Out]))] =>
          val (newStates, outputs) = listToStateAndChunk(list)

          (states ++ newStates, (outputs, batch.offsets))
        }
    }.map(_._2)
}

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

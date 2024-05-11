package com.stoufexis.lib.kafka

import cats._
import cats.effect._
import cats.implicits._
import com.stoufexis.lib.typeclass.Empty
import fs2._
import fs2.kafka._
import scala.collection.SeqView
import scala.concurrent.duration.FiniteDuration

/** Batches from a single partition
  */
case class PartitionStream[F[_]: Functor, Key, Value](
  stream: Stream[F, Batch[F, Key, Value]]
) {

  /** Batches must be processed sequentially to preserve the order in which messages for a
    * given key are observed.
    */
  def process[State: Empty, Out](
    init: Map[Key, State],
    f:    (State, Chunk[Value]) => F[(State, Chunk[Out])]
  ): Stream[F, (Chunk[(Key, Out)], CommittableOffset[F])] =
    stream.evalMapAccumulate(init) {
      (states: Map[Key, State], batch: Batch[F, Key, Value]) =>
        batch
          .parProcess { (key, inputs) =>
            f(states.getOrElse(key, Empty[State].empty), inputs)
          }
          .map { case (list: List[(Key, (State, Chunk[Out]))], offset: CommittableOffset[F]) =>
            val view: SeqView[(Key, (State, Chunk[Out]))] =
              list.view

            val newStates: Map[Key, State] =
              view.map { case (key, (st, _)) => (key, st) }.toMap

            val outputs =
              Chunk.concat(view.map { case (key, (_, o)) => o.map((key, _)) }.toList)

            (states ++ newStates, (outputs, offset))
          }
    }.map(_._2)
}

object PartitionStream {
  def apply[F[_]: Temporal, K, V](
    groupWithin: FiniteDuration,
    inStream:    Stream[F, CommittableConsumerRecord[F, K, V]]
  ): PartitionStream[F, K, V] =
    PartitionStream(inStream.groupWithin(Int.MaxValue, groupWithin).map(Batch(_)))
}

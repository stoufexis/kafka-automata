package com.stoufexis.lib.consumer

import cats.Functor
import cats.implicits._
import com.stoufexis.lib.typeclass.Empty
import com.stoufexis.lib.util.chunkToMap
import fs2._

trait PartitionStream[F[_], Key, Value] {

  def process[State: Empty, Out](
    init: Map[Key, State],
    f:    (State, Chunk[Value]) => F[(State, Chunk[Out])]
  ): Stream[F, (Map[Key, State], ProcessedBatch[F, Key, Out])]

}

object PartitionStream {
  def apply[F[_]: Functor, K, V](
    batches: Stream[F, Batch[F, K, V]]
  ): PartitionStream[F, K, V] =
    new PartitionStream[F, K, V] {

      override def process[State: Empty, Out](
        init: Map[K, State],
        f:    (State, Chunk[V]) => F[(State, Chunk[Out])]
      ): Stream[F, (Map[K, State], ProcessedBatch[F, K, Out])] =
        batches.evalMapAccumulate(init) { (states, batch) =>
          batch
            .process { case (key, inputs) =>
              f(states.getOrElse(key, Empty[State].empty), inputs)
            }
            .map { processed: ProcessedBatch[F, K, (State, Chunk[Out])] =>
              val (newStates, out) =
                processed.split(identity)

              (states ++ chunkToMap(newStates), out.compact)
            }
        }
    }
}

package com.stoufexis.fsm.lib.fsm

import cats.Monad
import cats.implicits._
import fs2._

trait FSM[F[_], InstanceId, State, In, Out] {
  def batch(instance: InstanceId): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])]
}

object FSM {
  abstract class Unbatched[F[_], InstId, State, In, Out](
    implicit F: Monad[F]
  ) extends FSM[F, InstId, State, In, Out] {

    def singleton(instance: InstId): (Option[State], In) => F[(Option[State], Chunk[Out])]

    override final def batch(
      instance: InstId
    ): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])] = {

      // TODO: Can be optimised
      def loop(st: Option[State], ins: List[In], out: Chunk[Out]): F[(Option[State], Chunk[Out])] =
        ins match {
          case Nil =>
            F.pure(st, out)

          case head :: tail =>
            singleton(instance)(st, head) >>= {
              case (nextSt, nextOuts) => loop(nextSt, tail, out ++ nextOuts)
            }
        }

      (st, ins) => loop(st, ins.toList, Chunk.empty)
    }

  }
}

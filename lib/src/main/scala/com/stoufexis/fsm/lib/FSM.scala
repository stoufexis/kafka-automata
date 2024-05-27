package com.stoufexis.fsm.lib.fsm

import cats.Monad
import cats.implicits._
import fs2._

/**
  * FSM describes a set of finite state machines. 
  * Each machine in the set is uniquely identified by the InstanceId.
  */
trait FSM[F[_], InstanceId, State, In, Out] {
  def batch(instance: InstanceId): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])]
}

object FSM {
  def unbatched[F[_]: Monad, InstanceId, State, In, Out](
    f: InstanceId => (Option[State], In) => F[(Option[State], Chunk[Out])]
  ): FSM[F, InstanceId, State, In, Out] = new FSM[F, InstanceId, State, In, Out] {

    override def batch(
      instance: InstanceId
    ): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])] = {
      // TODO: Can be optimised
      def loop(st: Option[State], ins: List[In], out: Chunk[Out]): F[(Option[State], Chunk[Out])] =
        ins match {
          case Nil =>
            Monad[F].pure(st, out)

          case head :: tail =>
            f(instance)(st, head) >>= {
              case (nextSt, nextOuts) => loop(nextSt, tail, out ++ nextOuts)
            }
        }

      (st, ins) => loop(st, ins.toList, Chunk.empty)
    }

  }
}

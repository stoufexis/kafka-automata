package com.stoufexis.fsm.lib.fsm

import fs2._

trait FSM[F[_], InstanceId, State, In, Out] {
  def batch(instance: InstanceId): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])]
}

object FSM {
  trait Unbatched[F[_], InstId, State, In, Out] extends FSM[F, InstId, State, In, Out] {
    def singleton(instance: InstId): (Option[State], In) => F[(Option[State], Chunk[Out])]

    override final def batch(instance: InstId)
      : (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])] =
      ???
  }
}

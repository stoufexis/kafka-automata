package com.stoufexis.fsm.lib.fsm

import fs2._

trait FSM[F[_], InstanceId, State, In, Out] {
  def apply(instance: InstanceId): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])]
}
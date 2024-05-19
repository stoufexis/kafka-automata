package com.stoufexis.fsm.lib.fsm

import cats._
import cats.implicits._
import fs2._

trait FSM[F[_], InstanceId, State, In, Out] {
  def apply(instance: InstanceId): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])]
}

object FSM {
  implicit def funtorFSM[F[_]: Functor, Id, S, In]: Functor[FSM[F, Id, S, In, *]] =
    new Functor[FSM[F, Id, S, In, *]] {
      override def map[A, B](fa: FSM[F, Id, S, In, A])(f: A => B): FSM[F, Id, S, In, B] =
        new FSM[F, Id, S, In, B] {
          override def apply(instance: Id): (Option[S], Chunk[In]) => F[(Option[S], Chunk[B])] =
            (s: Option[S], i: Chunk[In]) => fa(instance)(s, i).map(_.map(_.map(f)))
        }
    }

  implicit def contravariantFSM[F[_], Id, S, Out]: Contravariant[FSM[F, Id, S, *, Out]] =
    new Contravariant[FSM[F, Id, S, *, Out]] {
      override def contramap[A, B](fa: FSM[F,Id,S,A,Out])(f: B => A): FSM[F,Id,S,B,Out] =
        new FSM[F, Id, S, B, Out] {
          override def apply(instance: Id): (Option[S], Chunk[B]) => F[(Option[S], Chunk[Out])] =
            (s: Option[S], i: Chunk[B]) => fa(instance)(s, i.map(f))
        }
    }
}

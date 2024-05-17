package com.stoufexis.fsm.lib.fsm

import cats.Monad
import cats.implicits._
import fs2._

case class FSM[F[_], S, I, O](raw: (S, Chunk[I]) => F[(S, Chunk[O])])

object FSM {
  def unbatched[F[_], S, I, O](
    f: (S, I) => F[(S, O)]
  )(implicit F: Monad[F]): FSM[F, S, I, O] = {
    def loop(
      state: S,
      elems: List[I],
      out:   Vector[O]
    ): F[(S, Vector[O])] =
      elems.headOption match {
        case None =>
          F.pure(state, out)

        case Some(elem) =>
          f(state, elem) >>= { case (s, o) =>
            loop(s, elems.drop(1), out :+ o)
          }
      }

    FSM { case (s, inputs) =>
      loop(s, inputs.toList, Vector.empty)
        .map(_.map(Chunk.from(_)))
    }
  }
}

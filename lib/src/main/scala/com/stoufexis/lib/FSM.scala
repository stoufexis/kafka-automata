package com.stoufexis.lib

import cats.Monad
import cats.effect._
import cats.implicits._
import com.stoufexis.lib.config.Topic
import com.stoufexis.lib.state.EmitSnapshot
import fs2.Pull._
import fs2._
import fs2.kafka._

case class FSM[F[_], S, I, O](raw: (S, Chunk[I]) => F[(S, Stream[F, O])])

object FSM {
  def unbatched[F[_], S, I, O](
    f: (S, I) => F[(S, O)]
  )(implicit F: Monad[F]): FSM[F, S, I, O] = {
    def loop(
      state: S,
      elems: Chunk[I],
      out:   Stream[F, O]
    ): F[(S, Stream[F, O])] =
      elems.head match {
        case None =>
          F.pure(state, out)

        case Some(elem) =>
          f(state, elem) >>= { case (s, o) =>
            loop(s, elems.drop(1), out ++ Stream.emit(o))
          }
      }

    FSM { case (s, inputs) => loop(s, inputs, Stream.empty) }
  }
}

package com.stoufexis.fsm.examples.voting.fsm

import cats.Applicative
import cats.effect.kernel.Async
import com.stoufexis.fsm.examples.voting.domain.message._
import com.stoufexis.fsm.lib.typeclass.ToRecords
import fs2._
import fs2.kafka._

sealed trait Output

object Output {
  // Would benefit from scala 3 union types
  case class Event(event: VoteEvent)        extends Output
  case class Update(event: VoteStateUpdate) extends Output

  def both(event: VoteEvent, update: Option[VoteStateUpdate]): Chunk[Output] =
    update match {
      case None      => Chunk.singleton(Event(event))
      case Some(upd) => Chunk(Update(upd), Event(event))
    }

  def toRecords[F[_]: Async](
    eventsTopic:  String,
    updatesTopic: String
  ): ToRecords[F, Output] =
    new ToRecords[F, Output] {
      override def apply(a: Output): F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]] =
        ???
    }
}

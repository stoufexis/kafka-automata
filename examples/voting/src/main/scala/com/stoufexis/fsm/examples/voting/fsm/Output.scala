package com.stoufexis.fsm.examples.voting.fsm

import cats.implicits._
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
      def asRecords[K: Serializer[F, *], A: Serializer[F, *]](
        key:     K,
        payload: A,
        topic : String
      ): F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]] =
        for {
          keyBytes <-
            Serializer[F, K].serialize(topic, Headers.empty, key)

          payloadBytes <-
            Serializer[F, A].serialize(topic, Headers.empty, payload)

        } yield Chunk.singleton(ProducerRecord(topic, keyBytes, payloadBytes))

      override def apply(a: Output): F[Chunk[ProducerRecord[Array[Byte], Array[Byte]]]] =
        a match {
          case Event(event)   => asRecords(event.cmd.userId, event, eventsTopic)
          case Update(update) => asRecords(update.itemId, update, updatesTopic)
        }
    }
}

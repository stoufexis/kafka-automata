package com.stoufexis.fsm.examples.voting.domain

import io.chrisdavenport.fuuid.FUUID
import io.chrisdavenport.fuuid.circe._
import io.circe._
import fs2.kafka._
import cats.effect.kernel.Sync

// TODO: Use some abstraction to remove the decoder/encoder/serializer/deserializer boilerplate
package object typ {
  private def makeencoder[A](f: A => FUUID): Encoder[A] =
    fuuidEncoder.contramap(f)

  private def makedecoder[A](f: FUUID => A): Decoder[A] =
    fuuidDecoder.map(f)

  case class ItemId(id: FUUID) extends AnyVal
  object ItemId {
    implicit def decoderItemId: Decoder[ItemId] = makedecoder(ItemId(_))
    implicit def encoderItemId: Encoder[ItemId] = makeencoder(_.id)

    implicit def deserializeItemId[F[_]: Sync]: Deserializer[F, ItemId] =
      com.stoufexis.fsm.examples.voting.codec.jsonDeserializer

    implicit def serializeItemId[F[_]: Sync]: Serializer[F, ItemId] =
      com.stoufexis.fsm.examples.voting.codec.jsonSerializer
  }

  case class UserId(id: FUUID) extends AnyVal
  object UserId {
    implicit def decoderUserId: Decoder[UserId] = makedecoder(UserId(_))
    implicit def encoderUserId: Encoder[UserId] = makeencoder(_.id)

    implicit def deserializeUserId[F[_]: Sync]: Deserializer[F, UserId] =
      com.stoufexis.fsm.examples.voting.codec.jsonDeserializer

    implicit def serializeUserId[F[_]: Sync]: Serializer[F, UserId] =
      com.stoufexis.fsm.examples.voting.codec.jsonSerializer
  }

  case class CommandId(id: FUUID) extends AnyVal
  object CommandId {
    implicit def decoderCommandId: Decoder[CommandId] = makedecoder(CommandId(_))
    implicit def encoderCommandId: Encoder[CommandId] = makeencoder(_.id)

    implicit def deserializeCommandId[F[_]: Sync]: Deserializer[F, CommandId] =
      com.stoufexis.fsm.examples.voting.codec.jsonDeserializer

    implicit def serializeCommandId[F[_]: Sync]: Serializer[F, CommandId] =
      com.stoufexis.fsm.examples.voting.codec.jsonSerializer
  }

  case class EventId(id: FUUID) extends AnyVal
  object EventId {
    implicit def decoderEventId: Decoder[EventId] = makedecoder(EventId(_))
    implicit def encoderEventId: Encoder[EventId] = makeencoder(_.id)

    implicit def deserializeEventId[F[_]: Sync]: Deserializer[F, EventId] =
      com.stoufexis.fsm.examples.voting.codec.jsonDeserializer

    implicit def serializeEventId[F[_]: Sync]: Serializer[F, EventId] =
      com.stoufexis.fsm.examples.voting.codec.jsonSerializer
  }

  case class UpdateId(id: FUUID) extends AnyVal
  object UpdateId {
    implicit def decoderUpdateId: Decoder[UpdateId] = makedecoder(UpdateId(_))
    implicit def encoderUpdateId: Encoder[UpdateId] = makeencoder(_.id)

    implicit def deserializeUpdateId[F[_]: Sync]: Deserializer[F, UpdateId] =
      com.stoufexis.fsm.examples.voting.codec.jsonDeserializer

    implicit def serializeUpdateId[F[_]: Sync]: Serializer[F, UpdateId] =
      com.stoufexis.fsm.examples.voting.codec.jsonSerializer
  }

  case class CorrelationId(id: FUUID) extends AnyVal
  object CorrelationId {
    implicit def decoderCorrelationId: Decoder[CorrelationId] = makedecoder(CorrelationId(_))
    implicit def encoderCorrelationId: Encoder[CorrelationId] = makeencoder(_.id)

    implicit def deserializeCorrelationId[F[_]: Sync]: Deserializer[F, CorrelationId] =
      com.stoufexis.fsm.examples.voting.codec.jsonDeserializer

    implicit def serializeCorrelationId[F[_]: Sync]: Serializer[F, CorrelationId] =
      com.stoufexis.fsm.examples.voting.codec.jsonSerializer
  }
}

package com.stoufexis.fsm.examples.voting.domain

import fs2.kafka._
import io.chrisdavenport.fuuid.FUUID

package object typ {
  private def makeSerializer[F[_], A](f: A => FUUID): Serializer[F, A] =
    ???
  private def makeDeserializer[F[_], A](f: FUUID => A): Deserializer[F, A] =
    ???

  case class ItemId(id: FUUID) extends AnyVal
  object ItemId {
    implicit def deserializerItemId[F[_]]: Deserializer[F, ItemId] =
      makeDeserializer(ItemId(_))

    implicit def serializerItemId[F[_]]: Serializer[F, ItemId] =
      makeSerializer(_.id)
  }

  case class UserId(id: FUUID) extends AnyVal
  object UserId {
    implicit def deserializerUserId[F[_]]: Deserializer[F, UserId] =
      makeDeserializer(UserId(_))

    implicit def serializerUserId[F[_]]: Serializer[F, UserId] =
      makeSerializer(_.id)
  }

  case class CommandId(id: FUUID) extends AnyVal
  object CommandId {
    implicit def deserializerCommandId[F[_]]: Deserializer[F, CommandId] =
      makeDeserializer(CommandId(_))

    implicit def serializerCommandId[F[_]]: Serializer[F, CommandId] =
      makeSerializer(_.id)
  }

  case class EventId(id: FUUID) extends AnyVal
  object EventId {
    implicit def deserializerEventId[F[_]]: Deserializer[F, EventId] =
      makeDeserializer(EventId(_))

    implicit def serializerEventId[F[_]]: Serializer[F, EventId] =
      makeSerializer(_.id)
  }

  case class UpdateId(id: FUUID) extends AnyVal
  object UpdateId {
    implicit def deserializerUpdateId[F[_]]: Deserializer[F, UpdateId] =
      makeDeserializer(UpdateId(_))

    implicit def serializerUpdateId[F[_]]: Serializer[F, UpdateId] =
      makeSerializer(_.id)
  }

  case class CorrelationId(id: FUUID) extends AnyVal
  object CorrelationId {
    implicit def deserializerCorrelationId[F[_]]: Deserializer[F, CorrelationId] =
      makeDeserializer(CorrelationId(_))

    implicit def serializerCorrelationId[F[_]]: Serializer[F, CorrelationId] =
      makeSerializer(_.id)
  }
}

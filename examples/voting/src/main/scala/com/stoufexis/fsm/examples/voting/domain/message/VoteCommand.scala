package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.effect.kernel.Sync
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.typ._
import fs2.kafka._
import io.chrisdavenport.fuuid.FUUIDGen
import io.circe._

sealed trait VoteCommand {
  val id:            CommandId
  val itemId:        ItemId
  val correlationId: CorrelationId
  val userId:        UserId
}

object VoteCommand {
  case class VoteStart(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class VoteEnd(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class Upvote(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class Downvote(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  def voteStart[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId,
    userId:        UserId
  ): F[VoteCommand] =
    FUUIDGen[F].random map (id => VoteStart(CommandId(id), correlationId, itemId, userId))

  def voteEnd[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId,
    userId:        UserId
  ): F[VoteCommand] =
    FUUIDGen[F].random map (id => VoteEnd(CommandId(id), correlationId, itemId, userId))

  def upvote[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId,
    userId:        UserId
  ): F[VoteCommand] =
    FUUIDGen[F].random map (id => Upvote(CommandId(id), correlationId, itemId, userId))

  def downvote[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId,
    userId:        UserId
  ): F[VoteCommand] =
    FUUIDGen[F].random map (id => Downvote(CommandId(id), correlationId, itemId, userId))

  implicit def encoderVoteCommand: Encoder[VoteCommand] =
    io.circe.generic.semiauto.deriveEncoder

  implicit def decoderVoteCommand: Decoder[VoteCommand] =
    io.circe.generic.semiauto.deriveDecoder

  implicit def deserializerVoteCommand[F[_]: Sync]: Deserializer[F, VoteCommand] =
    com.stoufexis.fsm.examples.voting.codec.jsonDeserializer
}

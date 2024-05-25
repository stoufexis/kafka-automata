package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.typ._
import io.chrisdavenport.fuuid.FUUIDGen
import fs2.kafka._

sealed trait VoteCommand {
  val id:            CommandId
  val itemId:        ItemId
  val correlationId: CorrelationId
}

object VoteCommand {
  case class VoteStart(id: CommandId, correlationId: CorrelationId, itemId: ItemId)
      extends VoteCommand

  case class VoteEnd(id: CommandId, correlationId: CorrelationId, itemId: ItemId)
      extends VoteCommand

  case class Upvote(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class Downvote(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  def voteStart[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId
  ): F[VoteCommand] =
    FUUIDGen[F].random map (id => VoteStart(CommandId(id), correlationId, itemId))

  def voteEnd[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId
  ): F[VoteCommand] =
    FUUIDGen[F].random map (id => VoteEnd(CommandId(id), correlationId, itemId))

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


  implicit def deserializerVoteCommand[F[_]]: Deserializer[F, VoteCommand] = ???
}

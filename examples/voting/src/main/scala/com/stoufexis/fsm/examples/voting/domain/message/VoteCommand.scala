package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.effect.kernel.Sync
import cats.implicits._
import com.stoufexis.fsm.examples.voting.codec._
import com.stoufexis.fsm.examples.voting.domain.typ._
import fs2.kafka._
import io.chrisdavenport.fuuid.FUUIDGen
import io.circe._
import io.circe.syntax._

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

  private val voteStartTyp: String = "VOTE_START"
  private val voteEndTyp:   String = "VOTE_END"
  private val upvoteTyp:    String = "UPVOTE"
  private val downvoteTyp:  String = "DOWNVOTE"

  implicit def encoderVoteCommand: Encoder[VoteCommand] = {
    val encoderStart: Encoder[VoteStart] = io.circe.generic.semiauto.deriveEncoder
    val encoderEnd:   Encoder[VoteEnd]   = io.circe.generic.semiauto.deriveEncoder
    val encoderUp:    Encoder[Upvote]    = io.circe.generic.semiauto.deriveEncoder
    val encoderDown:  Encoder[Downvote]  = io.circe.generic.semiauto.deriveEncoder

    Encoder.instance {
      case x: VoteStart => encoderStart(x).withType(voteStartTyp)
      case x: VoteEnd   => encoderEnd(x).withType(voteEndTyp)
      case x: Upvote    => encoderUp(x).withType(upvoteTyp)
      case x: Downvote  => encoderDown(x).withType(downvoteTyp)
    }
  }

  implicit def decoderVoteCommand: Decoder[VoteCommand] = {
    val decoderStart: Decoder[VoteStart] = io.circe.generic.semiauto.deriveDecoder
    val decoderEnd:   Decoder[VoteEnd]   = io.circe.generic.semiauto.deriveDecoder
    val decoderUp:    Decoder[Upvote]    = io.circe.generic.semiauto.deriveDecoder
    val decoderDown:  Decoder[Downvote]  = io.circe.generic.semiauto.deriveDecoder

    Decoder.instance { cursor =>
      for {
        typ <- cursor.get[String]("type")
        out <- typ match {
          case `voteStartTyp` => decoderStart(cursor)
          case `voteEndTyp`   => decoderEnd(cursor)
          case `upvoteTyp`    => decoderUp(cursor)
          case `downvoteTyp`  => decoderDown(cursor)
          case t              => Left(DecodingFailure(s"Invalid type $t", cursor.history))
        }
      } yield out
    }
  }

  implicit def deserializerVoteCommand[F[_]: Sync]: Deserializer[F, VoteCommand] =
    com.stoufexis.fsm.examples.voting.codec.jsonDeserializer
}

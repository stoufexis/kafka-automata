package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.effect.kernel.Sync
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.typ._
import fs2.kafka._
import io.chrisdavenport.fuuid.FUUIDGen
import io.circe._

/**
  * The current vote state for a given itemId
  */
case class VoteStateUpdate(
  updateId:      UpdateId,
  correlationId: CorrelationId,
  itemId:        ItemId,
  voteCnt:       Int
)

object VoteStateUpdate {
  def make[F[_]: FUUIDGen: Functor](
    correlationId: CorrelationId,
    itemId:        ItemId,
    voteCnt:       Int
  ): F[VoteStateUpdate] =
    FUUIDGen[F].random map (id => VoteStateUpdate(UpdateId(id), correlationId, itemId, voteCnt))

  implicit def encoderVoteStateUpdate: Encoder[VoteStateUpdate] =
    io.circe.generic.semiauto.deriveEncoder

  implicit def decoderVoteStateUpdate: Decoder[VoteStateUpdate] =
    io.circe.generic.semiauto.deriveDecoder

  implicit def serializeVoteEvent[F[_]: Sync]: Serializer[F, VoteStateUpdate] =
    com.stoufexis.fsm.examples.voting.codec.jsonSerializer
}

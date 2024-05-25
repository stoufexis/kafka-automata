package com.stoufexis.fsm.examples.voting.fsm

import com.stoufexis.fsm.examples.voting.domain.typ._
import fs2.kafka._
import io.circe._
import cats.effect.kernel.Sync

case class Votes(upvotedBy: Set[UserId], downvotedBy: Set[UserId]) {
  def upvote(uid: UserId): Votes =
    Votes(upvotedBy = upvotedBy + uid, downvotedBy = downvotedBy - uid)

  def downvote(uid: UserId): Votes =
    Votes(upvotedBy = upvotedBy - uid, downvotedBy = downvotedBy + uid)

  /** @return
    *   can be negative
    */
  def total: Int =
    upvotedBy.size - downvotedBy.size
}

object Votes {
  val empty: Votes = Votes(Set(), Set())


  implicit def encoderVotes: Encoder[Votes] =
    io.circe.generic.semiauto.deriveEncoder

  implicit def decoderVotes: Decoder[Votes] =
    io.circe.generic.semiauto.deriveDecoder

  implicit def serializerVotes[F[_]: Sync]: Serializer[F, Votes] =
    com.stoufexis.fsm.examples.voting.codec.jsonSerializer

  implicit def deserializerVotes[F[_]: Sync]: Deserializer[F, Votes] =
    com.stoufexis.fsm.examples.voting.codec.jsonDeserializer
}

package com.stoufexis.fsm.examples.voting.fsm

import com.stoufexis.fsm.examples.voting.domain.typ._
import fs2.kafka._

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

  implicit def serializerVotes[F[_]]: Serializer[F, Votes] = ???
  implicit def deserializerVotes[F[_]]: Deserializer[F, Votes] = ???
}

package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.typ._
import io.chrisdavenport.fuuid.FUUIDGen

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
}

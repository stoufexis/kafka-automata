package com.stoufexis.fsm.examples.voting.domain.message

import cats.Functor
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.typ.EventId
import io.chrisdavenport.fuuid.FUUIDGen

sealed trait VoteEvent

object VoteEvent {
  case class CommandExecuted(eventId: EventId, cmd: VoteCommand)                 extends VoteEvent
  case class CommandRejected(eventId: EventId, cmd: VoteCommand, reason: String) extends VoteEvent

  def commandExecuted[F[_]: FUUIDGen: Functor](cmd: VoteCommand): F[VoteEvent] =
    FUUIDGen[F].random map (id => CommandExecuted(EventId(id), cmd))

  def commandRejected[F[_]: FUUIDGen: Functor](cmd: VoteCommand, reason: String): F[VoteEvent] =
    FUUIDGen[F].random map (id => CommandRejected(EventId(id), cmd, reason))
}

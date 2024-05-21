package com.stoufexis.fsm.examples.voting.domain.message

sealed trait VoteEvent

object VoteEvent {
  case class CommandExecuted(cmd: VoteCommand)                 extends VoteEvent
  case class CommandRejected(cmd: VoteCommand, reason: String) extends VoteEvent
}

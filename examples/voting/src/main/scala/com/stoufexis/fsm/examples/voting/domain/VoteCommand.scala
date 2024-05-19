package com.stoufexis.fsm.examples.voting.domain

sealed trait VoteCommand

object VoteCommand {
  case class VoteStart(itemId: String)                extends VoteCommand
  case class VoteEnd(itemId: String)                  extends VoteCommand
  case class Upvote(itemId: String, userId: String)   extends VoteCommand
  case class Downvote(itemId: String, userId: String) extends VoteCommand
}

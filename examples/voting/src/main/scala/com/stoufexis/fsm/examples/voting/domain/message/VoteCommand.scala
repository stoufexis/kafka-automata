package com.stoufexis.fsm.examples.voting.domain.message

import com.stoufexis.fsm.examples.voting.domain.typ._

sealed trait VoteCommand {
  val id:     CommandId
  val itemId: ItemId
}

object VoteCommand {
  case class VoteStart(id: CommandId, itemId: ItemId)                extends VoteCommand
  case class VoteEnd(id: CommandId, itemId: ItemId)                  extends VoteCommand
  case class Upvote(id: CommandId, itemId: ItemId, userId: UserId)   extends VoteCommand
  case class Downvote(id: CommandId, itemId: ItemId, userId: UserId) extends VoteCommand
}

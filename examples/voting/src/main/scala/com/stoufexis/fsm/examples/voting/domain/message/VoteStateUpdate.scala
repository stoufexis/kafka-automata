package com.stoufexis.fsm.examples.voting.domain.message

import com.stoufexis.fsm.examples.voting.domain.typ._

// TODO: Add VoteStateUpdate id
case class VoteStateUpdate(itemId: ItemId, voteCnt: Int)
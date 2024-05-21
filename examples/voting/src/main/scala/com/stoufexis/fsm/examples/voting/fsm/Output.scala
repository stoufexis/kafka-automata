package com.stoufexis.fsm.examples.voting.fsm

import com.stoufexis.fsm.examples.voting.domain.message._

case class Output(either: Either[VoteEvent, VoteStateUpdate])

object Output {
  def event(event: VoteEvent): Output = ???
  def update(update: VoteStateUpdate): Output = ???
}

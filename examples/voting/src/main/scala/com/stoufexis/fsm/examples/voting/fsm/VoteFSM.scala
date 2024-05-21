package com.stoufexis.fsm.examples.voting.fsm

import cats._
import cats.implicits._
import com.stoufexis.fsm.examples.voting.domain.message._
import com.stoufexis.fsm.examples.voting.domain.typ._
import com.stoufexis.fsm.lib.fsm.FSM
import fs2._
import io.chrisdavenport.fuuid.FUUIDGen

/** Input is expected to be keyed by itemId in kafka. If it's not, we reject it.
  */
class VoteFSM[F[_]: Monad: FUUIDGen] extends FSM.Unbatched[F, ItemId, Votes, VoteCommand, Output] {

  def reject(
    state:  Option[Votes],
    cmd:    VoteCommand,
    reason: String
  ): F[(Option[Votes], Chunk[Output])] =
    VoteEvent.commandRejected(cmd, reason) map { event =>
      (state, Chunk.singleton(Output.Event(event)))
    }

  def execute(state: Option[Votes], cmd: VoteCommand): F[(Option[Votes], Chunk[Output])] =
    for {
      event: VoteEvent <-
        VoteEvent.commandExecuted(cmd)

      update: Option[VoteStateUpdate] <-
        state.traverse { votes =>
          VoteStateUpdate.make(cmd.correlationId, cmd.itemId, votes.total)
        }

    } yield (state, Output.both(event, update))

  // Aliased to make it easier to read
  type Fold = (Option[Votes], VoteCommand) => F[(Option[Votes], Chunk[Output])]

  /** @param item
    *   We will be sent commands for this item.
    */
  override def singleton(item: ItemId): Fold = {
    // Input validation: check if someone sent us badly keyed data.
    // This could be avoided if VoteCommand did not also contain itemId, but we opt for this currently.
    case (state, cmd: VoteCommand) if cmd.itemId != item =>
      reject(state, cmd, s"Command ${cmd.id} was keyed incorrectly: ${cmd.itemId} != ${item}")

    // There already exists state for this item, so vote has already started
    case (st @ Some(_), cmd: VoteCommand.VoteStart) =>
      reject(st, cmd, s"Voting has already begun for $item")

    // Start the vote for `item`
    case (None, cmd: VoteCommand.VoteStart) =>
      execute(Some(Votes.empty), cmd)

    // End the vote for `item`
    case (Some(_), cmd: VoteCommand.VoteEnd) =>
      execute(None, cmd)

    // There is no open vote, so we cannot execute these
    case (None, cmd @ (_: VoteCommand.Downvote | _: VoteCommand.Upvote | _: VoteCommand.VoteEnd)) =>
      reject(None, cmd, s"No open vote for $item")

    // For the following cases note that Votes already makes sure that there cannot be
    // double upvotes/downvotes, but we explicitly handle it here to output rejection events

    // Users cannot upvote the same item twice
    case (Some(st), cmd: VoteCommand.Upvote) if st upvotedBy cmd.userId =>
      reject(Some(st), cmd, s"User ${cmd.userId} has already upvoted $item")

    // User upvotes `item`
    case (Some(st), cmd: VoteCommand.Upvote) =>
      execute(Some(st upvote cmd.userId), cmd)

    // Users cannot downvote the same item twice
    case (Some(st), cmd: VoteCommand.Downvote) if st downvotedBy cmd.userId =>
      reject(Some(st), cmd, s"User ${cmd.userId} has already downvoted $item")

    // User downvotes `item`
    case (Some(st), cmd: VoteCommand.Downvote) =>
      execute(Some(st downvote cmd.userId), cmd)

  }
}

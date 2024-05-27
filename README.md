
# kafka-automata

## What is this project

This is a POC library written in scala for creating distributed finite state machines with heavy use of kafka and fs2.

## Caveats

The code is very underdeveloped and tested at a fairly surface level.
I created this to personally investigate how such a system should look like and for future reference, so it is not by any means production ready.

## Technical stuff

### FSM

This library provides the FSM type, which describes a set of finite state machines.

```scala
trait FSM[F[_], InstanceId, State, In, Out] {
  def batch(instance: InstanceId): (Option[State], Chunk[In]) => F[(Option[State], Chunk[Out])]
}

// There is also the FSM.unbatched constructor for handling only one input at a time
object FSM {
  def unbatched[F[_]: Monad, InstanceId, State, In, Out](
    f: InstanceId => (Option[State], In) => F[(Option[State], Chunk[Out])]
  ): FSM[F, InstanceId, State, In, Out] = ...

  ...
}
```

Each automaton in the set is uniquely identified by the `InstanceId`, `FSM.batch` returns the implementation of the automaton for the given id.
The `InstanceId` acts as a shard identifier - the inputs that an automaton with a given `InstanceId` gets are not observed by any other automaton.
The state is also unique to an automaton - there is always a single valid `State` for an `InstanceId`.
The transition of the automaton is described as a function from the current state and new inputs to the new state and outputs.
There might be no current state for an automaton, in which case a None is provided.
The state for an automaton can be removed by returning a None.

By default, the `InstanceId` is derived from the key of incoming kafka records.
It is assumed that records in the input kafka topic are partitioned using the key (e.g. the default kafka partitioner).

### Pipeline

After you have defined FSM, you can run it by using a `Pipeline`.

```scala
trait Pipeline[F[_], InstanceId, State, In, Out] {
  def process(f: FSM[F, InstanceId, State, In, Out]): Stream[F, Unit]
}

```
A pipeline receives records from an input kafka topic, performs the transitions in all the state machines in parallel, and outputs records to one or more topics.
It also emits state snapshots to an internal topic, which is used for distributing and rebuilding the state if an automaton is moved to a new machine. Note that a custom partitioning strategy is used for the internal state topic.

### Features

#### Atomicity
Each transition of an automaton is atomic - comitting to the input topic, producing to the output topics, and emitting state snapshots happens in the same kafka transaction.

#### Exactly once output semantics
As a consequence of the above, records in the output topics are produced exactly once.

#### Ease of distribution/scaling
The distributed features are implemented completely using kafka's consumer groups and key-based partitioning.

Instances of applications using this library can operate in clusters of arbitrary numbers of nodes.
Each automaton will be live in only a single node in the cluster at a time and it can move to another node in cases of partition rebalancing or system failures.

A move happens when a partition of the input topic is assigned to a different host system.

#### Transparency to the user
The distributed features are fairly transparent to the user. The only core logic provided by the end user is simply the description of the automaton. Keeping automatons in only one machine, moving them from one machine to another, keeping and distributing the state is handled by the library.


# Example

In the `examples/voting` directory you will find an example application using this library, it implements a distributed voting system.

The system receives the following commands:

source code [here](examples/voting/src/main/scala/com/stoufexis/fsm/examples/voting/domain/message/VoteCommand.scala)
```scala
/**
 * Input to the votes state machine
 * 
 * Users can open votes on items, close votes on items, upvote items, and downvote items
 */
sealed trait VoteCommand {
  val id:            CommandId
  val itemId:        ItemId
  val correlationId: CorrelationId
  val userId:        UserId
}

object VoteCommand {
  case class VoteStart(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class VoteEnd(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class Upvote(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand

  case class Downvote(id: CommandId, correlationId: CorrelationId, itemId: ItemId, userId: UserId)
      extends VoteCommand
}
```

And responds with vote events and vote state updates to output topics, one for each output type. Note that the state updates are not the snapshots emitted to the internal state topic, this is completely handled by the library and is transparent to the user.

source code [here](examples/voting/src/main/scala/com/stoufexis/fsm/examples/voting/domain/message/VoteEvent.scala)
```scala
sealed trait VoteEvent {
  val eventId: EventId
  val cmd:     VoteCommand
}

object VoteEvent {
  case class CommandExecuted(eventId: EventId, cmd: VoteCommand)                 extends VoteEvent
  case class CommandRejected(eventId: EventId, cmd: VoteCommand, reason: String) extends VoteEvent
}
```

source code [here](examples/voting/src/main/scala/com/stoufexis/fsm/examples/voting/domain/message/VoteEvent.scala)
```scala
/**
  * The current vote count for a given itemId
  */
case class VoteStateUpdate(
  updateId:      UpdateId,
  correlationId: CorrelationId,
  itemId:        ItemId,
  voteCnt:       Int
)
```

The FSM for the vote system is described in VotesFSM.

Notes: 
* Some code is removed for brevity in the snippet.
* `Output` is a helper type that contains either a `VoteEvent` or a `VoteStateUpdate`

source code [here](examples/voting/src/main/scala/com/stoufexis/fsm/examples/voting/fsm/VoteFSM.scala).
```scala
object VoteFSM {

  def apply[F[_]: Monad: FUUIDGen]: FSM[F, ItemId, Votes, VoteCommand, Output] = {
    // Outputs a VoteEvent.CommandRejected and leaves the state untouched
    def reject(
      state:  Option[Votes],
      cmd:    VoteCommand,
      reason: String
    ): F[(Option[Votes], Chunk[Output])] =
      ...

    // Outputs a VoteEvent.CommandExecuted and a VoteEvent.VoteStateUpdate
    // it also passes the provided `state`
    def execute(state: Option[Votes], cmd: VoteCommand): F[(Option[Votes], Chunk[Output])] =
      ...

    def fsm(item: ItemId): (Option[Votes], VoteCommand) => F[(Option[Votes], Chunk[Output])] = {
      // There already exists state for this item, so vote has already started
      case (st @ Some(_), cmd: VoteCommand.VoteStart) =>
        reject(st, cmd, s"Voting has already begun for $item")

      // End the vote for `item`
      case (Some(_), cmd: VoteCommand.VoteEnd) =>
        execute(None, cmd)

      // Start the vote for `item`
      case (None, cmd: VoteCommand.VoteStart) =>
        execute(Some(Votes.empty), cmd)

      // There is no open vote, so we cannot execute anything other than VoteStart
      case (None, cmd) =>
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

    FSM.unbatched(fsm)
  }
}
```

In [App.scala](examples/voting/src/main/scala/com/stoufexis/fsm/examples/voting/App.scala) you will find the wiring for the voting system. A `Pipeline` is configured and `VoteFSM` is passed to it.
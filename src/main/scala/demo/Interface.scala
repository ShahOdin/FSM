package demo

import cats.effect.{Async, Deferred, Ref, Sync}
import cats.syntax.all._

import java.time.Instant

object Interface {

  //here Ref itself takes care of your state
  def withSyncTransition[F[_]: Sync](
      initialState: State
  ): F[DemoInterface[F]] = {
    def modifyStatePerCommand: Command => State => (State, F[Option[Event]]) = {
      case c: Command.Open => {
        case open: State.Open =>
          open -> Log.logNonAction(c) *> none[Event].pure[F]
        case s: State.Close =>
          val now = Instant.now()
          val event: Event = Event.Opened(now)
          State.Open(now) ->
            Log.logAttemptToPublishEvent(s, event, c) *> event.some
              .pure[F]
      }
      case c: Command.Close => {
        case close: State.Close =>
          close -> Log.logNonAction(c) *> none[Event].pure[F]
        case s: State.Open =>
          val now = Instant.now()
          val event: Event = Event.Closed(now)
          State.Close(now) ->
            Log.logAttemptToPublishEvent(s, event, c) *> event.some
              .pure[F]
      }
    }

    Ref
      .of[F, State](initialState)
      .map(ref => SyncFSM(ref, modifyStatePerCommand))
  }

  //with this, we need a Deferred instance to keep track of the async state
  def withAsyncTransition[F[_]: Async](
      initialState: State
  ): F[DemoInterface[F]] = {

    def updateStoreAndPerformSideEffects(
        ref: Ref[F, LocalState[State]],
        fetchRemoteState: FetchRemoteState[F],
        deferred: Fetched[F, State]
    )(
        lastState: State,
        eventToBeGenerated: Event,
        newState: State,
        command: Command
    ): F[Option[Event]] =
      Log.logAttemptToPublishEvent(
        lastState,
        eventToBeGenerated,
        command
      ) *> fetchRemoteState
        .run(newState)
        .flatTap(_ =>
          deferred.complete(newState.asRight) *> ref
            .set(LocalState.Value(newState))
        )
        .onError(e =>
          deferred.complete(e.asLeft).void
        ) *> eventToBeGenerated.some
        .pure[F]

    for {
      store <- Ref.of[F, State](initialState).map(StateStore.apply[F])
      ref <- Ref.of[F, LocalState[State]](LocalState.Value(initialState))
    } yield {

      def modifyStateOnInput: Command => LocalState[State] => (
          LocalState[State],
          F[Option[Event]]
      ) = {
        case openCommand: Command.Open => {
          case open @ LocalState.Value(State.Open(_)) =>
            open -> Log.logNonAction(openCommand) *> none[Event].pure[F]

          case LocalState.Value(s: State.Close) =>
            val now = Instant.now()
            LocalState.Updating(openCommand) ->
              Deferred[F, Either[Throwable, State]].flatMap { deferred =>
                updateStoreAndPerformSideEffects(ref, store, deferred)(
                  lastState = s,
                  eventToBeGenerated = Event.Opened(now),
                  newState = State.Open(now),
                  command = openCommand
                )
              }

          case async @ LocalState.Updating(runningCommand) =>
            async -> Log.raiseErrorForSystemBeingBusy(
              openCommand,
              runningCommand
            )
        }

        case closeCommand: Command.Close => {
          case close @ LocalState.Value(State.Close(_)) =>
            close -> Log.logNonAction(closeCommand) *> none[Event].pure[F]

          case LocalState.Value(s: State.Open) =>
            val now = Instant.now()
            LocalState.Updating(closeCommand) ->
              Deferred[F, Either[Throwable, State]].flatMap { deferred =>
                updateStoreAndPerformSideEffects(ref, store, deferred)(
                  lastState = s,
                  eventToBeGenerated = Event.Closed(now),
                  newState = State.Close(now),
                  closeCommand
                )
              }

          case async @ LocalState.Updating(runningCommand) =>
            async -> Log.raiseErrorForSystemBeingBusy(
              closeCommand,
              runningCommand
            )
        }
      }

      AsyncFSM(ref, modifyStateOnInput)
    }
  }
}

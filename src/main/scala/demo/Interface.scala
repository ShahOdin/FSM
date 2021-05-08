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
        store: StateStore[F]
    )(
        lastState: State,
        eventToBeGenerated: Event,
        newState: State,
        deferred: Deferred[F, Either[Throwable, State]],
        command: Command
    ): F[Option[Event]] =
      Log.logAttemptToPublishEvent(
        lastState,
        eventToBeGenerated,
        command
      ) *> store
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

      def givenDeferredModifyStateOnInput(
          deferred: Deferred[F, Potentially[State]]
      ): Command => LocalState[State] => (
          LocalState[State],
          F[Option[Event]]
      ) = {
        case c: Command.Open => {
          case open @ LocalState.Value(State.Open(_)) =>
            open -> Log.logNonAction(c) *> none[Event].pure[F]

          case LocalState.Value(s: State.Close) =>
            val now = Instant.now()
            LocalState.Updating(
              deferred
            ) -> updateStoreAndPerformSideEffects(ref, store)(
              lastState = s,
              eventToBeGenerated = Event.Opened(now),
              newState = State.Open(now),
              deferred = deferred,
              command = c
            )

          case async: LocalState.Updating[State, F] =>
            async -> Log.raiseErrorForSystemBeingBusy(c)
        }

        case c: Command.Close => {
          case close @ LocalState.Value(State.Close(_)) =>
            close -> Log.logNonAction(c) *> none[Event].pure[F]

          case LocalState.Value(s: State.Open) =>
            val now = Instant.now()
            LocalState.Updating(
              deferred
            ) -> updateStoreAndPerformSideEffects(ref, store)(
              lastState = s,
              eventToBeGenerated = Event.Closed(now),
              newState = State.Close(now),
              deferred = deferred,
              c
            )

          case async: LocalState.Updating[State, F] =>
            async -> Log.raiseErrorForSystemBeingBusy(c)
        }
      }

      AsyncFSM(ref, givenDeferredModifyStateOnInput)
    }
  }
}

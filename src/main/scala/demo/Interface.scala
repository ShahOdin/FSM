package demo

import cats.data.Kleisli
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
      .map(ref => RefBasedFSM(ref, modifyStatePerCommand))
  }

  //with this, we need a Deferred instance to keep track of the async state
  def withAsyncTransition[F[_]: Async](
      initialState: State
  ): F[DemoInterface[F]] = {
    sealed trait AsyncState[S]
    object AsyncState {
      case class Value[S](state: S) extends AsyncState[S]
      case class Updating[S](asyncState: Deferred[F, Either[Throwable, S]])
          extends AsyncState[S]
    }

    def updateStoreAndPerformSideEffects(
        ref: Ref[F, AsyncState[State]],
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
            .set(AsyncState.Value(newState))
        )
        .onError(e =>
          deferred.complete(e.asLeft).void
        ) *> eventToBeGenerated.some
        .pure[F]

    for {
      store <- Ref.of[F, State](initialState).map(StateStore.apply[F])
      ref <- Ref.of[F, AsyncState[State]](AsyncState.Value(initialState))
    } yield {

      def modifyStatePerCommand(
          deferred: Deferred[F, Potentially[State]]
      ): Command => AsyncState[State] => (
          AsyncState[State],
          F[Option[Event]]
      ) = {
        case c: Command.Open => {
          case open @ AsyncState.Value(State.Open(_)) =>
            open -> Log.logNonAction(c) *> none[Event].pure[F]

          case AsyncState.Value(s: State.Close) =>
            val now = Instant.now()
            AsyncState.Updating(
              deferred
            ) -> updateStoreAndPerformSideEffects(ref, store)(
              lastState = s,
              eventToBeGenerated = Event.Opened(now),
              newState = State.Open(now),
              deferred = deferred,
              command = c
            )

          case async: AsyncState.Updating[State] =>
            async -> Log.raiseErrorForSystemBeingBusy(c)
        }

        case c: Command.Close => {
          case close @ AsyncState.Value(State.Close(_)) =>
            close -> Log.logNonAction(c) *> none[Event].pure[F]

          case AsyncState.Value(s: State.Open) =>
            val now = Instant.now()
            AsyncState.Updating(
              deferred
            ) -> updateStoreAndPerformSideEffects(ref, store)(
              lastState = s,
              eventToBeGenerated = Event.Closed(now),
              newState = State.Close(now),
              deferred = deferred,
              c
            )

          case async: AsyncState.Updating[State] =>
            async -> Log.raiseErrorForSystemBeingBusy(c)
        }
      }

      Kleisli.liftF(Deferred[F, Either[Throwable, State]]).flatMap { deferred =>
        RefBasedFSM(ref, modifyStatePerCommand(deferred))
      }
    }
  }
}

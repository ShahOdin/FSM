package demo

import cats.data.Kleisli
import cats.effect.{Async, Sync, Temporal}
import cats.effect.kernel.Ref
import cats.syntax.all._

import scala.concurrent.duration.DurationInt

object UpdateRemoteState {
  def apply[F[_]: Async](ref: Ref[F, State]): UpdateRemoteState[F] = {

    def attemptUpdate(state: State): F[Either[Throwable, Unit]] =
      Sync[F].delay(println(s"attempting to update store: ${state}")) *>
        Log.intermittentlyFailToUpdateRemoteStore(
          state
        ) *> ref
        .set(state)
        .flatTap(_ => Log.logSuccessToUpdateStore(state))
        .void
        .map(_.asRight)

    Kleisli { state =>
      Temporal[F].sleep(500.millisecond) *> attemptUpdate(state)
    }
  }
}

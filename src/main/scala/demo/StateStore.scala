package demo

import cats.data.Kleisli
import cats.effect.{Async, Temporal}
import cats.effect.kernel.Ref
import cats.syntax.all._

import scala.concurrent.duration.DurationInt

object StateStore {
  def apply[F[_]: Async](ref: Ref[F, State]): StateStore[F] =
    Kleisli { state =>
      Temporal[F].sleep(2.seconds) *> ref
        .set(state)
        .void
        .map(_.asRight)
    }
}

package demo

import cats.data.Kleisli
import cats.effect.{Deferred, Ref}
import cats.effect.kernel.Concurrent

object AsyncFSM {
  def apply[F[_]: Concurrent, I, S, O](
      ref: Ref[F, LocalState[S]],
      givenDeferredModifyStateOnInput: Fetched[F, S] => I => LocalState[S] => (
          LocalState[S],
          F[O]
      )
  ): FSM[F, I, O] = {
    Kleisli
      .liftF(Deferred[F, Either[Throwable, S]])
      .flatMap(deferred =>
        SyncFSM(ref, givenDeferredModifyStateOnInput(deferred))
      )
  }
}

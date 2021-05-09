package demo

import cats.effect.Ref
import cats.effect.kernel.Concurrent

//simply a proxy to the call to SyncFSM. just makes it explicit that in the Async case, you need LocalState. no longer has a ref to Deferred.
object AsyncFSM {
  def apply[F[_]: Concurrent, I, S, O](
      ref: Ref[F, LocalState[S]],
      modifyStateOnInput: I => LocalState[S] => (LocalState[S], F[O])
  ): FSM[F, I, O] =
    SyncFSM(
      ref,
      modifyStateOnInput
    )
}

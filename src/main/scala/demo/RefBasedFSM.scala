package demo

import cats.FlatMap
import cats.data.Kleisli
import cats.effect.Ref
import cats.syntax.flatMap._

object RefBasedFSM {
  def apply[F[_]: FlatMap, I, S, O](
      ref: Ref[F, S],
      modifyStateOnInput: I => S => (S, F[O])
  ): FSM[F, I, O] =
    Kleisli(input =>
      ref
        .modify(modifyStateOnInput(input))
        .flatten
    )
}

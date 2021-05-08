package demo

import cats.effect.Deferred

sealed trait LocalState[S]
object LocalState {
  case class Value[S](state: S) extends LocalState[S]
  case class Updating[S, F[_]](asyncState: Deferred[F, Either[Throwable, S]])
      extends LocalState[S]
}

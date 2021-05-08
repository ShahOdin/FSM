package demo

import cats.effect.Deferred

sealed trait AsyncState[S]
object AsyncState {
  case class Value[S](state: S) extends AsyncState[S]
  case class Updating[S, F[_]](asyncState: Deferred[F, Either[Throwable, S]])
      extends AsyncState[S]
}

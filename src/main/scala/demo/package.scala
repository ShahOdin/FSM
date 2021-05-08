import cats.data.Kleisli

package object demo {

  type Potentially[S] = Either[Throwable, S]

  type FSM[F[_], I, O] = Kleisli[F, I, O]

  type DemoInterface[F[_]] = FSM[F, Command, Option[Event]]

  type StateStore[F[_]] = Kleisli[F, State, Potentially[Unit]]

}

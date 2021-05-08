package demo

import cats.effect.{Clock, ExitCode, IO, IOApp, Sync}

import java.time.Instant
import scala.collection.mutable.ListBuffer
import cats.syntax.all._

object Demo extends IOApp {
  val events: ListBuffer[Event] = ListBuffer.empty

  def requestTransitionWithRetry[F[_]: Sync](
      interface: DemoInterface[F],
      command: Command
  ): F[Option[Event]] =
    Sync[F]
      .handleErrorWith(
        interface
          .run(command)
          .onError {
            case e =>
              Log.logRetry(e)
          }
      )(_ => requestTransitionWithRetry(interface, command))
      .flatTap(_.traverse(e => events.append(e).pure[F]))

  override def run(args: List[String]): IO[ExitCode] =
    for {
      now <- Clock[IO].realTime.map(_.toSeconds).map(Instant.ofEpochSecond)
      _ <- IO(println(s"initial state is set at: $now"))
      interface <- Interface.withAsyncTransition[IO](State.Close(now))

      _ <- (
          requestTransitionWithRetry(interface, Command.Open(1)),
          requestTransitionWithRetry(interface, Command.Open(2))
      ).parTupled

      _ <- requestTransitionWithRetry(interface, Command.Open(3))
      _ <- requestTransitionWithRetry(interface, Command.Close(4))
      _ <- requestTransitionWithRetry(interface, Command.Close(5))

      _ <- IO(println(s"events received: ${events.toList.mkString(",")}"))
    } yield ExitCode.Success
}

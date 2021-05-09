package demo

import cats.Applicative
import cats.effect.Sync

import java.time.Instant
import scala.util.Random
import cats.syntax.all._

object Log {

  private def intermittentlyFail[F[_]: Sync, I](
      command: I
  ): Unit => F[Unit] =
    _ =>
      if (Random.nextInt(10) < 7)
        println(
          s"${Console.YELLOW} Failed to log, for command: $command ${Console.RESET}"
        ).pure[F] *> new Exception("Boom!").raiseError[F, Unit]
      else
        Applicative[F].unit

  def logAttemptToPublishEvent[F[_]: Sync, I, S, O](
      lastState: S,
      event: O,
      command: I
  ): F[Unit] =
    Sync[F]
      .handleErrorWith(
        println(
          s"${Console.YELLOW} attempting to publish $event requested by command: $command ${Console.RESET}"
        ).pure[F]
          .flatTap(intermittentlyFail(command))
          .flatTap(_ =>
            println(
              s"${Console.GREEN} succeeded in publishing $event, requested by command $command. Previous state was: $lastState ${Console.RESET}"
            ).pure[F]
          )
      )(_ => logAttemptToPublishEvent(lastState, event, command))

  def logRetry[F[_]: Sync](e: Throwable): F[Unit] =
    Sync[F]
      .delay(
        println(
          s"${Console.CYAN} failed due to: $e retrying request...${Console.RESET}"
        )
      )
      .whenA(cond = false)

  def logNonAction[F[_]: Sync, I](command: I): F[Unit] =
    Sync[F]
      .delay(
        println(
          s"${Console.CYAN} received a command: $command to change state it matched the current state. ignoring...${Console.RESET}"
        )
      )

  def raiseErrorForSystemBeingBusy[F[_]: Sync, I](
      newCommand: I,
      runningCommand: I
  ): F[Option[Event]] =
    new Exception(
      s"currently running command: ${runningCommand}, will retry command: $newCommand later"
    ).raiseError[F, Option[Event]]
}

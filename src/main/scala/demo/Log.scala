package demo

import cats.Applicative
import cats.effect.Sync

import java.time.Instant
import scala.util.Random
import cats.syntax.all._

object Log {

  def logSuccessToUpdateStore[F[_]: Sync](updateTo: State): F[Unit] =
    Sync[F].delay {
      println(
        s"${Console.GREEN} successfully updated store with: $updateTo ${Console.RESET}"
      )
    }

  private def intermittentlyFail[F[_]: Sync](message: String): F[Unit] =
    Sync[F]
      .delay(
        if (Random.nextInt(10) < 7)
          println(message).pure[F] *> new Exception("Boom!").raiseError[F, Unit]
        else
          Applicative[F].unit
      )
      .flatten

  def intermittentlyFailToUpdateRemoteStore[F[_]: Sync, S](
      updateTo: S
  ): F[Unit] = {
    intermittentlyFail(
      s"${Console.RED} Failed to update store with: $updateTo ${Console.RESET}"
    )
  }

  private def intermittentlyFailToAcknowledgeCommand[F[_]: Sync, I](
      command: I
  ): F[Unit] =
    intermittentlyFail(
      s"${Console.YELLOW} Failed to acknowledge command: $command ${Console.RESET}"
    )

  def acknowledgeCommand[F[_]: Sync, I, S](
      lastState: S,
      command: I
  ): F[Unit] =
    Sync[F]
      .handleErrorWith(
        println(
          s"${Console.YELLOW} acknowledging command: $command. last state is: ${lastState} ${Console.RESET}"
        ).pure[F]
          .flatTap(_ =>
            intermittentlyFailToAcknowledgeCommand(command).whenA(false)
          )
          .flatTap(_ =>
            println(
              s"${Console.GREEN} succeeded in processing command $command. Previous state was: $lastState ${Console.RESET}"
            ).pure[F]
          )
      )(_ => acknowledgeCommand(lastState, command))

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
          s"${Console.CYAN} received a command: $command to update the state, but it matched the current state. ignoring...${Console.RESET}"
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

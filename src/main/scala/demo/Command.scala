package demo

sealed trait Command
object Command {
  case class Close(id: Int) extends Command
  case class Open(id: Int) extends Command
}

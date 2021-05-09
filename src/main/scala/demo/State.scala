package demo

import java.time.Instant

sealed trait State extends Serializable with Product
object State {
  case class Open(createdAt: Instant) extends State
  case class Close(at: Instant) extends State
}

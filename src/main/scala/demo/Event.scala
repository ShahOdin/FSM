package demo

import java.time.Instant

sealed trait Event extends Serializable with Product
object Event {
  case class Opened(at: Instant) extends Event
  case class Closed(at: Instant) extends Event
}

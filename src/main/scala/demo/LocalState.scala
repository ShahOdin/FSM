package demo

sealed trait LocalState[+S]
object LocalState {
  case class Value[S](state: S) extends LocalState[S]
  case object Updating extends LocalState[Nothing]
}

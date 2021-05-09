package demo

sealed trait LocalState[+S]
object LocalState {
  case class Value[S](state: S) extends LocalState[S]
  case class Updating[I](command: I) extends LocalState[Nothing]
}

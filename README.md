# Finite State machines with Refs

Assuming this simple interface for our FSM:

```scala
  type FSM[F[_], I, O] = Kleisli[F, I, O]
```

where `I` is the input and `O` is output, we can model it with Cats-effect's Ref as below:

```scala
object RefBasedFSM {
  def apply[F[_]: FlatMap, I, S, O](
      ref: Ref[F, S],
      modifyStateOnInput: I => S => (S, F[O])
  ): FSM[F, I, O] =
    Kleisli(input =>
      ref
        .modify(modifyStateOnInput(input))
        .flatten
    )
}
```

- In the case of our state transitioning being synchronous, that is all there is to it. See [this](https://github.com/ShahOdin/FSM/blob/ea449d86656a4c08420537d996078ac56311f67a/src/main/scala/demo/Interface.scala#L12-L41) for a concrete example.

- In the case of the state transition being asynchronous, ie, if there is a database layer, so for example:

```scala
  type Potentially[S] = Either[Throwable, S]
  type StateStore[F[_]] = Kleisli[F, State, Potentially[Unit]]
```

we can internally introduce this structure:

```scala
    sealed trait AsyncState[S]
    object AsyncState {
      case class Value[S](state: S) extends AsyncState[S]
      case class Updating[S](asyncState: Deferred[F, Either[Throwable, S]])
          extends AsyncState[S]
    }
```

and use an instance of `Deferred` to handle the Async part. See [here](https://github.com/ShahOdin/FSM/blob/ea449d86656a4c08420537d996078ac56311f67a/src/main/scala/demo/Interface.scala#L43-L135) for a concrete example. 


# Next steps

The sync impl, simply wants a `I => S => (S, F[O])` to work, which is intuitive enough. In the case of Async impl however, as it stands, the usage of deferred is left to the developer and not managed by the code. Would be nice to come up with an abstraction that would manage that for us.


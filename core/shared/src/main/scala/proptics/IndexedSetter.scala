package proptics

import proptics.internal.Indexed

import scala.Function.const

/**
 * An [[IndexedSetter_]] is an indexed optic. A generalization of indexed fmap.
 *
 * @tparam S the source of an [[IndexedSetter_]]
 * @tparam I the index of an [[IndexedSetter_]]
 * @tparam T the modified source of an [[IndexedSetter_]]
 * @tparam A the target of an [[IndexedSetter_]]
 * @tparam B the modified target of an [[IndexedSetter_]]
 */
abstract class IndexedSetter_[I, S, T, A, B] extends Serializable { self =>
  private[proptics] def apply(indexed: Indexed[* => *, I, A, B]): S => T

  def set(b: B): S => T = over(const(b))

  def over(f: ((I, A)) => B): S => T = self(Indexed(f))
}

object IndexedSetter_ {
  private[proptics] def apply[I, S, T, A, B](f: Indexed[* => *, I, A, B] => S => T): IndexedSetter_[I, S, T, A, B] = new IndexedSetter_[I, S, T, A, B] {
    override def apply(indexed: Indexed[* => *, I, A, B]): S => T = f(indexed)
  }

  def apply[I, S, T, A, B](get: ((I, A) => B) => S => T)(implicit ev: DummyImplicit): IndexedSetter_[I, S, T, A, B] =
    IndexedSetter_((indexed: Indexed[* => *, I, A, B]) => {
      get { case (i, a) => indexed.runIndex(i, a) }
    })
}

object IndexedSetter {
  def apply[I, S, A](get: ((I, A) => A) => S => S): IndexedSetter[I, S, A] =
    IndexedSetter_((indexed: Indexed[* => *, I, A, A]) => get { case (i, a) => indexed.runIndex(i, a) })
}
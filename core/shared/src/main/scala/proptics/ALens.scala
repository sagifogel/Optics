package proptics

import cats.instances.function._
import cats.mtl.MonadState
import cats.syntax.apply._
import cats.syntax.eq._
import cats.syntax.option._
import cats.{Applicative, Eq, Functor, Id, Monoid}
import proptics.internal.{Forget, RunBazaar, Shop, Wander}
import proptics.rank2types.Traversing

import scala.Function.const

/**
  * A [[Lens_]] with fixed type [[Shop]] [[cats.arrow.Profunctor]]
  *
  * @tparam S the source of a [[ALens_]]
  * @tparam T the modified source of a [[ALens_]]
  * @tparam A the focus of a [[ALens_]]
  * @tparam B the modified focus of a [[ALens_]]
  */
abstract class ALens_[S, T, A, B] { self =>
  def apply(shop: Shop[A, B, A, B]): Shop[A, B, S, T]

  /** view the focus of a [[ALens_]] */
  def view(s: S): A = self(Shop(identity, const(identity))).get(s)

  /** set the modified focus of a [[ALens_]] */
  def set(b: B): S => T = over(const(b))

  /** modify the focus type of a [[ALens_]] using a function, resulting in a change of type to the full structure  */
  def over(f: A => B): S => T = s => overF[Id](f)(s)

  /** synonym for [[traverse]], flipped */
  def overF[F[_]: Functor](f: A => F[B])(s: S): F[T] = traverse(s)(f)

  /** modify the focus type of a [[ALens_]] using a [[cats.Functor]], resulting in a change of type to the full structure  */
  def traverse[F[_]: Functor](s: S)(f: A => F[B])(implicit ev: Functor[F]): F[T] = {
    val shop = self(Shop(identity, const(identity)))

    ev.map(f(shop.get(s)))(shop.set(s))
  }

  /** tests whether a predicate holds for the focus of a [[ALens_]] */
  def exists(f: A => Boolean): S => Boolean = f compose view

  /** tests whether a predicate does not hold for the focus of a [[ALens_]] */
  def noExists(f: A => Boolean): S => Boolean = s => !exists(f)(s)

  /** tests whether the focus of a [[ALens_]] contains a given value */
  def contains(s: S)(a: A)(implicit ev: Eq[A]): Boolean = exists(_ === a)(s)

  /** tests whether the focus a [[ALens_]] does not contain a given value */
  def notContains(s: S)(a: A)(implicit ev: Eq[A]): Boolean = !contains(s)(a)

  /** finds if the focus of a [[ALens_]] is satisfying a predicate. */
  def find(f: A => Boolean): S => Option[A] = s => view(s).some.filter(f)

  /** view the focus of an [[ALens_]] in the state of a monad */
  def use[M[_]](implicit ev: MonadState[M, S]): M[A] = ev.inspect(view)

  /** convert an [[ALens_]] to the pair of functions that characterize it */
  def withLens[R](f: (S => A) => (S => B => T) => R): R = {
    val shop = toShop

    f(shop.get)(shop.set)
  }

  /** transforms an [[ALens_]] to a [[Lens_]] */
  def asLens: Lens_[S, T, A, B] = withLens(Lens_[S, T, A, B])

  /**
    * Converts a [[ALens_]] into the form that [[Lens_]] accepts.
    *
    * Can be useful when defining a lens where the focus appears under multiple
    * constructors of an algebraic data type. This function would be called for
    * each case of the data type.
    */
  def lensStore(s: S): (A, B => T) = withLens(sa => sbt => (sa, sbt).mapN(Tuple2.apply))(s)

  /** compose [[ALens_]] with an [[Iso_]] */
  def compose[C, D](other: Iso_[A, B, C, D]): ALens_[S, T, C, D] = new ALens_[S, T, C, D] {
    override def apply(shop: Shop[C, D, C, D]): Shop[C, D, S, T] =
      self(Shop(identity, const(identity))) compose other(shop)
  }

  /** compose [[ALens_]] with an [[AnIso_]] */
  def compose[C, D](other: AnIso_[A, B, C, D]): ALens_[S, T, C, D] = self compose other.asIso

  /** compose [[ALens_]] with an [[Lens_]] */
  def compose[C, D](other: Lens_[A, B, C, D]): ALens_[S, T, C, D] = new ALens_[S, T, C, D] {
    override def apply(shop: Shop[C, D, C, D]): Shop[C, D, S, T] =
      self(Shop(identity, const(identity))) compose other(shop)
  }

  /** compose [[ALens_]] with an [[ALens_]] */
  def compose[C, D](other: ALens_[A, B, C, D]): ALens_[S, T, C, D] = new ALens_[S, T, C, D] {
    override def apply(shop: Shop[C, D, C, D]): Shop[C, D, S, T] =
      self(Shop(identity, const(identity))) compose other(shop)
  }

  /** compose [[ALens_]] with an [[Prism_]] */
  def compose[C, D](other: Prism_[A, B, C, D]): Traversal_[S, T, C, D] = new Traversal_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Wander[P]): P[S, T] = {
      val traversing = new Traversing[S, T, C, D] {
        override def apply[F[_]](f: C => F[D])(s: S)(implicit ev: Applicative[F]): F[T] =
          self.traverse(s)(other.traverse(_)(f))
      }

      ev.wander(traversing)(pab)
    }
  }

  /** compose [[ALens_]] with an [[APrism_]] */
  def compose[C, D](other: APrism_[A, B, C, D]): Traversal_[S, T, C, D] = self compose other.asPrism

  /** compose [[ALens_]] with an [[Traversal_]] */
  def compose[C, D](other: Traversal_[A, B, C, D]): Traversal_[S, T, C, D] = new Traversal_[S, T, C, D] {
    override private[proptics] def apply[P[_, _]](pab: P[C, D])(implicit ev: Wander[P]) = {
      val traversing = new Traversing[S, T, C, D] {
        override def apply[F[_]](f: C => F[D])(s: S)(implicit ev: Applicative[F]): F[T] =
          self.traverse(s)(other.traverse(_)(f))
      }

      ev.wander(traversing)(pab)
    }
  }

  /** compose [[ALens_]] with an [[ATraversal_]] */
  def compose[C, D](other: ATraversal_[A, B, C, D]): ATraversal_[S, T, C, D] =
    ATraversal_(new RunBazaar[* => *, C, D, S, T] {
      override def apply[F[_]](pafb: C => F[D])(s: S)(implicit ev: Applicative[F]): F[T] =
        self.traverse(s)(other.traverse(_)(pafb))
    })

  /** compose [[ALens_]] with an [[Setter_ */
  def compose[C, D](other: Setter_[A, B, C, D]): Setter_[S, T, C, D] = new Setter_[S, T, C, D] {
    override private[proptics] def apply(pab: C => D): S => T = s => {
      val shop = toShop

      shop.set(s)(other(pab)(shop.get(s)))
    }
  }

  /** compose [[ALens_]] with an [[Getter_]] */
  def compose[C, D](other: Getter_[A, B, C, D]): Getter_[S, T, C, D] = new Getter_[S, T, C, D] {
    override private[proptics] def apply(forget: Forget[C, C, D]): Forget[C, S, T] =
      Forget(forget.runForget compose other.view compose self.view)
  }

  /** compose [[ALens_]] with an [[Fold_]] */
  def compose[C, D](other: Fold_[A, B, C, D]): Fold_[S, T, C, D] = new Fold_[S, T, C, D] {
    override def apply[R: Monoid](forget: Forget[R, C, D]): Forget[R, S, T] =
      Forget(s => other.foldMap(self.view(s))(forget.runForget))
  }

  private[this] def toShop: Shop[A, B, S, T] = self(Shop(identity, const(identity)))
}

object ALens_ {

  /** create a polymorphic [[ALens_]] from Rank2TypeLensLike encoding */
  private[proptics] def apply[S, T, A, B](f: Shop[A, B, A, B] => Shop[A, B, S, T]): ALens_[S, T, A, B] = new ALens_[S, T, A, B] { self =>
    override def apply(shop: Shop[A, B, A, B]): Shop[A, B, S, T] = f(shop)
  }

  /** create a polymorphic [[ALens_]] from a getter/setter pair */
  def apply[S, T, A, B](get: S => A)(set: S => B => T): ALens_[S, T, A, B] =
    ALens_ { shop =>
      Shop(
        shop.get compose get,
        s =>
          b => {
            val a = get(s)

            set(s)(shop.set(a)(b))
          })
    }
}

object ALens {

  /** create a monomorphic [[ALens]] from a getter/setter pair */
  def apply[S, A](get: S => A)(set: S => A => S): ALens[S, A] = ALens_(get)(set)
}

package proptics

import cats.arrow.{Profunctor, Strong}
import cats.instances.either._
import cats.mtl.MonadState
import cats.syntax.eq._
import cats.syntax.option._
import cats.{Applicative, Comonad, Eq, Functor, Id, Monoid}
import proptics.internal._
import proptics.profunctor.{Choice, Closed}

import scala.Function.const

/**
  * An Iso with fixed type [[Exchange]] [[cats.arrow.Profunctor]]
  *
  * @tparam S the source of a [[AnIso_]]
  * @tparam T the modified source of an [[AnIso_]]
  * @tparam A the focus of an [[AnIso_]]
  * @tparam B the modified focus of an [[AnIso_]]
  */
abstract class AnIso_[S, T, A, B] { self =>
  private[proptics] def apply(exchange: Exchange[A, B, A, B]): Exchange[A, B, S, T]

  /** view the focus of an [[AnIso_]] */
  def view(s: S): A = self(Exchange(identity, identity)).view(s)

  /** view the modified source of an [[AnIso_]] */
  def review(b: B): T

  /** set the modified focus of an [[AnIso_]] */
  def set(b: B): S => T = over(const(b))

  /** modify the focus type of an [[AnIso_]] using a function, resulting in a change of type to the full structure  */
  def over(f: A => B): S => T = overF[Id](f)

  /** synonym for [[traverse]], flipped */
  def overF[F[_]: Applicative](f: A => F[B])(s: S): F[T] = traverse(s)(f)

  /** modify the focus type of an [[AnIso_]] using a [[cats.Functor]], resulting in a change of type to the full structure  */
  def traverse[F[_]](s: S)(f: A => F[B])(implicit ev: Applicative[F]): F[T] = ev.map(f(view(s)))(set(_)(s))

  /** finds if the focus of an [[AnIso_]] is satisfying a predicate. */
  def filter(f: A => Boolean): S => Option[A] = s => view(s).some.filter(f)

  /** tests whether a predicate holds for the focus of an [[AnIso_]] */
  def exists(f: A => Boolean): S => Boolean = f compose view

  /** tests whether a predicate does not hold for the focus of an [[AnIso_]] */
  def noExists(f: A => Boolean): S => Boolean = s => !exists(f)(s)

  /** tests whether the focus contains a given value */
  def contains(s: S)(a: A)(implicit ev: Eq[A]): Boolean = exists(_ === a)(s)

  /** tests whether the focus does not contain a given value */
  def notContains(s: S)(a: A)(implicit ev: Eq[A]): Boolean = !contains(s)(a)

  /** transforms an [[AndIso_]] to an [[Iso_]] */
  def asIso: Iso_[S, T, A, B] = self.withIso(Iso_[S, T, A, B])

  /** convert an [[AndIso_]] to the pair of functions that characterize it */
  def withIso[P[_, _], R](f: (S => A) => (B => T) => R): R = {
    val exchange = self.apply(Exchange(identity, identity))

    f(exchange.view)(exchange.review)
  }

  /** based on  [[newtype.Newtype.ala]] */
  def au[P[_, _], E](f: (B => T) => E => S): E => A = withIso(sa => bt => e => sa(f(bt)(e)))

  /** based on [[newtype.Newtype.alaF]] */
  def auf[P[_, _], E, R](f: P[R, A] => E => B)(g: P[R, S])(implicit ev: Profunctor[P]): E => T =
    withIso(sa => bt => e => bt(f(ev.rmap(g)(sa))(e)))

  /** the opposite of working over a [[AnIso_.set]] is working under an isomorphism */
  def under[P[_, _]](f: T => S): B => A = withIso(sa => bt => sa compose f compose bt)

  /** lift an [[Iso_]] into an arbitrary Functor. */
  def mapping[P[_, _], F[_], G[_]](implicit ev0: Functor[F], ev1: Functor[G]): Iso_[F[S], G[T], F[A], G[B]] =
    withIso(sa => bt => Iso_(ev0.lift(sa))(ev1.lift(bt)))

  /** lift two [[Iso_]] instances into both arguments of a Profunctor simultaneously. */
  def dimapping[P[_, _], Q[_, _], SS, TT, AA, BB](
      that: AnIso_[SS, TT, AA, BB])(implicit ev0: Profunctor[P], ev1: Profunctor[Q]): Iso_[P[A, SS], Q[B, TT], P[S, AA], Q[T, BB]] =
    withIso[P, Iso_[P[A, SS], Q[B, TT], P[S, AA], Q[T, BB]]] { sa => bt =>
      that.withIso[Q, Iso_[P[A, SS], Q[B, TT], P[S, AA], Q[T, BB]]] { ssaa => bbtt =>
        Iso_.iso[P[A, SS], Q[B, TT], P[S, AA], Q[T, BB]](ev0.dimap(_)(sa)(ssaa))(ev1.dimap(_)(bt)(bbtt))
      }
    }

  /** view the focus of a [[Lens_]] in the state of a monad */
  def use[M[_]](implicit ev: MonadState[M, S]): M[A] = ev.inspect(view)

  /** modify an effectful focus of an [[AnIso_]] to the type of the modified focus, resulting in a change of type to the full structure  */
  def cotraverse[F[_]](fs: F[S])(f: F[A] => B)(implicit ev: Applicative[F]): T = {
    val exchange = self(Exchange(identity, identity))

    exchange.review(f(ev.map(fs)(exchange.view)))
  }

  /** synonym for [[cotraverse]], flipped */
  def zipWithF[F[_]: Comonad: Applicative](f: F[A] => B)(fs: F[S]): T = cotraverse(fs)(f)

  /** reverses an [[AnIso_]] by swapping the source and the focus */
  def reverse: AnIso_[B, A, T, S] = new AnIso_[B, A, T, S] {
    override private[proptics] def apply(exchange: Exchange[T, S, T, S]): Exchange[T, S, B, A] =
      Exchange[T, S, B, A](self.review, self.view)

    override def review(s: S): A = self.view(s)
  }

  /** compose [[AnIso_]] with an [[Iso_]] */
  def compose[C, D](other: Iso_[A, B, C, D]): AnIso_[S, T, C, D] = new AnIso_[S, T, C, D] {
    override private[proptics] def apply(exchange: Exchange[C, D, C, D]) =
      self(Exchange(identity, identity)) compose other(exchange)

    override def review(d: D): T = self.review(other.review(d))
  }

  /** compose [[AnIso_]] with an [[AnIso_]] */
  def compose[C, D](other: AnIso_[A, B, C, D]): AnIso_[S, T, C, D] = new AnIso_[S, T, C, D] {
    override private[proptics] def apply(exchange: Exchange[C, D, C, D]): Exchange[C, D, S, T] =
      self(Exchange(identity, identity)) compose other(exchange)

    override def review(d: D): T = self.review(other.review(d))
  }

  /** compose [[AnIso_]] with a [[Lens_]] */
  def compose[C, D](other: Lens_[A, B, C, D]): Lens_[S, T, C, D] = new Lens_[S, T, C, D] {
    override private[proptics] def apply[P[_, _]](pab: P[C, D])(implicit ev: Strong[P]): P[S, T] =
      dimapExchange[P](other(pab))
  }

  /** compose [[AnIso_]] with an [[ALens_]] */
  def compose[C, D](other: ALens_[A, B, C, D]): ALens_[S, T, C, D] = new ALens_[S, T, C, D] {
    override def apply(shop: Shop[C, D, C, D]): Shop[C, D, S, T] =
      Shop(shop.get compose other.view compose self.view, s => d => self.traverse[Id](s)(other(shop).set(_)(d)))
  }

  /** compose [[AnIso_]] with a [[Prism_]] */
  def compose[C, D](other: Prism_[A, B, C, D]): Prism_[S, T, C, D] = new Prism_[S, T, C, D] {
    override private[proptics] def apply[P[_, _]](pab: P[C, D])(implicit ev: Choice[P]): P[S, T] =
      dimapExchange[P](other(pab))
  }

  /** compose [[AnIso_]] with an [[APrism_]] */
  def compose[C, D](other: APrism_[A, B, C, D]): APrism_[S, T, C, D] = new APrism_[S, T, C, D] {
    override private[proptics] def apply(market: Market[C, D, C, D]): Market[C, D, S, T] = {
      val exchange = self(Exchange(identity, identity))
      val marketFromExchange = Market(exchange.review, Right[T, A] _ compose exchange.view)

      marketFromExchange compose other(market)
    }

    override def traverse[F[_]](s: S)(f: C => F[D])(implicit ev: Applicative[F]): F[T] =
      self.traverse(s)(other.traverse(_)(f))
  }

  /** compose [[AnIso_]] with a [[Traversal_]] */
  def compose[C, D](other: Traversal_[A, B, C, D]): Traversal_[S, T, C, D] = new Traversal_[S, T, C, D] {
    override private[proptics] def apply[P[_, _]](pab: P[C, D])(implicit ev: Wander[P]) = dimapExchange[P](other(pab))
  }

  /** compose [[AnIso_]] with an [[ATraversal_]] */
  def compose[C, D](other: ATraversal_[A, B, C, D]): ATraversal_[S, T, C, D] =
    ATraversal_(new RunBazaar[* => *, C, D, S, T] {
      override def apply[F[_]](pafb: C => F[D])(s: S)(implicit ev: Applicative[F]): F[T] =
        self.traverse(s)(other.traverse(_)(pafb))
    })

  /** compose [[AnIso_]] with a [[Setter_]] */
  def compose[C, D](other: Setter_[A, B, C, D]): Setter_[S, T, C, D] = new Setter_[S, T, C, D] {
    override private[proptics] def apply(pab: C => D): S => T = self.over(other(pab))
  }

  /** compose [[AnIso_]] with a [[Getter_]] */
  def compose[C, D](other: Getter_[A, B, C, D]): Getter_[S, T, C, D] = new Getter_[S, T, C, D] {
    override private[proptics] def apply(forget: Forget[C, C, D]): Forget[C, S, T] =
      Forget(forget.runForget compose other.view compose self.view)
  }

  /** compose [[AnIso_]] with a [[Fold_]] */
  def compose[C, D](other: Fold_[A, B, C, D]): Fold_[S, T, C, D] = new Fold_[S, T, C, D] {
    override def apply[R: Monoid](forget: Forget[R, C, D]): Forget[R, S, T] =
      Forget(s => other.foldMap(self.view(s))(forget.runForget))
  }

  /** compose [[AnIso_]] with a [[Grate_]] */
  def compose[C, D](other: Grate_[A, B, C, D]): Grate_[S, T, C, D] = new Grate_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Closed[P]): P[S, T] = dimapExchange[P](other(pab))
  }

  /** compose [[AnIso_]] with a [[Review_]] */
  def compose[C, D](other: Review_[A, B, C, D]): Review_[S, T, C, D] = new Review_[S, T, C, D] { that =>
    override private[proptics] def apply(tagged: Tagged[C, D]): Tagged[S, T] = {
      val exchange = self(Exchange(identity, identity))

      Tagged(exchange.review(other.review(tagged.runTag)))
    }
  }

  private[this] def dimapExchange[P[_, _]](pab: P[A, B])(implicit ev: Profunctor[P]): P[S, T] = {
    val exchange = self(Exchange(identity, identity))

    ev.dimap[A, B, S, T](pab)(exchange.view)(exchange.review)
  }
}

object AnIso_ {

  /** create a polymorphic [[AnIso_]] from an Iso encoded in Exchange */
  private[proptics] def apply[S, T, A, B](f: Exchange[A, B, A, B] => Exchange[A, B, S, T]): AnIso_[S, T, A, B] = new AnIso_[S, T, A, B] { self =>
    override def apply(exchange: Exchange[A, B, A, B]): Exchange[A, B, S, T] = f(exchange)

    override def review(b: B): T = f(Exchange(identity, identity)).review(b)
  }

  /** create a polymorphic [[AnIso_]] from view/review pair */
  def apply[S, T, A, B](view: S => A)(review: B => T): AnIso_[S, T, A, B] =
    AnIso_((ex: Exchange[A, B, A, B]) => Exchange(ex.view compose view, review compose ex.review))
}

object AnIso {

  /** create a monomorphic [[AnIso]] from view/review pair */
  def apply[S, A](view: S => A)(review: A => S): AnIso[S, A] = AnIso_(view)(review)
}

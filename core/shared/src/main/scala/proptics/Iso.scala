package proptics

import scala.Function.const

import cats.arrow.{Profunctor, Strong}
import cats.data.State
import cats.syntax.bifunctor._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.option._
import cats.{Applicative, Bifunctor, Contravariant, Eq, Functor, Monoid}

import proptics.internal.Forget._
import proptics.internal._
import proptics.profunctor.{Choice, Closed, Costar, Wander}
import proptics.rank2types._
import proptics.syntax.costar._

/** An [[Iso_]] is a generalized isomorphism.
  *
  * An [[Iso_]] is a complete reversible transformation between two types.
  *
  * @tparam S the source of an [[Iso_]]
  * @tparam T the modified source of an [[Iso_]]
  * @tparam A the focus of an [[Iso_]]
  * @tparam B the modified focus of a [[Iso_]]
  */
abstract class Iso_[S, T, A, B] extends Serializable { self =>
  private[proptics] def apply[P[_, _]](pab: P[A, B])(implicit ev: Profunctor[P]): P[S, T]

  /** view the focus of an [[Iso_]] */
  final def view(s: S): A =
    self[Forget[A, *, *]](Forget(identity[A]))(profunctorForget[A]).runForget(s)

  /** view the modified source of an [[Iso_]] */
  final def review(b: B): T = self(Tagged[A, B](b))(Tagged.profunctorTagged).runTag

  /** set the modified focus of an [[Iso_]] */
  final def set(b: B): S => T = over(const(b))

  /** modify the focus type of an [[Iso_]] using a function, resulting in a change of type to the full structure */
  final def over(f: A => B): S => T = self(f)

  /** synonym for [[traverse]], flipped */
  final def overF[F[_]: Applicative](f: A => F[B])(s: S): F[T] = traverse(s)(f)

  /** modify the focus type of an [[Iso_]] using a [[cats.Functor]], resulting in a change of type to the full structure */
  def traverse[F[_]](s: S)(f: A => F[B])(implicit ev: Applicative[F]): F[T] = ev.map(f(self.view(s)))(self.set(_)(s))

  /** test whether a predicate holds for the focus of an [[Iso_]] */
  final def exists(f: A => Boolean): S => Boolean = f compose view

  /** test whether a predicate does not hold for the focus of an [[Iso_]] */
  final def notExists(f: A => Boolean): S => Boolean = s => !exists(f)(s)

  /** test whether the focus contains a given value */
  final def contains(a: A)(s: S)(implicit ev: Eq[A]): Boolean = exists(_ === a)(s)

  /** test whether the focus does not contain a given value */
  final def notContains(a: A)(s: S)(implicit ev: Eq[A]): Boolean = !contains(a)(s)

  /** find if the focus of an [[Iso_]] is satisfying a predicate. */
  final def find(f: A => Boolean): S => Option[A] = s => view(s).some.filter(f)

  /** view the focus of a [[Iso_]] in the state of a monad */
  final def use(implicit ev: State[S, A]): State[S, A] = ev.inspect(view)

  /** zip two sources of an [[Iso_]] together provided a binary operation which modify the focus type of an [[Iso_]] */
  final def zipWith[F[_]](s1: S, s2: S)(f: (A, A) => B): T = self(Zipping(f.curried))(Zipping.profunctorZipping).runZipping(s1)(s2)

  /** modify an effectful focus of an [[Iso_]] to the type of the modified focus, resulting in a change of type to the full structure */
  final def cotraverse[F[_]](fs: F[S])(f: F[A] => B)(implicit ev: Applicative[F]): T =
    self(Costar(f))(Costar.profunctorCostar[F](ev)).runCostar(fs)

  /** synonym for [[cotraverse]], flipped */
  final def zipWithF[F[_]: Applicative](f: F[A] => B)(fs: F[S]): T = cotraverse(fs)(f)

  /** reverse an [[Iso_]] by swapping the source and the focus */
  final def reverse: Iso_[B, A, T, S] = new Iso_[B, A, T, S] {
    override def apply[P[_, _]](pab: P[T, S])(implicit ev: Profunctor[P]): P[B, A] =
      self(Re(identity[P[B, A]])).runRe(pab)
  }

  /** compose an [[Iso_]] with a function lifted to a [[Getter_]] */
  final def to[C, D](f: A => C): Getter_[S, T, C, D] = compose(Getter_[A, B, C, D](f))

  /** compose an [[Iso_]] with an [[Iso_]] */
  final def compose[C, D](other: Iso_[A, B, C, D]): Iso_[S, T, C, D] = new Iso_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Profunctor[P]): P[S, T] = self(other(pab))
  }

  /** compose an [[Iso_]] with an [[AnIso_]] */
  final def compose[C, D](other: AnIso_[A, B, C, D]): AnIso_[S, T, C, D] = new AnIso_[S, T, C, D] {
    override private[proptics] def apply(exchange: Exchange[C, D, C, D]): Exchange[C, D, S, T] = self(other(exchange))

    override def review(d: D): T = self.review(other.review(d))
  }

  /** compose an [[Iso_]] with a [[Lens_]] */
  final def compose[C, D](other: Lens_[A, B, C, D]): Lens_[S, T, C, D] = new Lens_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Strong[P]): P[S, T] = self(other(pab))
  }

  /** compose an [[Iso_]] with an [[ALens_]] */
  final def compose[C, D](other: ALens_[A, B, C, D]): ALens_[S, T, C, D] = new ALens_[S, T, C, D] {
    override def apply(shop: Shop[C, D, C, D]): Shop[C, D, S, T] = self(other(shop))
  }

  /** compose an [[Iso_]] with a [[Prism_]] */
  final def compose[C, D](other: Prism_[A, B, C, D]): Prism_[S, T, C, D] = new Prism_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Choice[P]): P[S, T] = self(other(pab))

    /** view the focus of a [[Prism_]] or return the modified source of a [[Prism_]] */
    override def viewOrModify(s: S): Either[T, C] = other.viewOrModify(self.view(s)).leftMap(self.set(_)(s))
  }

  /** compose an [[Iso_]] with an [[APrism_]] */
  final def compose[C, D](other: APrism_[A, B, C, D]): APrism_[S, T, C, D] = new APrism_[S, T, C, D] {
    override private[proptics] def apply(market: Market[C, D, C, D]): Market[C, D, S, T] = self(other(market))

    override def traverse[F[_]](s: S)(f: C => F[D])(implicit ev: Applicative[F]): F[T] = {
      val market = self(other(Market[C, D, C, D](_.asRight[D], identity)))

      market.viewOrModify(s).fold(ev.pure, c => ev.map(f(c))(market.review))
    }
  }

  /** compose an [[Iso_]] with an [[AffineTraversal_]] */
  final def compose[C, D](other: AffineTraversal_[A, B, C, D]): AffineTraversal_[S, T, C, D] = new AffineTraversal_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev0: Choice[P], ev1: Strong[P]): P[S, T] = self(other(pab))(ev1)

    /** view the focus of an [[AffineTraversal_]] or return the modified source of an [[AffineTraversal_]] */
    override def viewOrModify(s: S): Either[T, C] = other.viewOrModify(self.view(s)).leftMap(self.set(_)(s))
  }

  /** compose an [[Iso_]] with an [[AnAffineTraversal_]] */
  final def compose[C, D](other: AnAffineTraversal_[A, B, C, D]): AnAffineTraversal_[S, T, C, D] =
    AnAffineTraversal_ { s: S =>
      other.viewOrModify(self.view(s)).leftMap(self.set(_)(s))
    }(s => d => self.over(other.set(d))(s))

  /** compose an [[Iso_]] with a [[Traversal_]] */
  final def compose[C, D](other: Traversal_[A, B, C, D]): Traversal_[S, T, C, D] = new Traversal_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Wander[P]): P[S, T] = self(other(pab))
  }

  /** compose an [[Iso_]] with an [[ATraversal_]] */
  final def compose[C, D](other: ATraversal_[A, B, C, D]): ATraversal_[S, T, C, D] =
    ATraversal_(new RunBazaar[* => *, C, D, S, T] {
      override def apply[F[_]](pafb: C => F[D])(s: S)(implicit ev: Applicative[F]): F[T] =
        self.traverse(s)(other.traverse(_)(pafb))
    })

  /** compose an [[Iso_]] with a [[Setter_]] */
  final def compose[C, D](other: Setter_[A, B, C, D]): Setter_[S, T, C, D] = new Setter_[S, T, C, D] {
    override private[proptics] def apply(pab: C => D): S => T = self(other(pab))
  }

  /** compose an [[Iso_]] with a [[Getter_]] */
  final def compose[C, D](other: Getter_[A, B, C, D]): Getter_[S, T, C, D] = new Getter_[S, T, C, D] {
    override private[proptics] def apply(forget: Forget[C, C, D]): Forget[C, S, T] =
      self(other(Forget(identity)))(profunctorForget[C])
  }

  /** compose an [[Iso_]] with a [[Fold_]] */
  final def compose[C, D](other: Fold_[A, B, C, D]): Fold_[S, T, C, D] = new Fold_[S, T, C, D] {
    override def apply[R: Monoid](forget: Forget[R, C, D]): Forget[R, S, T] = self(other(forget))(Forget.wanderForget)
  }

  /** compose an [[Iso_]] with a [[Grate_]] */
  final def compose[C, D](other: Grate_[A, B, C, D]): Grate_[S, T, C, D] = new Grate_[S, T, C, D] {
    override def apply[P[_, _]](pab: P[C, D])(implicit ev: Closed[P]): P[S, T] = self(other(pab))
  }

  /** compose an [[Iso_]] with a [[Review_]] */
  final def compose[C, D](other: Review_[A, B, C, D]): Review_[S, T, C, D] = new Review_[S, T, C, D] {
    override private[proptics] def apply(tagged: Tagged[C, D]): Tagged[S, T] = self(other(tagged))(Tagged.choiceTagged)
  }

  /** compose an [[Iso_]] with an [[IndexedLens_]] */
  final def compose[I, C, D](other: IndexedLens_[I, A, B, C, D]): IndexedLens_[I, S, T, C, D] =
    IndexedLens_[I, S, T, C, D]((s: S) => other.view(self.view(s)))(s => d => self.set(other.set(d)(self.view(s)))(s))

  /** compose an [[Iso_]] with an [[AnIndexedLens_]] */
  final def compose[I, C, D](other: AnIndexedLens_[I, A, B, C, D]): AnIndexedLens_[I, S, T, C, D] =
    AnIndexedLens_[I, S, T, C, D]((s: S) => other.view(self.view(s)))(s => d => self.set(other.set(d)(self.view(s)))(s))

  /** compose an [[Iso_]] with an [[IndexedTraversal_]] */
  final def compose[I, C, D](other: IndexedTraversal_[I, A, B, C, D]): IndexedTraversal_[I, S, T, C, D] =
    IndexedTraversal_.wander(new LensLikeWithIndex[I, S, T, C, D] {
      override def apply[F[_]](f: ((C, I)) => F[D])(implicit ev: Applicative[F]): S => F[T] =
        self.traverse(_)(other.traverse(_)(f))
    })

  /** compose an [[Iso_]] with an [[IndexedFold_]] */
  final def compose[I, C, D](other: IndexedSetter_[I, A, B, C, D]): IndexedSetter_[I, S, T, C, D] = new IndexedSetter_[I, S, T, C, D] {
    override private[proptics] def apply(indexed: Indexed[* => *, I, C, D]): S => T = s => self.set(other.over(indexed.runIndex)(self.view(s)))(s)
  }

  /** compose an [[Iso_]] with an [[IndexedGetter_]] */
  final def compose[I, C, D](other: IndexedGetter_[I, A, B, C, D]): IndexedFold_[I, S, T, C, D] = new IndexedFold_[I, S, T, C, D] {
    override private[proptics] def apply[R: Monoid](indexed: Indexed[Forget[R, *, *], I, C, D]): Forget[R, S, T] =
      Forget(indexed.runIndex.runForget compose other.view compose self.view)
  }

  /** compose an [[Iso_]] with an [[IndexedFold]] */
  final def compose[I, C, D](other: IndexedFold_[I, A, B, C, D]): IndexedFold_[I, S, T, C, D] = new IndexedFold_[I, S, T, C, D] {
    override private[proptics] def apply[R: Monoid](indexed: Indexed[Forget[R, *, *], I, C, D]): Forget[R, S, T] =
      Forget(s => other.foldMap(self.view(s))(indexed.runIndex.runForget))
  }
}

object Iso_ {
  /** create a polymorphic [[Iso_]] from Rank2TypeIsoLike encoding */
  private[proptics] def apply[S, T, A, B](f: Rank2TypeIsoLike[S, T, A, B]): Iso_[S, T, A, B] = new Iso_[S, T, A, B] {
    override def apply[P[_, _]](pab: P[A, B])(implicit ev: Profunctor[P]): P[S, T] = f(pab)
  }

  /** create an [[Iso_]] from pair of functions
    * <p>
    * view -> from the source of an [[Iso_]] to the focus of an [[Iso_]],
    * review -> from the modified focus of an [[Iso_]] to the modified source of an [[Iso_]]
    * </p>
    */
  final def apply[S, T, A, B](view: S => A)(review: B => T): Iso_[S, T, A, B] = iso(view)(review)

  /** synonym to [[apply]] */
  final def iso[S, T, A, B](view: S => A)(review: B => T): Iso_[S, T, A, B] = Iso_(new Rank2TypeIsoLike[S, T, A, B] {
    override def apply[P[_, _]](pab: P[A, B])(implicit ev: Profunctor[P]): P[S, T] = ev.dimap(pab)(view)(review)
  })

  /** lift a polymorphic [[Iso_]] to operate on Functors */
  final def mapP[F[_], G[_]]: MapPartiallyApplied[F, G] = new MapPartiallyApplied[F, G]

  /** lift a polymorphic [[Iso_]] to operate on Functors */
  final def map[F[_]]: MapPartiallyApplied[F, F] = new MapPartiallyApplied[F, F]

  final def id[S, T]: Iso_[S, T, S, T] = Iso_(identity[S] _)(identity[T])

  final private[Iso_] class MapPartiallyApplied[F[_], G[_]](val dummy: Boolean = true) extends AnyVal {
    final def apply[S, T, A, B](iso: Iso_[S, T, A, B])(implicit ev0: Functor[F], ev1: Functor[G]): Iso_[F[S], G[T], F[A], G[B]] =
      Iso_.iso[F[S], G[T], F[A], G[B]](ev0.lift(iso.view))(ev1.lift(iso.review))
  }
}

object Iso {
  /** create a monomorphic [[Iso]] from Rank2TypeIsoLike encoding */
  private[proptics] def apply[S, A](f: Rank2TypeIsoLike[S, S, A, A]): Iso[S, A] = new Iso[S, A] {
    override def apply[P[_, _]](pab: P[A, A])(implicit ev: Profunctor[P]): P[S, S] = f(pab)
  }

  /** create a monomorphic [[Iso]] from pair of functions
    * <p>
    * view -> from the source of an [[Iso]] to the focus of an [[Iso]],
    * review -> from the focus of an [[Iso]] to the source of an [[Iso]]
    * </p>
    */
  final def apply[S, A](view: S => A)(review: A => S): Iso[S, A] = Iso_(view)(review)

  /** synonym to [[apply]] */
  final def iso[S, A](view: S => A)(review: A => S): Iso[S, A] = Iso_(view)(review)

  /** if `A1` is obtained from `A` by removing a single value, then `Option[A1]` is isomorphic to `A` */
  final def non[A](a: A)(implicit ev: Eq[A]): Iso[Option[A], A] = {
    def g(a1: A): Option[A] = if (a1 === a) None else a.some

    Iso_.iso((op: Option[A]) => op.getOrElse(a))(g)
  }

  /** create an [[Iso]] from a function that is its own inverse */
  final def involuted[A](update: A => A): Iso[A, A] = Iso(update)(update)

  /** lift a polymorphic [[Iso_]] to operate on Functors */
  final def map[F[_]]: MapPartiallyApplied[F] = new MapPartiallyApplied[F]

  final def contramap[F[_]]: ContravariantPartiallyApplied[F] = new ContravariantPartiallyApplied[F]

  /** lift a polymorphic [[Iso_]] to operate on Bifunctors */
  final def bimap[F[_, _]]: BimapPartiallyApplied[F] = new BimapPartiallyApplied[F]

  /** monomorphic identity of an [[Iso]] */
  final def id[S]: Iso[S, S] = Iso_.id[S, S]

  final private[Iso] class MapPartiallyApplied[F[_]](val dummy: Boolean = true) extends AnyVal {
    def apply[S, A](iso: Iso[S, A])(implicit ev: Functor[F]): Iso[F[S], F[A]] =
      Iso.iso[F[S], F[A]](ev.lift(iso.view))(ev.lift(iso.review))
  }

  final private[Iso] class ContravariantPartiallyApplied[F[_]](val dummy: Boolean = true) extends AnyVal {
    def apply[S, A](iso: Iso[S, A])(implicit ev: Contravariant[F]): Iso[F[A], F[S]] =
      Iso.iso[F[A], F[S]](ev.liftContravariant(iso.view))(ev.liftContravariant(iso.review))
  }

  final private[Iso] class BimapPartiallyApplied[F[_, _]](val dummy: Boolean = true) extends AnyVal {
    def apply[S, A](iso: Iso[S, A])(implicit ev: Bifunctor[F]): Iso[F[S, S], F[A, A]] =
      Iso.iso[F[S, S], F[A, A]](_.bimap(iso.view, iso.view))(_.bimap(iso.review, iso.review))
  }
}

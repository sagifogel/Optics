package proptics.newtype

import cats.{Applicative, Apply, Eq, FlatMap, Functor, Monad, Order, Semigroup, Show}
import cats.Monoid
import cats.syntax.show._
import cats.syntax.order._
import cats.syntax.semigroup._

import scala.annotation.tailrec

/** The dual of a [[Monoid]] */
final case class Dual[A](runDual: A) extends AnyVal

abstract class DualInstances {
  implicit final def eqDual[A: Eq]: Eq[Dual[A]] = new Eq[Dual[A]] {
    override def eqv(x: Dual[A], y: Dual[A]): Boolean = x.runDual === y.runDual
  }

  implicit final def ordDual[A: Order]: Order[Dual[A]] = new Order[Dual[A]] {
    override def compare(x: Dual[A], y: Dual[A]): Int = x.runDual.compare(y.runDual)
  }

  implicit final def showDual[A: Show]: Show[Dual[A]] = new Show[Dual[A]] {
    override def show(t: Dual[A]): String = s"(Dual ${t.runDual.show})"
  }

  implicit final def semigroupDual[A: Semigroup]: Semigroup[Dual[A]] = new Semigroup[Dual[A]] {
    def combine(x: Dual[A], y: Dual[A]): Dual[A] = Dual(x.runDual |+| y.runDual)
  }

  implicit final def monoidDual[A](implicit ev: Monoid[A]): Monoid[Dual[A]] = new Monoid[Dual[A]] {
    def empty: Dual[A] = Dual(ev.empty)

    def combine(x: Dual[A], y: Dual[A]): Dual[A] = x |+| y
  }

  implicit final def functorDual: Functor[Dual] = new Functor[Dual] {
    override def map[A, B](fa: Dual[A])(f: A => B): Dual[B] = Dual(f(fa.runDual))
  }

  implicit final def applyDual: Apply[Dual] = new Apply[Dual] {
    override def ap[A, B](ff: Dual[A => B])(fa: Dual[A]): Dual[B] = Dual(ff.runDual(fa.runDual))

    override def map[A, B](fa: Dual[A])(f: A => B): Dual[B] = functorDual.fmap(fa)(f)
  }

  implicit final def applicativeDual: Applicative[Dual] = new Applicative[Dual] {
    override def pure[A](x: A): Dual[A] = Dual(x)

    override def ap[A, B](ff: Dual[A => B])(fa: Dual[A]): Dual[B] = applyDual.ap(ff)(fa)
  }

  implicit final def bindDual: FlatMap[Dual] = new FlatMap[Dual] {
    override def flatMap[A, B](fa: Dual[A])(f: A => Dual[B]): Dual[B] = f(fa.runDual)

    @tailrec
    override def tailRecM[A, B](a: A)(f: A => Dual[Either[A, B]]): Dual[B] =
      f(a).runDual match {
        case Right(value) => Dual(value)
        case Left(value)  => tailRecM(value)(f)
      }

    override def map[A, B](fa: Dual[A])(f: A => B): Dual[B] = functorDual.fmap(fa)(f)
  }

  implicit final def monadDual: Monad[Dual] = new Monad[Dual] {
    override def pure[A](x: A): Dual[A] = applicativeDual.pure(x)

    override def flatMap[A, B](fa: Dual[A])(f: A => Dual[B]): Dual[B] = bindDual.flatMap(fa)(f)

    override def tailRecM[A, B](a: A)(f: A => Dual[scala.Either[A, B]]): Dual[B] = bindDual.tailRecM(a)(f)
  }
}

object Dual extends DualInstances

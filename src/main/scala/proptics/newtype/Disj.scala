package proptics.newtype

import algebra.lattice.Heyting
import cats.kernel.Monoid

final case class Disj[A](runDisj: A) extends AnyVal

abstract class DisjInstances {
  implicit final def monoidDisj[A](implicit ev: Heyting[A]): Monoid[Disj[A]] = new Monoid[Disj[A]] {
    override def empty: Disj[A] = Disj(ev.zero)

    override def combine(x: Disj[A], y: Disj[A]): Disj[A] = Disj(ev.or(x.runDisj, y.runDisj))
  }
}

object Disj extends DisjInstances
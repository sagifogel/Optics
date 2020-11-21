package proptics.instances

import scala.Function.const

import cats.Id
import cats.syntax.option._

import proptics.instances.index._
import proptics.{AffineTraversal, At, Lens}

trait AtInstances {
  def at[S, I, A](i: I)(implicit ev: At[S, I, A]): Lens[S, Option[A]] = ev.at(i)

  /** remove a value associated with a key in a Map-like container */
  def remove[S, I, A](i: I)(s: S)(implicit ev: At[S, I, A]): S =
    ev.at(i).set(None)(s)

  implicit final def atIdentity[A]: At[Id[A], Unit, A] = new At[Id[A], Unit, A] {
    override def at(i: Unit): Lens[Id[A], Option[A]] = Lens { id: Id[A] => id.some }(a => _.getOrElse(a))

    override def ix(i: Unit): AffineTraversal[Id[A], A] = indexIdentity[A].ix(i)
  }

  implicit final def atOption[A]: At[Option[A], Unit, A] = new At[Option[A], Unit, A] {
    override def at(i: Unit): Lens[Option[A], Option[A]] = Lens { op: Option[A] => op }(const(identity))

    override def ix(i: Unit): AffineTraversal[Option[A], A] = indexOption[A].ix(i)
  }

  implicit final def atSet[A]: At[Set[A], A, Unit] = new At[Set[A], A, Unit] {
    private def get(i: A)(set: Set[A]): Option[Unit] = if (set.contains(i)) ().some else None

    private def update(i: A): Set[A] => Option[Unit] => Set[A] = set => {
      case Some(_) => set - i
      case None    => set + i
    }

    override def at(i: A): Lens[Set[A], Option[Unit]] = Lens[Set[A], Option[Unit]](get(i))(update(i))

    override def ix(i: A): AffineTraversal[Set[A], Unit] = indexSet[A].ix(i)
  }

  implicit final def atMap[K, V]: At[Map[K, V], K, V] = new At[Map[K, V], K, V] {
    override def at(i: K): Lens[Map[K, V], Option[V]] =
      Lens { map: Map[K, V] => map.get(i) }(map => _.fold(map - i)(map.updated(i, _)))

    override def ix(i: K): AffineTraversal[Map[K, V], V] = indexMap[K, V].ix(i)
  }
}

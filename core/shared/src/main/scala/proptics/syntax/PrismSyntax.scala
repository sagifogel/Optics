package proptics.syntax

import cats.Applicative

import proptics.Prism_

trait PrismSyntax {
  implicit def prismSequenceOps[F[_], S, T, A](prism: Prism_[S, T, F[A], A]): PrismSequenceOps[F, S, T, A] = PrismSequenceOps(prism)
}

final case class PrismSequenceOps[F[_], S, T, A](private val prism: Prism_[S, T, F[A], A]) extends AnyVal {
  /** invert a structure of S containing F[A] to F[T], a structure T containing A's inside an Applicative Functor */
  def sequence(s: S)(implicit ev: Applicative[F]): F[T] = prism.traverse(s)(identity)
}

package proptics.law.discipline

import cats.Eq
import cats.laws.discipline._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll
import org.typelevel.discipline.Laws

import proptics.IndexedLens
import proptics.law.IndexedLensLaws

trait IndexedLensTests[I, S, A] extends Laws {
  def laws: IndexedLensLaws[I, S, A]

  def indexedLens(implicit eqS: Eq[S], eqA: Eq[A], arbS: Arbitrary[S], arbA: Arbitrary[A], arbAA: Arbitrary[A => A], arbIAA: Arbitrary[(A, I) => A]): RuleSet =
    new SimpleRuleSet(
      "IndexedLens",
      "setView" -> forAll(laws.setGet _),
      "viewSet" -> forAll((s: S, a: A) => laws.getSet(s, a)),
      "setSet" -> forAll((s: S, a: A) => laws.setSet(s, a)),
      "overIdentity" -> forAll(laws.overIdentity _),
      "composeOver" -> forAll((s: S, f: (A, I) => A, g: (A, I) => A) => laws.composeOver(s)(f)(g)),
      "composeSourceLens" -> forAll(laws.composeSourceLens _),
      "composeFocusLens" -> forAll((s: S, a: A) => laws.composeFocusLens(s, a))
    )
}

object IndexedLensTests {
  def apply[I, S, A](_indexedLens: IndexedLens[I, S, A]): IndexedLensTests[I, S, A] =
    new IndexedLensTests[I, S, A] { def laws: IndexedLensLaws[I, S, A] = IndexedLensLaws[I, S, A](_indexedLens) }
}

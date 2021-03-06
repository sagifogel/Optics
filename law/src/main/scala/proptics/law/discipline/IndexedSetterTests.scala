package proptics.law.discipline

import cats.Eq
import cats.laws.discipline._
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll
import org.typelevel.discipline.Laws

import proptics.IndexedSetter
import proptics.law.IndexedSetterLaws

trait IndexedSetterTests[I, S, A] extends Laws {
  def laws: IndexedSetterLaws[I, S, A]

  def indexedSetter(implicit eqS: Eq[S], eqA: Eq[A], arbS: Arbitrary[S], arbA: Arbitrary[A], arbAA: Arbitrary[A => A], arbIAA: Arbitrary[(A, I) => A]): RuleSet =
    new SimpleRuleSet(
      "IndexedSetter",
      "setSet" -> forAll((s: S, a: A) => laws.setSet(s, a)),
      "setTwiceSet" -> forAll((s: S, a: A, b: A) => laws.setASetB(s, a, b)),
      "overIdentity" -> forAll(laws.overIdentity _),
      "composeOver" -> forAll((s: S, f: (A, I) => A, g: (A, I) => A) => laws.composeOver(s)(f)(g))
    )
}

object IndexedSetterTests {
  def apply[I, S, A](_indexedSetter: IndexedSetter[I, S, A]): IndexedSetterTests[I, S, A] =
    new IndexedSetterTests[I, S, A] { def laws: IndexedSetterLaws[I, S, A] = IndexedSetterLaws[I, S, A](_indexedSetter) }
}

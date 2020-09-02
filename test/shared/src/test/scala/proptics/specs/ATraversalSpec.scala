package proptics.specs

import cats.data.State
import cats.instances.int._
import cats.instances.list._
import cats.instances.option._
import cats.instances.string._
import cats.syntax.foldable._
import cats.syntax.option._
import proptics.ATraversal
import proptics.law._
import proptics.specs.Compose._
import spire.std.boolean._

import scala.Function.const
import scala.util.Random

class ATraversalSpec extends PropticsSuite {
  val plusOne: Int => Int = _ + 1
  val emptyList = List.empty[Int]
  val list: List[Int] = List(1, 2, 3, 4, 5, 6)
  val boolList: List[Boolean] = List(true, false, true, false)
  val falseBoolList: List[Boolean] = boolList.map(const(false))
  val someEven: Int => Option[Int] = i => if (i % 2 == 0) i.some else none[Int]
  val fromTraversal: ATraversal[List[Int], Int] = ATraversal.fromTraverse[List, Int]
  val boolTraversal: ATraversal[List[Boolean], Boolean] = ATraversal.fromTraverse[List, Boolean]
  val wholeTraversal: ATraversal[Whole, Int] = ATraversal[Whole, Int](_.part)(whole => focus => whole.copy(part = focus))

  checkAll("ATraversal[List[Int], Int] fromTraverse", ATraversalRules(fromTraversal))
  checkAll("ATraversal[Whole, Int] apply", ATraversalRules(wholeTraversal))
  checkAll("ATraversal[Whole, Int] asTraversal", TraversalTests(wholeTraversal.asTraversal).traversal)
  checkAll("ATraversal[Int, Int] id", ATraversalRules(ATraversal.id[Int]))
  checkAll("ATraversal[Int, Int] compose with Iso[Int, Int]", ATraversalRules(aTraversal compose iso))
  checkAll("ATraversal[Int, Int] compose with AnIso[Int, Int]", ATraversalRules(aTraversal compose anIso))
  checkAll("ATraversal[Int, Int] compose with Lens[Int, Int]", ATraversalRules(aTraversal compose lens))
  checkAll("ATraversal[Int, Int] compose with ALens[Int, Int]", ATraversalRules(aTraversal compose aLens))
  checkAll("ATraversal[Int, Int] compose with Prism[Int, Int]", ATraversalRules(aTraversal compose prism))
  checkAll("ATraversal[Int, Int] compose with APrism[Int, Int]", ATraversalRules(aTraversal compose aPrism))
  checkAll("ATraversal[Int, Int] compose with AffineTraversal[Int, Int]", ATraversalRules(aTraversal compose affineTraversal))
  checkAll("ATraversal[Int, Int] compose with AnAffineTraversal[Int, Int]", ATraversalRules(aTraversal compose anAffineTraversal))
  checkAll("ATraversal[Int, Int] compose with Traversal[Int, Int]", ATraversalRules(aTraversal compose traversal))
  checkAll("ATraversal[Int, Int] compose with ATraversal[Int, Int]", ATraversalRules(aTraversal compose aTraversal))
  checkAll("ATraversal[Int, Int] compose with Setter[Int, Int]", SetterTests(aTraversal compose setter).setter)

  test("viewAll") {
    fromTraversal.viewAll(list) shouldEqual list
    fromTraversal.viewAll(emptyList) shouldEqual emptyList
    wholeTraversal.viewAll(whole9) shouldEqual List(9)
  }

  test("preview") {
    fromTraversal.preview(list) shouldEqual Some(1)
    fromTraversal.preview(emptyList) shouldEqual None
    wholeTraversal.preview(whole9) shouldEqual Some(9)
  }

  test("set") {
    fromTraversal.set(0)(list) shouldEqual list.map(const(0))
    wholeTraversal.set(9)(Whole(0)) shouldEqual whole9
  }

  test("over") {
    fromTraversal.over(plusOne)(list) shouldEqual list.map(plusOne)
    wholeTraversal.over(plusOne)(Whole(8)) shouldEqual whole9
  }

  test("traverse") {
    fromTraversal.traverse(list)(_.some) shouldEqual list.some
    fromTraversal.traverse(list)(someEven) shouldEqual None
    fromTraversal.traverse(list)(_.some) shouldEqual fromTraversal.overF(_.some)(list)
    wholeTraversal.traverse(whole9)(_.some) shouldEqual whole9.some
  }

  test("foldMap") {
    fromTraversal.foldMap(list)(_.toString) shouldEqual list.map(_.toString).intercalate("")
    wholeTraversal.foldMap(whole9)(_.toString) shouldEqual 9.toString
  }

  test("fold") {
    fromTraversal.fold(list) shouldEqual list.sum
    fromTraversal.fold(emptyList) shouldEqual 0
    fromTraversal.view(list) shouldEqual fromTraversal.fold(list)
    wholeTraversal.view(whole9) shouldEqual 9
  }

  test("foldr") {
    fromTraversal.foldr(list ++ List(20))(0)(_ - _) should be > 0
    wholeTraversal.foldr(whole9)(0)(_ - _) should be > 0
  }

  test("foldl") {
    fromTraversal.foldl(list ++ List(20))(0)(_ - _) should be < 0
    wholeTraversal.foldl(whole9)(0)(_ - _) should be < 0
  }

  test("sequence_") {
    fromTraversal.sequence_[Option](list) shouldEqual ().some
    wholeTraversal.sequence_[Option](whole9) shouldEqual ().some
  }

  test("traverse_") {
    fromTraversal.traverse_(list)(_.some) shouldEqual Some(())
    fromTraversal.traverse_(list)(someEven) shouldEqual None
    wholeTraversal.traverse_(whole9)(_.some) shouldEqual Some(())
    wholeTraversal.traverse_(whole9)(someEven) shouldEqual None
  }

  {
    import spire.std.int.IntAlgebra

    test("sum") {
      fromTraversal.sum(list) shouldEqual list.sum
      wholeTraversal.sum(whole9) shouldEqual 9
    }

    test("product") {
      fromTraversal.product(list) shouldEqual list.product
      wholeTraversal.product(whole9) shouldEqual 9
    }
  }

  test("forall") {
    fromTraversal.forall(_ < 10)(list) shouldEqual true
    fromTraversal.forall(_ < 10)(emptyList) shouldEqual true
    fromTraversal.forall(_ > 10)(list) shouldEqual false
    fromTraversal.forall(_ > 10)(emptyList) shouldEqual true
    wholeTraversal.forall(_ < 10)(whole9) shouldEqual true
    wholeTraversal.forall(_ > 10)(whole9) shouldEqual false
  }

  test("forall using heyting") {
    fromTraversal.forall(list)(_ < 10) shouldEqual true
    fromTraversal.forall(emptyList)(_ < 10) shouldEqual true
    fromTraversal.forall(list)(_ > 10) shouldEqual false
    fromTraversal.forall(emptyList)(_ > 10) shouldEqual true
    wholeTraversal.forall(whole9)(_ < 10) shouldEqual true
    wholeTraversal.forall(whole9)(_ > 10) shouldEqual false
  }

  test("and") {
    boolTraversal.and(boolList) shouldEqual false
    boolTraversal.and(boolTraversal.set(true)(boolList)) shouldEqual true
    boolTraversal.and(falseBoolList) shouldEqual false
  }

  test("or") {
    boolTraversal.or(boolList) shouldEqual true
    boolTraversal.or(falseBoolList) shouldEqual false
  }

  test("any") {
    fromTraversal.any(list)(greaterThan5) shouldEqual true
    fromTraversal.any(emptyList)(greaterThan10) shouldEqual false
    wholeTraversal.any(whole9)(greaterThan5) shouldEqual true
  }

  test("exist") {
    fromTraversal.exists(greaterThan5)(list) shouldEqual true
    fromTraversal.exists(greaterThan10)(list) shouldEqual false
    wholeTraversal.exists(greaterThan5)(whole9) shouldEqual true
    wholeTraversal.exists(greaterThan10)(whole9) shouldEqual false
  }

  test("notExists") {
    fromTraversal.notExists(greaterThan5)(list) shouldEqual false
    fromTraversal.notExists(greaterThan10)(list) shouldEqual true
    fromTraversal.notExists(greaterThan10)(list) shouldEqual !fromTraversal.exists(greaterThan10)(list)
    wholeTraversal.notExists(greaterThan5)(whole9) shouldEqual false
    wholeTraversal.notExists(greaterThan10)(whole9) shouldEqual true
    wholeTraversal.notExists(greaterThan10)(whole9) shouldEqual !wholeTraversal.exists(greaterThan10)(whole9)
  }

  test("contains") {
    fromTraversal.contains(list)(5) shouldEqual true
    fromTraversal.contains(list)(10) shouldEqual false
    wholeTraversal.contains(whole9)(9) shouldEqual true
    wholeTraversal.contains(whole9)(10) shouldEqual false
  }

  test("notContains") {
    fromTraversal.notContains(list)(5) shouldEqual false
    fromTraversal.notContains(list)(10) shouldEqual true
    fromTraversal.notContains(list)(10) shouldEqual !fromTraversal.contains(list)(10)
    wholeTraversal.notContains(whole9)(9) shouldEqual false
    wholeTraversal.notContains(whole9)(10) shouldEqual true
    wholeTraversal.notContains(whole9)(10) shouldEqual !wholeTraversal.contains(whole9)(10)
  }

  test("isEmpty") {
    fromTraversal.isEmpty(list) shouldEqual false
    fromTraversal.isEmpty(emptyList) shouldEqual true
    wholeTraversal.isEmpty(whole9) shouldEqual false
  }

  test("nonEmpty") {
    fromTraversal.nonEmpty(list) shouldEqual true
    fromTraversal.nonEmpty(emptyList) shouldEqual false
    fromTraversal.nonEmpty(list) shouldEqual !fromTraversal.isEmpty(list)
    wholeTraversal.nonEmpty(whole9) shouldEqual true
    wholeTraversal.nonEmpty(whole9) shouldEqual !wholeTraversal.isEmpty(whole9)
  }

  test("length") {
    fromTraversal.length(list) shouldEqual list.length
    fromTraversal.length(emptyList) shouldEqual 0
    wholeTraversal.length(whole9) shouldEqual 1
  }

  test("find") {
    fromTraversal.find(greaterThan5)(list) shouldEqual list.find(greaterThan5)
    fromTraversal.find(greaterThan10)(list) shouldEqual None
    wholeTraversal.find(greaterThan5)(whole9) shouldEqual 9.some
    wholeTraversal.find(greaterThan10)(whole9) shouldEqual None
  }

  test("first") {
    fromTraversal.first(list) shouldEqual list.head.some
    fromTraversal.first(emptyList) shouldEqual None
    wholeTraversal.first(whole9) shouldEqual 9.some
  }

  test("last") {
    fromTraversal.last(list) shouldEqual list.last.some
    fromTraversal.last(emptyList) shouldEqual None
    wholeTraversal.last(whole9) shouldEqual 9.some
  }

  test("minimum") {
    fromTraversal.minimum(Random.shuffle(list)) shouldEqual list.head.some
    fromTraversal.minimum(emptyList) shouldEqual None
    wholeTraversal.minimum(whole9) shouldEqual 9.some
  }

  test("maximum") {
    fromTraversal.maximum(Random.shuffle(list)) shouldEqual list.last.some
    fromTraversal.maximum(emptyList) shouldEqual None
    wholeTraversal.maximum(whole9) shouldEqual 9.some
  }

  test("toArray") {
    fromTraversal.toArray(list) shouldEqual list.toArray
    wholeTraversal.toArray(whole9) shouldEqual Array(9)
  }

  test("toList") {
    fromTraversal.toList(list) shouldEqual list
    wholeTraversal.toList(whole9) shouldEqual List(9)
  }

  test("use") {
    implicit val state: State[List[Int], Int] = State.pure[List[Int], Int](1)

    fromTraversal.use.runA(list).value shouldEqual list
    wholeTraversal.use.runA(whole9).value shouldEqual List(9)
  }

  test("compose with Getter") {
    (aTraversal compose getter).view(9) shouldEqual 9
  }

  test("compose with Fold") {
    (aTraversal compose fold).fold(9) shouldEqual 9
  }
}

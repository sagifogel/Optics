package proptics.specs

import scala.collection.immutable.ArraySeq

import proptics.instances.prefixed._
import proptics.law.discipline.PrefixedTests

class PrefixedSpec extends PrefixedSpec0 {
  checkAll("PrefixedTests[LazyList[Int], LazyList[Int]] prefixed", PrefixedTests[LazyList[Int], LazyList[Int]].prefixed)
  checkAll("PrefixedTests[ArraySeq[Int], ArraySeq[Int]] prefixed", PrefixedTests[ArraySeq[Int], ArraySeq[Int]].prefixed)
}

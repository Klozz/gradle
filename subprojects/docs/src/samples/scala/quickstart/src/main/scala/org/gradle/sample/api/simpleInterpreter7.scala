package org.gradle.sample.api

object simpleInterpreter7 {
  case class M[A](value: A) {
    def bind[B](k: A => M[B]): M[B] =  k(value)
    def map[B](f: A => B): M[B] =  bind(x => unitM(f(x)))
    def flatMap[B](f: A => M[B]): M[B] = bind(f)
  }

  def unitM[A](a: A): M[A] = M(a)

  def showM(m: M[Value]): String = m.value.toString();


  type Name = String


  trait Term;
  case class Var(x: Name) extends Term
  case class Con(n: Int) extends Term
  case class Add(l: Term, r: Term) extends Term

  case class Lam(x: Name, body: Term) extends Term

  case class App(fun: Term, arg: Term) extends Term

  trait Value
  case object Wrong extends Value {
   override def toString() = "wrong"
  }
  case class Num(n: Int) extends Value {
    override def toString() = n.toString()
  }
  case class Fun(f: Value => M[Value]) extends Value {
    override def toString() = "<function>"
  }

  type Environment = List[Pair[Name, Value]]

  def lookup(x: Name, e: Environment): M[Value] = e match {
    case List() => unitM(Wrong)
    case Pair(y, b) :: e1 => if (x == y) unitM(b) else lookup(x, e1)
  }

  def add(a: Value, b: Value): M[Value] = Pair(a, b) match {
    case Pair(Num(m), Num(n)) => unitM(Num(m + n))
    case _ => unitM(Wrong)
  }

  def apply(a: Value, b: Value): M[Value] = a match {
    case Fun(k) => k(b)
    case _ => unitM(Wrong)
  }

  def Interp(t: Term, e: Environment): M[Value] = t match {
    case Var(x) => lookup(x, e)
    case Con(n) => unitM(Num(n))
    case Add(l, r) => for (val a <- Interp(l, e);
			   val b <- Interp(r, e);
			   val c <- add(a, b))
                      yield c
    case Lam(x, t) => unitM(Fun(a => Interp(t, Pair(x, a) :: e)))
    case App(f, t) => for (val a <- Interp(f, e);
			   val b <- Interp(t, e);
			   val c <- apply(a, b))
		      yield c
  }

  def test(t: Term): String =
    showM(Interp(t, List()))

  val term0 = App(Lam("x", Add(Var("x"), Var("x"))), Add(Con(10), Con(11)))
  val term1 = App(Con(1), Con(2))

  def main(args: Array[String]) {
    println(test(term0))
    println(test(term1))
  }
}
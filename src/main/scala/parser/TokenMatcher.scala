package parser

trait TokenMatcher[T] extends (String => Seq[T])

object IntegerMatcher extends TokenMatcher[Int] {
  def apply(str: String) = {
    try {
      Seq(Integer.parseInt(str))
    } catch {
      case nfe: NumberFormatException => Nil
    }
  }
}
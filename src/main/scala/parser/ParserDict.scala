package parser

import ccg.{CcgCat, CategoryParser}
import semantics.{Ignored, SemanticState}

import scala.collection.mutable
import scala.io.Source

trait DictAdder[S, U] {
  def apply(dict: ParserDict[S], pair: U): ParserDict[S]
}

object DictAdder {
  implicit def stringSyntaxAdder[S, T <: S] = new DictAdder[S, (String, T)] {
    def apply(dict: ParserDict[S], pair: (String, T)) = {
      val term = pair._1
      val syntax = pair._2
      dict.withTerm(term, Seq((syntax, Ignored(term))))
    }
  }

  implicit def stringSyntaxSemanticsAdder[S, T <: S] = new DictAdder[S, (String, (T, SemanticState))] {
    def apply(dict: ParserDict[S], pair: (String, (T, SemanticState))) = {
      val term = pair._1
      val syntax = pair._2._1
      val semantics = pair._2._2
      dict.withTerm(term, Seq((syntax, semantics)))
    }
  }

  implicit def seqStringSyntaxAdder[S, T <: S] = new DictAdder[S, (Seq[String], T)] {
    def apply(dict: ParserDict[S], pair: (Seq[String], T)) = {
      val terms = pair._1
      val syntax = pair._2
      dict.withTerms(terms.map(t => t -> Seq((syntax, Ignored(t)))) toMap)
    }
  }

  implicit def seqStringSyntaxSemanticsAdder[S, T <: S] = new DictAdder[S, (Seq[String], (T, SemanticState))] {
    def apply(dict: ParserDict[S], pair: (Seq[String], (T, SemanticState))) = {
      val terms = pair._1
      val syntax = pair._2._1
      val semantics = pair._2._2
      dict.withTerms(terms.map(t => t -> Seq((syntax, semantics))) toMap)
    }
  }

  implicit def matcherSemanticsAdder[S, T <: S, V, W <: String => Seq[V], Y <: SemanticState] = new DictAdder[S, (W, (T, V => Y))] {
    def apply(dict: ParserDict[S], pair: (W, (T, V => Y))) = {
      val matcher = pair._1
      val syntax = pair._2._1
      val semantics = pair._2._2
      val func = (str: String) => matcher(str).map(m => (syntax, semantics(m)))
      dict.withFunc(func)
    }
  }

  implicit def matcherSyntaxSemanticsAdder[S, T <: S, V, W <: String => Seq[V], Y <: SemanticState] = new DictAdder[S, (W, (V => T, V => Y))] {
    def apply(dict: ParserDict[S], pair: (W, (V => T, V => Y))) = {
      val matcher = pair._1
      val syntax = pair._2._1
      val semantics = pair._2._2
      val func = (str: String) => matcher(str).map(m => (syntax(m), semantics(m)))
      dict.withFunc(func)
    }
  }
}

case class ParserDict[S](map: Map[String, Seq[(S, SemanticState)]] = Map[String, Seq[(S, SemanticState)]](),
                         funcs: Seq[String => Seq[(S, SemanticState)]] = Seq()) extends (String => Seq[(S, SemanticState)]) {
  def +[U](pair: U)(implicit adder: DictAdder[S, U]): ParserDict[S] = {
    adder(this, pair)
  }

  def withTerm(term: String, entries: Seq[(S, SemanticState)]): ParserDict[S] = {
    ParserDict(map.updated(term, entries), funcs)
  }

  def withTerms(termsAndEntries: Map[String, Seq[(S, SemanticState)]]): ParserDict[S] = {
    ParserDict(map ++ termsAndEntries, funcs)
  }

  def withFunc(func: String => Seq[(S, SemanticState)]) = {
    ParserDict(map, funcs :+ func)
  }

  def apply(str: String): Seq[(S, SemanticState)] = {
    getMapEntries(str) ++ getFuncEntries(str)
  }

  private def getMapEntries(str: String): Seq[(S, SemanticState)] = {
    for (entry <- map.getOrElse(str, Seq())) yield {
      entry
    }
  }

  private def getFuncEntries(str: String): Seq[(S, SemanticState)] = {
    for {
      func <- funcs
      entry <- func(str)
    } yield {
      entry
    }
  }
}

object ParserDict {
  def fromCcgBankLexicon(path: String): ParserDict[CcgCat] = {
    val lexiconMap: mutable.Map[String, mutable.ListBuffer[CcgCat]] = mutable.Map()

    val file = Source.fromFile(path)
    for (line <- file.getLines()) {
      val parts = line.split(" +")
      val term = parts(0)
      val parsedCategory: CategoryParser.ParseResult[CcgCat] = CategoryParser(parts(1))
      val prob = parts(4).toDouble
      if (parsedCategory.successful) {
        val cat: CcgCat = parsedCategory.get % prob
        if (lexiconMap contains term) {
          lexiconMap(term).append(cat)
        } else {
          lexiconMap(term) = mutable.ListBuffer(cat)
        }
      }
    }

    ParserDict[CcgCat](syntaxToSemantics(lexiconMap.toMap.mapValues(s => s.toSeq)))
  }

  private def syntaxToSemantics[S](inputMap: Map[String, Seq[S]]): Map[String, Seq[(S, SemanticState)]] = {
    for ((term, entries) <- inputMap) yield {
      term -> entries.map(_ -> Ignored(term))
    }
  }
}
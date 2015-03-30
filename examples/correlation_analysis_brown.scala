import org.nlp4l.core._
import org.nlp4l.core.analysis._
import org.nlp4l.stats._

val index = "/tmp/index-brown"

def schema(): Schema = {
  val analyzerBr = Analyzer(new org.apache.lucene.analysis.brown.BrownCorpusAnalyzer())
  val analyzerEn = Analyzer(new org.apache.lucene.analysis.standard.StandardAnalyzer(null.asInstanceOf[org.apache.lucene.analysis.util.CharArraySet]))
  val fieldTypes = Map(
    "file" -> FieldType(null, true, true),
    "cat" -> FieldType(null, true, true),
    "body_pos" -> FieldType(analyzerBr, true, true, true, true),   // set termVectors and termPositions to true
    "body" -> FieldType(analyzerEn, true, true, true, true)        // set termVectors and termPositions to true
  )
  Schema(analyzerEn, fieldTypes)
}

val reader = IReader(index, schema())

val docSetGOV = reader.subset(TermFilter("cat", "government"))
val docSetNEW = reader.subset(TermFilter("cat", "news"))
val docSetROM = reader.subset(TermFilter("cat", "romance"))
val docSetSF = reader.subset(TermFilter("cat", "science_fiction"))

val words = List("can", "could", "may", "might", "must", "will")

val wcGOV = WordCounts.count(reader, "body", words.toSet, docSetGOV)
val wcNEW = WordCounts.count(reader, "body", words.toSet, docSetNEW)
val wcROM = WordCounts.count(reader, "body", words.toSet, docSetROM)
val wcSF = WordCounts.count(reader, "body", words.toSet, docSetSF)

println("\n\n\nword counts")
println("========================================")
println("word\tgov\tnews\tromance\tSF")
words.foreach{ e =>
  println("%8s%,6d\t%,6d\t%,6d\t%,6d".format(e, wcGOV.getOrElse(e, 0), wcNEW.getOrElse(e, 0), wcROM.getOrElse(e, 0), wcSF.getOrElse(e, 0)))
}

val lj = List( ("gov", wcGOV), ("news", wcNEW), ("romance", wcROM), ("SF", wcSF) )
println("\n\n\nCorrelation Coefficient")
println("================================")
println("\tgov\tnews\tromance\tSF")
lj.foreach{ ej =>
  print("%s".format(ej._1))
  lj.foreach{ ei =>
    print("\t%5.3f".format(Stats.correlationCoefficient(words.map(ej._2.getOrElse(_, 0.toLong)), words.map(ei._2.getOrElse(_, 0.toLong)))))
  }
  println
}

reader.close
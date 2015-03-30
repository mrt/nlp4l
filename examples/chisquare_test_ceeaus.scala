import org.nlp4l.core._
import org.nlp4l.core.analysis._
import org.nlp4l.stats._

val index = "/tmp/index-ceeaus-all"

def schema(): Schema = {
  val analyzerEn = Analyzer(new org.apache.lucene.analysis.standard.StandardAnalyzer(null.asInstanceOf[org.apache.lucene.analysis.util.CharArraySet]))
  val builder = AnalyzerBuilder()
  builder.withTokenizer("whitespace")
  builder.addTokenFilter("lowerCase")
  val analyzerWs = builder.build
  val analyzerJa = Analyzer(new org.apache.lucene.analysis.ja.JapaneseAnalyzer())
  val fieldTypes = Map(
    "file" -> FieldType(null, true, true),
    "type" -> FieldType(null, true, true),
    "cat" -> FieldType(null, true, true),
    "body_en" -> FieldType(analyzerEn, true, true, true, true),   // set termVectors and termPositions to true
    "body_ws" -> FieldType(analyzerWs, true, true, true, true),   // set termVectors and termPositions to true
    "body_ja" -> FieldType(analyzerJa, true, true, true, true)    // set termVectors and termPositions to true
  )
  val analyzerDefault = Analyzer(new org.apache.lucene.analysis.standard.StandardAnalyzer())
  Schema(analyzerDefault, fieldTypes)
}

val reader = IReader(index, schema())

val docSetJUS = reader.subset(TermFilter("file", "ceejus_all.txt"))
val docSetNAS = reader.subset(TermFilter("file", "ceenas_all.txt"))

val totalCountJUS = WordCounts.totalCount(reader, "body_en", docSetJUS)
val totalCountNAS = WordCounts.totalCount(reader, "body_en", docSetNAS)

val words = List("i", "my", "me", "you", "your")

val wcJUS = WordCounts.count(reader, "body_en", words.toSet, docSetJUS)
val wcNAS = WordCounts.count(reader, "body_en", words.toSet, docSetNAS)

println("\t\tCEEJUS\tCEENAS\tchi square")
println("==============================================")
words.foreach{ w =>
  val countJUS = wcJUS.getOrElse(w, 0.toLong)
  val countNAS = wcNAS.getOrElse(w, 0.toLong)
  val cs = Stats.chiSquare(countJUS, totalCountJUS - countJUS, countNAS, totalCountNAS - countNAS, true)
  println("%8s\t%,6d\t%,6d\t%9.4f".format(w, countJUS, countNAS, cs))
}

reader.close

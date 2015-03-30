import java.io.File
import java.nio.file.FileSystems
import org.apache.lucene.index._
import org.apache.lucene.search.TermQuery
import org.nlp4l.core.analysis.Analyzer
import org.nlp4l.core._

import scalax.file.Path
import scalax.file.PathSet

val index = "/tmp/index-ldcc"

def document(file: Path): Document = {
  val ps: Array[String] = file.path.split("/")
  val cat = ps(3)
  val lines = file.lines().toArray
  val url = lines(0)
  val date = lines(1)
  val title = lines(2)
  val body = file.lines().drop(3).toList
  Document(Set(
    Field("url", url), Field("date", date), Field("cat", cat),
    Field("title", title), Field("body", body)
  ))
}

// delete existing Lucene index
val p = Path(new File(index))
p.deleteRecursively()

// define a schema for the index
val analyzerJa = Analyzer(new org.apache.lucene.analysis.ja.JapaneseAnalyzer())
val fieldTypes = Map(
  "url" -> FieldType(null, true, true),
  "date" -> FieldType(null, true, true),
  "cat" -> FieldType(null, true, true),
  "title" -> FieldType(analyzerJa, true, true, true, true),  // set termVectors and termPositions to true
  "body" -> FieldType(analyzerJa, true, true, true, true, true)    // set termVectors and termPositions and termOffsets to true
)
val analyzerDefault = Analyzer(new org.apache.lucene.analysis.standard.StandardAnalyzer())
val schema = Schema(analyzerDefault, fieldTypes)

// write documents into an index
val writer = IWriter(index, schema)

val c: PathSet[Path] = Path("corpora", "ldcc", "text").children()
c.filterNot( e => e.name.endsWith(".txt") ).foreach {
  f => f.children().filterNot( g => g.name.equals("LICENSE.txt") ).foreach( h => writer.write(document(h)) )
}

writer.close

// search
val searcher = ISearcher(index)
val results = searcher.search(query=new TermQuery(new Term("title", "旅行")), rows=10)

results.foreach(doc => {
  printf("[DocID] %d: %s\n", doc.docId, doc.get("title"))
})
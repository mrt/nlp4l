package org.nlp4l.core

import org.apache.lucene.index.{TermsEnum, DocsAndPositionsEnum, DocsEnum, Terms => LuceneTerms}
import org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS
import org.apache.lucene.util.{BytesRef, Bits}


/**
 * Class representing sequence of document ids (and optionally, positions/offsets information) associated to given field and a term.
 * This is a sequence of [[Doc]] instances. This holds Lucene TermsEnum and DocsEnum (or DocsAndPositionsEnum) for the term internally.
 *
 * @constructor Create a new TermDocs instance with given term.
 *
 * @param text the string representation for the term
 * @param terms the Lucene Terms instance
 * @param liveDocs the Bits representing live docs
 * @param field the FieldInfo instance holding this term
 */
class TermDocs(val text: String, terms: LuceneTerms, liveDocs: Bits, field: FieldInfo) extends Seq[Doc] {

  val te = terms.iterator(null)
  val found = te.seekExact(new BytesRef(text))
  val (docFreq, totalTermFreq) =
  if (found)
    (te.docFreq(), te.totalTermFreq())
  else
    (0, 0L)

  def docStream: Stream[Doc] = {
    def newDoc(de: DocsEnum) = {
      val dpe = if (de.isInstanceOf[DocsAndPositionsEnum]) de.asInstanceOf[DocsAndPositionsEnum] else null
      // TODO: better ways to get the term vector for this doc?
      val tvTerm = {
        if (field == null || field.reader == null || field.reader.ir == null) null
        else
          field.reader.ir.getTermVector(de.docID(), field.name)
      }
      new Doc(de.docID(), de.freq(), text, dpe, tvTerm)
    }

    def from(first: Doc, de: DocsEnum): Stream[Doc] =
      if (de.nextDoc() == NO_MORE_DOCS) first #:: Stream.empty
      else first #:: from(newDoc(de), de)

    lazy val de =
      if (found) if (te.docsAndPositions(liveDocs, null) != null) te.docsAndPositions(liveDocs, null) else te.docs(liveDocs, null)
      else null

    if (de == null || de.nextDoc() == NO_MORE_DOCS) Stream.empty
    else from(newDoc(de), de)
  }

  /**
   * Returns the set of ids of documents including this term.
   */
  def docIds = docStream.map(_.docId).toSet

  override def iterator: Iterator[Doc] = docStream.iterator
  
  override def length: Int = docStream.length
  
  override def apply(idx: Int): Doc = docStream(idx)

  override def toString() = "Term(text=%s,docFreq=%d)".format(text, docFreq)

}


/**
 * Class representing a document in the index. This holds the Lucene document id and term frequency and optionally, positions/offsets information.
 *
 * @constructor Create a new Doc instance with given document id.
 *
 * @param docId the document id
 * @param freq the term frequency in this doc
 * @param text the term text
 * @param dpe the Lucene's DocsAndPositionsEnum instance
 * @param tvTerm the Lucene's Terms instance representing the term vector for this doc
 */
class Doc(val docId: Int, val freq: Int, text: String, dpe: DocsAndPositionsEnum = null, tvTerm: LuceneTerms = null) {

  // position & offsets info from term vector for this doc
  lazy val tvPosList: Iterable[PosAndOffset] =
    if (tvTerm == null) Iterable.empty[PosAndOffset]
    else {
      val builder = Seq.newBuilder[PosAndOffset]
      val te = tvTerm.iterator(null)
      if (te.seekExact(new BytesRef(text))) {
        val dpe = te.docsAndPositions(null, null)
        if (dpe != null) {
          assert(dpe.nextDoc() != NO_MORE_DOCS)  // term vector has exactly one document
          for (i <- 0 to dpe.freq() - 1) {
            val pos = dpe.nextPosition()
            val payload: String = if (dpe.getPayload == null) null else dpe.getPayload.utf8ToString()
            builder += PosAndOffset(pos, dpe.startOffset(), dpe.endOffset(), payload)
          }
        }
      }
      builder.result()
    }

  // populate position and offsets from index or term vector
  lazy val posAndOffsets: Seq[PosAndOffset] =
    // get positions info from term vector or DocsAndPositionsEnum
    if (tvPosList.nonEmpty)
      tvPosList.toSeq
    else if (dpe != null) {
      val builder = Seq.newBuilder[PosAndOffset]
      for (i <- 0 to dpe.freq() - 1) {
        // next position
        val pos = dpe.nextPosition()
        // offsets
        val (sOffset: Int, eOffset: Int): (Int, Int) =
          if (dpe.startOffset() >= 0 && dpe.endOffset() >= 0) (dpe.startOffset(), dpe.endOffset())
          else (-1, -1)
        // payload
        val payload: String = if (dpe.getPayload == null) null else dpe.getPayload.utf8ToString()

        builder += PosAndOffset(pos, sOffset, eOffset, payload)
      }
      builder.result()
    }
    else Seq.empty[PosAndOffset]

  /**
   * Returns true if this Doc has positions information else false.
   */
  def hasPositions: Boolean = posAndOffsets.nonEmpty

  /**
   * Returns true if this Doc has offsets information else false.
   */
  def hasOffsets: Boolean = hasPositions && posAndOffsets.exists(_.hasOffsets)

  /**
   * Returns true if this Doc has payload else false.
   */
  def hasPayloads: Boolean = hasPositions && posAndOffsets.exists(_.hasPayload)

  override def toString = "Doc(id=%d, freq=%d, positions=%s)".format(docId, freq, posAndOffsets)

}

/**
 * Case class representing a position of a term in a document. Optionally, this also holds start/end offsets and payload.
 */
case class PosAndOffset(pos: Int, startOffset: Int, endOffset: Int, payload: String) {

  /**
   * Returns true if this has offsets information
   */
  def hasOffsets = startOffset >= 0 && endOffset >= 0

  /**
   * Returns true if this has a payload
   */
  def hasPayload = payload != null

  override def toString = {
    if (hasOffsets) "(pos=%d,offset={%d-%d})".format(pos, startOffset, endOffset)
    else "pos=%d".format(pos)
  }

}

/*
 * Class representing terms associated to a field.
 * This is a sequence of [[TermDocs]] instances. This holds Lucene Terms internally.
 *
 * @constructor Create a new Terms instance with given Lucene's Terms instance.
 *
 * @param terms the Lucene Terms
 * @param liveDocs the Bits representing live docs
 * @param field the FieldInfo instance holding the terms
 */
class Terms(terms: LuceneTerms, liveDocs: Bits, val field: FieldInfo) extends Seq[TermDocs]{

  def termStream: Stream[TermDocs] = {
    def newTermDocs(te: TermsEnum) =
      new TermDocs(te.term().utf8ToString(), terms, liveDocs, field)

    def from(first: TermDocs, te: TermsEnum): Stream[TermDocs] =
      if (te.next() == null) first #:: Stream.empty
      else first #:: from(newTermDocs(te), te)

    val te = terms.iterator(null)
    if (te.next() == null) Stream.empty
    else from(newTermDocs(te), te)
  }

  override def iterator: Iterator[TermDocs] = termStream.iterator

  override def length: Int = termStream.length

  override def apply(idx: Int): TermDocs = termStream(idx)

  /**
   * Returns the number of unique terms for the field.
   */
  def uniqTerms = terms.size()

  /**
   * Returns the number of documents that have at least one term for this field, or -1 if this measure isn't stored by the codec
   */
  def docCount = terms.getDocCount

  /**
   * Returns string representation for the largest term (in lexicographic order) in the field
   */
  // TODO: support types other than String
  def max = terms.getMax.utf8ToString()

  /**
   * Returns string representation for the smallest term (in lexicographic order) in the field.
   */
  // TODO: support types other than String
  def min = terms.getMin.utf8ToString()

  /**
   * Returns the sum of document frequencies for all terms in this field, or -1 if this measure isn't stored by the codec.
   */
  def sumDocFreq = terms.getSumDocFreq

  /**
   * Returns the sum of total term frequencies for all terms in this field, or -1 if this measure isn't stored by the codec (or if this fields omits term freq and positions)
   */
  def sumTotalTermFreq = terms.getSumDocFreq

  /**
   * Returns true if documents in this field store per-document term frequency
   */
  def hasFreqs = terms.hasFreqs

  /**
   * Returns true if documents in this field store offsets
   */
  def hasOffsets = terms.hasOffsets

  /**
   * Returns true if documents in this field store positions.
   */
  def hasPositions = terms.hasPositions

}

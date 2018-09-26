package mtas.search.spans;

import mtas.codec.util.CodecInfo;
import mtas.search.similarities.MtasSimScorer;
import mtas.search.spans.util.MtasSpanQuery;
import mtas.search.spans.util.MtasSpanWeight;
import mtas.search.spans.util.MtasSpans;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.Similarity.SimScorer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class MtasSpanPositionQuery extends MtasSpanQuery {
  private String field;
  private int start;
  private int end;

  public MtasSpanPositionQuery(String field, int position) {
    this(field, position, position);
  }

  public MtasSpanPositionQuery(String field, int start, int end) {
    super(1, 1);
    this.field = field;
    this.start = start;
    this.end = end;
  }

  @Override
  public String getField() {
    return field;
  }

  @Override
  public MtasSpanWeight createWeight(IndexSearcher searcher,
      boolean needsScores, float boost) throws IOException {
    return new SpanAllWeight(searcher, null, boost);
  }

  protected class SpanAllWeight extends MtasSpanWeight {
    private static final String METHOD_GET_DELEGATE = "getDelegate";
    private static final String METHOD_GET_POSTINGS_READER = "getPostingsReader";

    public SpanAllWeight(IndexSearcher searcher,
        Map<Term, TermContext> termContexts, float boost) throws IOException {
      super(MtasSpanPositionQuery.this, searcher, termContexts, boost);
    }

    @Override
    public void extractTermContexts(Map<Term, TermContext> contexts) {
      // don't do anything
    }

    @Override
    public MtasSpans getSpans(LeafReaderContext context,
        Postings requiredPostings) throws IOException {
      try {
        // get leafreader
        LeafReader r = context.reader();
        // get delegate
        Boolean hasMethod = true;
        while (hasMethod) {
          hasMethod = false;
          Method[] methods = r.getClass().getMethods();
          for (Method m : methods) {
            if (m.getName().equals(METHOD_GET_DELEGATE)) {
              hasMethod = true;
              r = (LeafReader) m.invoke(r, (Object[]) null);
              break;
            }
          }
        }
        // get fieldsproducer
        Method fpm = r.getClass().getMethod(METHOD_GET_POSTINGS_READER,
            (Class<?>[]) null);
        FieldsProducer fp = (FieldsProducer) fpm.invoke(r, (Object[]) null);
        // get MtasFieldsProducer using terms
        Terms t = fp.terms(field);
        if (t == null) {
          return new MtasSpanMatchNoneSpans(MtasSpanPositionQuery.this);
        } else {
          CodecInfo mtasCodecInfo = CodecInfo.getCodecInfoFromTerms(t);
          return new MtasSpanPositionSpans(MtasSpanPositionQuery.this,
              mtasCodecInfo, field, start, end);
        }
      } catch (InvocationTargetException | IllegalAccessException
          | NoSuchMethodException e) {
        throw new IOException("Can't get reader", e);
      }

    }

    @Override
    public void extractTerms(Set<Term> terms) {
      // don't do anything
    }

    @Override
    public SimScorer getSimScorer(LeafReaderContext context) {
      return new MtasSimScorer();
    }

//    @Override
//    public boolean isCacheable(LeafReaderContext arg0) {
//      return true;
//    }

  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(this.getClass().getSimpleName() + "([" + start
        + (start != end ? "," + end : "") + "])");
    return buffer.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final MtasSpanPositionQuery that = (MtasSpanPositionQuery) obj;
    return field.equals(that.field) && start == that.start && end == that.end;
  }

  @Override
  public int hashCode() {
    int h = this.getClass().getSimpleName().hashCode();
    h = (h * 7) ^ field.hashCode();
    h = (h * 13) ^ start;
    h = (h * 17) ^ end;
    return h;
  }
  
  @Override
  public boolean isMatchAllPositionsQuery() {
    return false;
  }
}

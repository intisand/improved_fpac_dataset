/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 *
 * @author Debasis
 */
public class TermStats implements Comparable<TermStats> {

    /**
     * @return the term
     */
    public String getTerm() {
        return term;
    }

    /**
     * @param term the term to set
     */
    public void setTerm(String term) {
        this.term = term;
    }

    /**
     * @return the tf
     */
    public int getTf() {
        return tf;
    }

    /**
     * @param tf the tf to set
     */
    public void setTf(int tf) {
        this.tf = tf;
    }

    /**
     * @return the ntf
     */
    public float getNtf() {
        return ntf;
    }

    /**
     * @param ntf the ntf to set
     */
    public void setNtf(float ntf) {
        this.ntf = ntf;
    }

    /**
     * @return the idf
     */
    public float getIdf() {
        return idf;
    }

    /**
     * @param idf the idf to set
     */
    public void setIdf(float idf) {
        this.idf = idf;
    }

    /**
     * @return the wt
     */
    public float getWt() {
        return wt;
    }

    /**
     * @param wt the wt to set
     */
    public void setWt(float wt) {
        this.wt = wt;
    }

    /**
     * @return the wt_author
     */
    public float getWt_author() {
        return wt_author;
    }

    /**
     * @param wt_author the wt_author to set
     */
    public void setWt_author(float wt_author) {
        this.wt_author = wt_author;
    }

    private String term;
    private int tf;
    private float ntf;
    private float idf;
    private float wt;
    private float wt_author;

    public static int MAX_NUM_QRY_TERMS = 50000;
    
    public TermStats(String term, float wt) {
        this.term = term;
        this.wt = wt;
        this.wt_author = wt;
    }
    
    public TermStats(String term, int tf, IndexReader reader) throws Exception {
        this.term = term;
        this.tf = tf;
        idf = (float)(
                Math.log(reader.numDocs()/
                (float)(reader.docFreq(new Term(WMTIndexer.FIELD_ANALYZED_CONTENT, term)))));
    }

    public void computeWeight(int docLen, float lambda) {
        setNtf(getTf() / (float)docLen);
        setWt((float) Math.log(1 + lambda/(1-lambda) * getNtf() * getIdf()));
    }
    public void computeTFIDF(int docLen) {
        setWt((getTf() / (float)docLen) * getIdf());
        setNtf(getTf() / (float)docLen);
        setWt_author((float) Math.log(1 + .6/(1-.6) * getNtf() * getIdf()));
    }

    @Override
    public int compareTo(TermStats that) {
        return -1*Float.compare(this.getWt(), that.getWt()); // descending
    }

}

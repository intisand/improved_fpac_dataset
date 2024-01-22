/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer.utils;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author inti
 */
public class TermFreqPT implements Comparable<TermFreqPT>{

    private String term;
    private int tf;
    
    public TermFreqPT(BytesRef term, IndexReader reader) throws Exception {
        this.term=term.utf8ToString();
        tf = reader.docFreq(new Term("words",term));
    }

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

    @Override
    public int compareTo(TermFreqPT that) {
        return -1*Float.compare(this.tf, that.tf); // descending
    }
}

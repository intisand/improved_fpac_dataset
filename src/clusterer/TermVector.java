/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import static clusterer.TermStats.MAX_NUM_QRY_TERMS;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;


/**
 *
 * @author Debasis
 */
public final class TermVector {
    public List<TermStats> termStatsList;
    float norm;

    public TermVector() {
        termStatsList = new ArrayList<>();
        norm = 0;
    }
    
    public TermVector(List<TermStats> termStats) {
        this.termStatsList = termStats;
        norm = computeNorm();
    }
    
    void add(TermStats stats) {
        termStatsList.add(stats);
    }
    
    public static TermVector extractAllDocTerms(IndexReader reader, int docId, String contentFieldName, float lambda) throws Exception {
        return extractDocTerms(reader, docId, contentFieldName, 1, lambda);
    }

    public static TermVector extractAllDocTermsTF(IndexReader reader, int docId, String contentFieldName, float lambda) throws Exception {
        return extractDocTermsTF(reader, docId, contentFieldName, 1, lambda);
    }


    public int getLen() throws Exception {
		return termStatsList.size();
	}

    public static TermVector extractDocTermsTF(IndexReader reader, int docId, String contentFieldName, float queryToDocRatio, float lambda) throws Exception {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;

        tfvector = reader.getTermVector(docId, contentFieldName);
        if (tfvector == null || tfvector.size() == 0)
            return null;
        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field

        List<TermStats> termStats = new ArrayList<>();

        int docLen = 0;
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            tf = (int)termsEnum.totalTermFreq();
            termText = term.utf8ToString();
            termStats.add(new TermStats(termText, tf, reader));
            docLen += tf;
        }

//        if (queryToDocRatio == 1) {
//            // if all terms are to be selected skip the weighting and the
//            // sorting steps.
//            return new TermVector(termStats);
//        }
//
//        float acumulado = 0.0f;
        for (TermStats ts : termStats) {
            ts.computeTFIDF(docLen);
//            acumulado += ts.wt;
        }
//        System.out.println(acumulado);
        Collections.sort(termStats);
        int numTopTerms = (int)(queryToDocRatio*termStats.size());
        numTopTerms = Math.min(numTopTerms, MAX_NUM_QRY_TERMS);
        if (numTopTerms == 0)
            return null;

        return new TermVector(termStats.subList(0, numTopTerms));
    }
    
    public static TermVector extractDocTerms(IndexReader reader, int docId, String contentFieldName, float queryToDocRatio, float lambda) throws Exception {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;
        
        tfvector = reader.getTermVector(docId, contentFieldName);
        if (tfvector == null || tfvector.size() == 0)            
            return null;
        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field
        
        List<TermStats> termStats = new ArrayList<>();
        
        int docLen = 0;
    	while ((term = termsEnum.next()) != null) { // explore the terms for this field            
            tf = (int)termsEnum.totalTermFreq();
            termText = term.utf8ToString();
            termStats.add(new TermStats(termText, tf, reader));            
            docLen += tf;
        }
        
//        if (queryToDocRatio == 1) {
//            // if all terms are to be selected skip the weighting and the
//            // sorting steps.
//            return new TermVector(termStats);
//        }
//
//        float acumulado = 0.0f;
        for (TermStats ts : termStats) {
            ts.computeWeight(docLen, lambda);
//            acumulado += ts.wt;
        }
//        System.out.println(acumulado);
        Collections.sort(termStats);
        int numTopTerms = (int)(queryToDocRatio*termStats.size());
        numTopTerms = Math.min(numTopTerms, MAX_NUM_QRY_TERMS);
		if (numTopTerms == 0)
			return null;
        
        return new TermVector(termStats.subList(0, numTopTerms));
    }
    
    public float cosineSim(TermVector that) {
        float sim = 0;
        int i, j;
        int alen = this.termStatsList.size(), blen = that.termStatsList.size();
        
        for (i=0, j=0; i < alen && j < blen; ) {
            TermStats a = this.termStatsList.get(i);
            TermStats b = that.termStatsList.get(j);
            
            int cmp = a.getTerm().compareTo(b.getTerm());
            if (cmp == 0) {
                sim += (a.getWt() * b.getWt());
                i++;
                j++;
            }
            else if (cmp < 0) {
                i++;
            }
            else {
                j++;
            }
        }
        
        return sim/(this.norm * that.norm);
    }
    
    float computeNorm() {
        float normval = 0;
        for (TermStats ts : termStatsList) {
            normval += ts.getWt()*ts.getWt();
        }
        return (float)Math.sqrt(normval);
    }
    
    // Call this function for constructing the centroid vector during K-means
    static public TermVector add(TermVector avec, TermVector bvec) {
        TermVector sum = new TermVector();
        int i, j;
        int alen = sum.termStatsList.size(), blen = bvec.termStatsList.size();

        for (i=0, j=0; i < alen && j < blen; ) {
            TermStats a = avec.termStatsList.get(i);
            TermStats b = bvec.termStatsList.get(j);
            
            int cmp = a.getTerm().compareTo(b.getTerm());
            if (cmp == 0) {
                sum.add(new TermStats(a.getTerm(), a.getWt()+b.getWt()));
                i++;
                j++;
            }
            else if (cmp < 0) {
                sum.add(new TermStats(a.getTerm(), a.getWt()));
                i++;
            }
            else {
                sum.add(new TermStats(b.getTerm(), b.getWt()));
                j++;
            }
        }

        return sum;
    }
    static public TermVector addToAverage(TermVector averageVector, TermVector currentVector) {
        return  averageVector;
    }

    TermsEnum iterator(Object object) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer.utils;

import clusterer.TermStats;
import clusterer.TermVector;
import indexer.WMTIndexer;
import static indexer.WMTIndexer.FIELD_ANALYZED_CONTENT;
import static indexer.WMTIndexer.FIELD_CLUSTER_ID;
import static indexer.WMTIndexer.FIELD_DOMAIN_ID;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public class RelatedDocumentsRetriever implements Runnable {

    /**
     * @return the relatedDocs
     */
    public TopDocs getRelatedDocs() {
        return relatedDocs;
    }

    /**
     * @param relatedDocs the relatedDocs to set
     */
    public void setRelatedDocs(TopDocs relatedDocs) {
        this.relatedDocs = relatedDocs;
    }

    IndexReader reader;
    public int docId;
    int clusterId;
    public Document queryDoc;
    String contentFieldName;
    String idFieldName;
    private TopDocs relatedDocs;
    public HashMap<Integer, ScoreDoc> docScoreMap;
    List<Integer> nonretrievedDocIds;  // doc-ids that were not retrieved but assigned to this cluster
    public TermVector termVectorForTrueCentroids = null;
    Properties prop;
    float qSelLambda;
    float queryToDocRatio;
    int numWanted;

    public int getNumWanted() {
        return numWanted;
    }

    public void setNumWanted(int numWanted) {
        this.numWanted = numWanted;
    }

    public RelatedDocumentsRetriever(IndexReader reader, int docId, Properties prop, int clusterId) throws IOException {
        this.reader = reader;
        this.docId = docId;
        this.prop = prop;
        this.clusterId = clusterId;
        this.contentFieldName = prop.getProperty("content.field_name", WMTIndexer.FIELD_ANALYZED_CONTENT);
        this.idFieldName = prop.getProperty("content.id", WMTIndexer.FIELD_URL);
        qSelLambda = Float.parseFloat(prop.getProperty("lm.termsel.lambda", "0.6f"));
        this.queryDoc = reader.document(docId);
        nonretrievedDocIds = new ArrayList<>();
        queryToDocRatio = 1;
    }

    TopDocs normalize(TopDocs topDocs) {
        if (topDocs.totalHits == 0) {
            return topDocs;
        }

        ScoreDoc[] sortedSD = normalize(topDocs.scoreDocs);
        return new TopDocs(topDocs.totalHits, sortedSD, sortedSD[0].score);
    }

    ScoreDoc[] normalize(ScoreDoc[] sd) {
        ScoreDoc[] normalizedScoreDocs = new ScoreDoc[sd.length];
        for (int i = 0; i < sd.length; i++) {
            normalizedScoreDocs[i] = new ScoreDoc(sd[i].doc, sd[i].score);
        }

        float sumScore = 0;

        for (int i = 0; i < sd.length; i++) {
            if (Float.isNaN(sd[i].score)) {
                continue;
            }
            sumScore += sd[i].score;
        }

        for (int i = 0; i < sd.length; i++) {
            if (Float.isNaN(sd[i].score)) {
                normalizedScoreDocs[i].score = 0;
            } else {
                normalizedScoreDocs[i].score = sd[i].score / sumScore;
            }
        }
        return normalizedScoreDocs;
    }

    public void addDocId(int docId) {
        nonretrievedDocIds.add(docId);
    }

    @Override
    public void run() {
        try {
            getRelatedDocs(this.numWanted);
        } catch (Exception ex) {
            //logger.debug(ex.toString());
        }
    }

    public TopDocs getRelatedDocs(int numWanted) throws Exception {

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new LMJelinekMercerSimilarity(0.4f));

        BooleanQuery queryDocument = new BooleanQuery();

        TermVector repTerms = termVectorForTrueCentroids != null ? termVectorForTrueCentroids : TermVector.extractDocTerms(reader, docId, contentFieldName, queryToDocRatio, qSelLambda);
        if (repTerms == null) {
            return null;
        }
        boolean retry = true;
        while (retry) {
            try {
                int counter = 0;
                for (TermStats ts : repTerms.termStatsList) {
//                    if (counter++ >= 1024) break;
                    queryDocument.add(new TermQuery(new Term(contentFieldName, ts.getTerm())), BooleanClause.Occur.SHOULD);
                }
                setRelatedDocs(searcher.search(queryDocument, numWanted));
                retry = false;
            } catch (BooleanQuery.TooManyClauses e) {
                // Double the number of boolean queries allowed.
                // The default is in org.apache.lucene.search.BooleanQuery and is 1024.
                String defaultQueries = Integer.toString(BooleanQuery.getMaxClauseCount());
                int oldQueries = Integer.parseInt(System.getProperty("org.apache.lucene.maxClauseCount", defaultQueries));
                int newQueries = oldQueries * 2;
                System.setProperty("org.apache.lucene.maxClauseCount", Integer.toString(newQueries));
                BooleanQuery.setMaxClauseCount(newQueries);
                retry = true;
            }
        }

        setRelatedDocs(normalize(getRelatedDocs()));
        docScoreMap = new HashMap<>();
//        System.out.println("Mi lista TOP es:");
        for (ScoreDoc sd : getRelatedDocs().scoreDocs) {
            docScoreMap.put(sd.doc, sd);
//            System.out.println("doc = " + sd.doc + " score = " + sd.score);
        }
        return getRelatedDocs();
    }

    public int getUnrelatedDocument(HashMap<Integer, Byte> centroidDocIds, RelatedDocumentsRetriever[] rdes) {
        int numDocs = reader.numDocs();
        int start = (int) (Math.random() * numDocs), i;
        int end = numDocs;
        boolean isInTopList;
        HashMap<Integer, ScoreDoc> centroidDocScoreMap = new HashMap<>();
//        System.out.println(centroidDocIds);
        for (i = start; i < end; i++) {
            isInTopList = false;
            if (!centroidDocIds.containsKey(i)) {
                for (RelatedDocumentsRetriever rde : rdes) {
                    if (rde == null) {
                        continue;
                    }
                    centroidDocScoreMap = rde.docScoreMap;
//                    System.out.println(centroidDocScoreMap);
                    if (centroidDocScoreMap != null && centroidDocScoreMap.containsKey(i)) {
                        isInTopList = true;
                        break;
                    }
                }
            }
            if (!isInTopList) {
                break;
            }
            if (i == end - 1) {
                end = start;
                i = 0;
            }
        }
        // if nothing found, return a random one... else this document
        return end == start ? start : i;
    }

    Document constructDoc(int docId, int clusterId) throws Exception {

        Document indexedDoc = reader.document(docId);
        String id = indexedDoc.get(idFieldName);
        String domainName = indexedDoc.get(FIELD_DOMAIN_ID);
        String content = indexedDoc.get(FIELD_ANALYZED_CONTENT);

        Document doc = new Document();
        doc.add(new Field(idFieldName, id, Field.Store.YES, Field.Index.NOT_ANALYZED));

        if (domainName != null) {
            doc.add(new Field(FIELD_DOMAIN_ID, domainName, Field.Store.YES, Field.Index.NOT_ANALYZED));
        }

        doc.add(new Field(FIELD_CLUSTER_ID, String.valueOf(clusterId), Field.Store.YES, Field.Index.NOT_ANALYZED));

        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(contentFieldName, content,
                Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.YES));

        return doc;
    }

    public int getNumberOfUniqueTerms(int docId) throws Exception {

        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        tfvector = reader.getTermVector(docId, contentFieldName);
        if (tfvector == null || tfvector.size() == 0) {
            return 0;
        }

        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field
        int numTerms = 0;
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            numTerms++;
        }
        return numTerms;
    }

    public int recomputeCentroidDoc(HashMap<Integer, Byte> centroidsIds) throws Exception {
        int numNonRetrDocs = nonretrievedDocIds.size();
//        System.out.println("no retrieved docs = " + numNonRetrDocs);
        int numRelatedDocs = getRelatedDocs() == null ? 0 : getRelatedDocs().scoreDocs.length;
//        System.out.println("num related docs = " + numRelatedDocs);
        int[] docIds = new int[numRelatedDocs + numNonRetrDocs];
        int i;
        for (i = 0; i < numRelatedDocs; i++) {
            docIds[i] = getRelatedDocs().scoreDocs[i].doc;
        }
        for (i = 0; i < numNonRetrDocs; i++) {
            docIds[numRelatedDocs + i] = nonretrievedDocIds.get(i);
        }

        int mostCentralDocId = 0;
        int lastCentralDocId = 0;
        int maxNumUniqueTermsSeen = getNumberOfUniqueTerms(docIds[0]);

        for (i = 1; i < docIds.length; i++) {
            try {
                int numUniqueTerms = getNumberOfUniqueTerms(docIds[i]);
                if (numUniqueTerms > maxNumUniqueTermsSeen) {
                    maxNumUniqueTermsSeen = numUniqueTerms;
                    mostCentralDocId = i;
                    if (centroidsIds != null && centroidsIds.containsKey(mostCentralDocId)) {
                        mostCentralDocId = lastCentralDocId;
                    } else {
                        lastCentralDocId = mostCentralDocId;
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
        return mostCentralDocId;
    }
}

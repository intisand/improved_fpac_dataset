package clusterer;

import clusterer.utils.RelatedDocumentsRetriever;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.util.*;

import static java.lang.System.out;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FPAC_TC_with_L_centroids extends FPACNU_SetCover {

    @Override
    public String getAlgoName() {
        return "FPAC_TC_with_L_centroids";
    }

    public FPAC_TC_with_L_centroids(Properties propFile, String dataset, int centroides) throws Exception {
        super(propFile, dataset, centroides);
    }

    public FPAC_TC_with_L_centroids(Properties propFile, List<Integer> initCentroids, String dataset, int centroides) throws Exception {
        super(propFile, initCentroids, dataset, centroides);
    }

    public FPAC_TC_with_L_centroids(Properties propFile, List<Integer> initCentroids, int numberOfCentroidsByGroupL, String dataset, int centroides) throws Exception {
        super(propFile, initCentroids, dataset, centroides);
        numberOfCentroidsByGroup = numberOfCentroidsByGroupL;
    }

    @Override
    public float recomputeCentroids() throws IOException {
//        numberOfCentroidsByGroup = 10;
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            docsInEachCluster.add(new ArrayList<>());
            dynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
            for (int j = 0; j < numberOfCentroidsByGroup; j++) {
                dynamicCentroids.get(i).add(new RelatedDocumentsRetriever(reader, 0, prop, i));
            }
        }
        int clusterId;
        for (int docId = 0; docId < numDocs; docId++) {
            try {
                clusterId = getClusterId(docId);
                if (clusterId == INITIAL_CLUSTER_ID) {
                    continue;
                }
                docsInEachCluster.get(getClusterId(docId)).add(docId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        int tf;
        float wt;
        float idf;
        String termText;

        for (int cluster = 0; cluster < K; cluster++) {
            HashMap<String, Float> vocabularySetStats[] = new HashMap[numberOfCentroidsByGroup];
            for (int c = 0; c < vocabularySetStats.length; c++) {
                vocabularySetStats[c] = new HashMap<>();
            }

            out.println("Calculando centroides para el cluster " + cluster);

            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {

                List<TermStats> termStats = new ArrayList<>();
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                try {
                    tfvector = reader.getTermVector(docId, contentFieldName);
                    if (tfvector == null || tfvector.size() == 0) {
                        continue;
                    }
                    termsEnum = tfvector.iterator();
                    int docLen = 0;
                    while ((term = termsEnum.next()) != null) {
                        tf = (int) termsEnum.totalTermFreq();
                        termText = term.utf8ToString();
                        if (vocabularySetStats[clusterDocsIdx % numberOfCentroidsByGroup].containsKey(termText)) {
                            continue;
                        }
                        vocabularySetStats[clusterDocsIdx % numberOfCentroidsByGroup].put(termText, null);
                        termStats.add(new TermStats(termText, 1, reader));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dynamicCentroids.get(cluster).get(clusterDocsIdx % numberOfCentroidsByGroup).termVectorForTrueCentroids == null) {
                    dynamicCentroids.get(cluster).get(clusterDocsIdx % numberOfCentroidsByGroup).termVectorForTrueCentroids = new TermVector(termStats);
                    dynamicTermVectorCentroids.get(cluster).add(new TermVector(termStats));
                } else {
                    for (TermStats ts : termStats) {
                        dynamicCentroids.get(cluster).get(clusterDocsIdx % numberOfCentroidsByGroup).termVectorForTrueCentroids.add(ts);
                    }
                }
            }

        }

        int numHilos = 20;

        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int cluster = 0; cluster < K; cluster++) {
            for (int i = 0; i < numberOfCentroidsByGroup; i++) {
                try {
                    RelatedDocumentsRetriever relatedDocumentsRetriever = dynamicCentroids.get(cluster).get(i);
                    relatedDocumentsRetriever.setNumWanted(numDocs);
                    pool.execute(relatedDocumentsRetriever);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        pool.shutdown();
        while (!pool.isTerminated()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Logger.getLogger(FPAC_TC_with_L_centroids.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        long totalHits=0;
        float score=0;
        for (int cluster = 0; cluster < K; cluster++) {
            for (int i = 0; i < numberOfCentroidsByGroup; i++) {
                try {
                    score+=dynamicCentroids.get(cluster).get(i).getRelatedDocs().getMaxScore();
                    totalHits+=dynamicCentroids.get(cluster).get(i).getRelatedDocs().totalHits;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Centroides:"+numberOfCentroidsByGroup+" Score:"+(score/numberOfCentroidsByGroup)+" Total Hits:"+totalHits/numberOfCentroidsByGroup);
        return (score/numberOfCentroidsByGroup);
    }
}

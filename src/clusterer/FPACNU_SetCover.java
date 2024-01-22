/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import clusterer.utils.RelatedDocumentsRetriever;
import indexer.WMTIndexer;

import java.io.IOException;
import java.util.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author dganguly
 */
public class FPACNU_SetCover extends LuceneClusterer {

    private IndexSearcher searcher;

    @Override
    public String getAlgoName() {
        return "";
    }
    //Estos son los grupos de centroides
    public ArrayList<ArrayList<RelatedDocumentsRetriever>> dynamicCentroids;

    public ArrayList<ArrayList<TermVector>> dynamicTermVectorCentroids;
    private TermVector[][] termVectorCentroids;
    private boolean useStopThresholdCriteria = false;
    private float stopThresholdCritera;
    int initialSelectedDoc;
    boolean shouldUseRandom = false;
    List<Integer> initialCentroids;

    RelatedDocumentsRetriever[] rdes;

    public FPACNU_SetCover(Properties propFile, String dataset, int centroides) throws Exception {
        super(propFile,dataset, centroides);
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        initialSelectedDoc = 100;
        rdes = new RelatedDocumentsRetriever[K];
        // Inicializa estructura que guarda los centroides
        dynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids = new TermVector[K][numberOfCentroidsByGroup];
    }

    public FPACNU_SetCover(Properties propFile, List<Integer> initialCentroidsSeeds, String dataset, int centroides) throws Exception {
        super(propFile, dataset, centroides);
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        rdes = new RelatedDocumentsRetriever[K];
        // Inicializa estructura que guarda los centroides
        dynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids = new TermVector[K][numberOfCentroidsByGroup];
        initialCentroids = initialCentroidsSeeds;
    }

    int selectDoc(HashSet<String> queryTerms) throws IOException {

        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (String qterm : queryTerms) {
            TermQuery tq = new TermQuery(new Term(contentFieldName, qterm));
            b.add(new BooleanClause(tq, BooleanClause.Occur.MUST_NOT));
        }

        TopDocsCollector collector = TopScoreDocCollector.create(1);
        searcher.search(b.build(), collector);
        TopDocs topDocs = collector.topDocs();
        return topDocs.scoreDocs == null || topDocs.scoreDocs.length == 0 ? -1
                : topDocs.scoreDocs[0].doc;
    }

    // Initialize centroids
    // The idea is to select a random document. Grow a region around it and choose
    // as the next candidate centroid a document that does not belong to this region.
    // Same but with several centroids for each cluster
    // With the top list we select the most similar docs from an initial selected doc
    // at each iteration
    @Override
    void initCentroids() throws Exception {
        int selectedDoc = (int) (Math.random() * numDocs);
//        int selectedDoc = 25;
        int numClusterCentresAssigned = 0;
        centroidDocIds = new HashMap<>();

        for (int i = 0; i < K; i++) {
            dynamicCentroids.add(new ArrayList<>());
            dynamicTermVectorCentroids.add(new ArrayList<>());
        }

        System.out.println("El número de clusters es " + dynamicCentroids.size());

        int idxCentroidsGroup;

        // Se obtiene un centroide por cada cluster, usando la heurística del autor.
        while (numClusterCentresAssigned < K) {
            selectedDoc = initialCentroids.get(numClusterCentresAssigned);
            // Obtiene la lista top para el documento que no aparece
            // en otras listas TOP.
            System.out.println("El documento " + selectedDoc + " se elige como centroide para el cluster " + numClusterCentresAssigned);
            RelatedDocumentsRetriever rde = new RelatedDocumentsRetriever(reader, selectedDoc, prop, numClusterCentresAssigned);
//            System.out.println("Chosen doc " + selectedDoc + " as first centroid for cluster " + numClusterCentresAssigned);
//             Si no tiene lista TOP tomo otro documento
            clusterIdMap.put(selectedDoc, numClusterCentresAssigned);
            TopDocs topDocs = rde.getRelatedDocs(numDocs);
            rdes[numClusterCentresAssigned] = rde;
            if (topDocs == null) {
                selectedDoc = rde.getUnrelatedDocument(centroidDocIds, rdes);
                continue;
            }
            // Actualizo mapa
            centroidDocIds.put(selectedDoc, null);
            rdes[numClusterCentresAssigned] = rde;
            TermVector centroid = TermVector.extractAllDocTerms(reader, selectedDoc, contentFieldName, lambda);
            // Agrego a mis centroides
            dynamicCentroids.get(numClusterCentresAssigned).add(rde);
            dynamicTermVectorCentroids.get(numClusterCentresAssigned).add(centroid);

            // Actualizo el nuevo documento que puede ser un posible centroide
            selectedDoc = rde.getUnrelatedDocument(centroidDocIds, rdes);

            numClusterCentresAssigned++;

        }
    }

    @Override
    void showCentroids() throws Exception {
        for (int i = 0, j = 0; i < K; i++) {
            System.out.println("Cluster " + i + " has the centroids:");
            for (RelatedDocumentsRetriever rde : dynamicCentroids.get(i)) {
                Document doc = rde.queryDoc;
                System.out.println("Centroid " + (j++ % numberOfCentroidsByGroup) + ": " + doc.get(WMTIndexer.FIELD_DOMAIN_ID) + ", " + doc.get(idFieldName));
            }
        }
    }

    @Override
    boolean isCentroid(int docId) {
        for (int i = 0; i < K; i++) {
            for (RelatedDocumentsRetriever rde : dynamicCentroids.get(i)) {
                if (rde.docId == docId) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    int getClosestCluster(int docId) throws Exception { // O(K) computation...
        float maxScore = -1;
        int clusterId = 0;
        boolean sista = false;
        for (int i = 0; i < K; i++) {
            float localScore = 0;
            for (RelatedDocumentsRetriever rde : dynamicCentroids.get(i)) {
                if (rde.docScoreMap == null) {
                    continue;
                }
                ScoreDoc sd = rde.docScoreMap.get(docId);
                if (sd != null) {
                    localScore += sd.score;
                    sista = true;
                }
            }
            if (localScore > maxScore) {
                maxScore = localScore;
                clusterId = i;
            }
        }
//        if (!sista) System.out.println("El doc " + docId + " no está en ninguna lista");
        if (!sista) {
            // Retrieved in none... Assign to a random cluster id
            if (shouldUseRandom && Math.random() > 0.9) {
                System.out.println("El documento " + docId + " se asignó aleatoriamente al cluster " + clusterId);
                clusterId = (int) (Math.random() * K);
                numberOfDocsAssginedRandomly++;
            } // Obtiene el más cercano con la medida de similitud del coseno
            else {
                clusterId = getClosestClusterNotAssignedDoc(docId);
            }
//            shouldUseRandom = !shouldUseRandom;
        }
        return clusterId;
    }

    int getClosestClusterNotAssignedDoc(int docId) throws Exception {
        TermVector docVec = TermVector.extractAllDocTerms(reader, docId, contentFieldName, lambda);
        if (docVec == null) {
            System.out.println("Skipping cluster assignment for empty doc, because the docs is empty: " + docId);
            numberOfDocsAssginedRandomly++;
            return (int) (Math.random() * K);
        }

        float maxSim = 0, sim;
        int mostSimClusterId = 0;
        int clusterId = 0;
        for (int i = 0; i < K; i++) {
            for (TermVector centroidVec : dynamicTermVectorCentroids.get(i)) {
                if (centroidVec == null) {
                    numberOfDocsAssginedRandomly++;
//                    System.out.println("Skipping cluster assignment for empty doc because there is an empty centroid: " + docId);
//                    System.out.println("El documento " + docId  + " se asignó aleatoriamente al cluster " + clusterId);
                    return (int) (Math.random() * K);
                }
                clusterId = i;
                sim = docVec.cosineSim(centroidVec);
                if (sim > maxSim) {
                    maxSim = sim;
                    mostSimClusterId = clusterId;
                }
            }
        }
        if (Float.compare(maxSim, 0) == 0) {
            numberOfDocsAssginedRandomly++;
            clusterId = (int) (Math.random() * K);
//            System.out.println("El documento " + docId  + " se asignó aleatoriamente al cluster " + clusterId);
            return clusterId;
        }
        numberOfAssignedByCosineSim++;
//        System.out.println("El documento " + docId  + " se asignó al cluster " + clusterId + " con similitud = " + maxSim);
        return mostSimClusterId;
    }

    // Returns true if the cluster id is changed...
    @Override
    boolean assignClusterId(int docId, int clusterId) throws Exception {
        return super.assignClusterId(docId, clusterId);
    }

    ArrayList<ArrayList<Integer>> ListOfDocsForEachCluster() throws Exception {
        ArrayList<ArrayList<Integer>> docsIdForThisCluster = new ArrayList<>(K);
        for (int i = 0; i < numDocs; i++) {
            docsIdForThisCluster.get(getClusterId(i)).add(i);
        }
        return docsIdForThisCluster;
    }

    ArrayList<ArrayList<Integer>> getOldCentroids() {
        ArrayList<ArrayList<Integer>> oldCentroids = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            oldCentroids.add(new ArrayList<>());
            for (int j = 0; j < dynamicCentroids.get(i).size(); j++) {
                oldCentroids.get(i).add(dynamicCentroids.get(i).get(j).docId);
            }
        }
        return oldCentroids;
    }

    int countEqualCentroids(ArrayList<Integer> oldCentroids, int i) {
        int equals = 0;
        for (RelatedDocumentsRetriever rde : dynamicCentroids.get(i)) {
            if (Collections.binarySearch(oldCentroids, rde.docId) > 0) {
                equals++;
            }
        }
        return equals;
    }

    @Override
    float recomputeCentroids() throws Exception {
        System.out.println("Recalculando centroides");
        ArrayList<HashSet<String>> clustersVocabulary = new ArrayList<>();
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);

        int clusterId;
        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        ArrayList<ArrayList<Integer>> oldCentroids = getOldCentroids();
        // Por cada cluster se crea un conjunto donde
        // se va a guardar el vocabulario del cluster
        // y se limpian las estructuras que guardan los centroides
        // de cada cluster
        for (int i = 0; i < K; i++) {
            clustersVocabulary.add(new HashSet<>());
            docsInEachCluster.add(new ArrayList<>());
            dynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
        }

        // Por cada documento obtengo el el id del cluster al
        // que pertence y lo agrego a mi arreglo que agrupa
        // los ids de los documentos por cluster.
        // Mientras, en cada iteración se va llenando
        // el vocabulario de cada cluster.
        System.out.println("Generando vocabulario del cluster");
        for (int docId = 0; docId < numDocs; docId++) {
            clusterId = getClusterId(docId);
            if (clusterId == INITIAL_CLUSTER_ID) {
                continue;
            }
            docsInEachCluster.get(getClusterId(docId)).add(docId);
            tfvector = reader.getTermVector(docId, contentFieldName);
            if (tfvector == null || tfvector.size() == 0) {
                continue;
            }
            termsEnum = tfvector.iterator();
            while ((term = termsEnum.next()) != null) { // explore the terms for this field
                clustersVocabulary.get(clusterId).add(term.utf8ToString());
            }
        }

        // Cubrimiento del vocabulario
        for (int cluster = 0; cluster < K; cluster++) {
            System.out.println("Cubriendo el vocabulario del cluster " + cluster);
            HashSet<String> clusterVocabulary = clustersVocabulary.get(cluster);
            int idx = 0;
            int clusterVocabularyInitialSize = clusterVocabulary.size();
            Set<String> intersection;
            Set<String> bestDoc = new HashSet<>();
            int bestDocId = 0;
            HashMap<Integer, Byte> hasBeenSelected = new HashMap<>();
            float porcentajeCubierto = 0.0f;
            int documentosCubiertos = 0;
            int initialNumberOfRelatedDocs = numDocs;
            int remaining = 0;

            HashSet[] docVocabulary = new HashSet[docsInEachCluster.get(cluster).size()];
            
            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                tfvector = reader.getTermVector(docId, contentFieldName);
                if (tfvector == null || tfvector.size() == 0) {
                    continue;
                }
                docVocabulary[clusterDocsIdx] = new HashSet<>();
                termsEnum = tfvector.iterator();

                while ((term = termsEnum.next()) != null) { // explore the terms for this field
                    docVocabulary[clusterDocsIdx].add(term.utf8ToString());
                }

            }

            while (!clusterVocabulary.isEmpty()) {
                int maxCover = 0;
                for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                    if (hasBeenSelected.containsKey(clusterDocsIdx)) {
                        continue;
                    }
                    int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                    if (docVocabulary[clusterDocsIdx] == null) {
                        continue;
                    }
                    intersection = new HashSet<>(docVocabulary[clusterDocsIdx]);
                    intersection.retainAll(clusterVocabulary);
                    if (intersection.size() > maxCover) {
                        maxCover = intersection.size();
                        bestDoc = intersection;
                        bestDocId = docId;
                        //                        docVocabulary[clusterDocsIdx] = null;
                    }
                }
                if (maxCover == 0) {
                    System.out.println("No cubrí el vocabulario pero ya no había documentos que cumplieran la propiedad");
                    break;
                }
                hasBeenSelected.put(bestDocId, null);
                clusterVocabulary.removeAll(bestDoc);
                porcentajeCubierto = 100.0f - (clusterVocabulary.size() * 100.0f) / clusterVocabularyInitialSize;
                System.out.println("He cubierto " + porcentajeCubierto + "% del vocabulario del cluster con " + idx + " centroides");
                dynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, bestDocId, prop, cluster));

//                System.out.println("Con " + DynamicCentroids.get(cluster).size() + " centroides");
                dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, bestDocId, contentFieldName, lambda));
                dynamicCentroids.get(cluster).get(idx++).getRelatedDocs(initialNumberOfRelatedDocs);
                
//                documentosCubiertos += relatedDocs.scoreDocs.length;
//                if (documentosCubiertos > (numDocs / K)) {
//                    System.out.println("He cubierto " + documentosCubiertos + " documentos");
//                    break;
//                }
                if (useStopThresholdCriteria && porcentajeCubierto > stopThresholdCritera) {
                    break;
                }
            }
//            if (clusterVocabulary.isEmpty()){ System.out.println("Cubrí el vocabulario con " + idx + " centroides"); }
            //System.out.println(countEqualCentroids(oldCentroids.get(cluster), cluster) + " centroides se mantuvieron igual");
        }
        return 0;
    }

}

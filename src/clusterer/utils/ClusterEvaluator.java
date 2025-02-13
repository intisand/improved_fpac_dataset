/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer.utils;

import indexer.WMTIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis Reads in an index and evaluates the clustering... Reads the
 * cluster ids from the field cluster id... The field domain_id is taken as the
 * reference labels...
 */
public final class ClusterEvaluator {

    IndexReader reader;  // the combined index to search
    Properties prop;
    long numDocs;
    long[][] clusterClassMatrix;
    int K;
    int J;
    long classCardinalities[];
    long clusterCardinalities[];
    HashMap<Integer, Integer> clusterIdMap;
    String dataset;

    public ClusterEvaluator() {
    }

    public ClusterEvaluator(HashMap<Integer, Integer> clusterIdMap, Properties prop, String dataset) {
        try {
            this.prop = prop;
            File indexDir = new File(prop.getProperty(dataset + "." + "index"));
            reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
            this.numDocs = reader.numDocs();
            K = Integer.parseInt(prop.getProperty(dataset + "." + "clusters", "200"));
            J = Integer.parseInt(prop.getProperty(dataset + "." + "numclasses"));
            clusterClassMatrix = new long[K][J];
            this.clusterIdMap = clusterIdMap;
            initStats(clusterClassMatrix);
        } catch (IOException ex) {
            Logger.getLogger(ClusterEvaluator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(ClusterEvaluator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public ClusterEvaluator(Properties propFile, String dataset) throws Exception {
        prop = propFile;
        this.dataset = dataset;
        init();
    }

    void initClusterIdMap() throws Exception {
        FileReader fr = new FileReader(prop.getProperty("cluster.idfile"));
        BufferedReader br = new BufferedReader(fr);
        String line;

        clusterIdMap = new HashMap<>();

        while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\t");
            clusterIdMap.put(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        }
        br.close();
        fr.close();
        System.out.println("Termina de inicializar mapa de ids de clusters");
    }

    void init() throws Exception {
        File indexDir = new File(prop.getProperty(dataset + "." + "index"));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        numDocs = reader.numDocs();
        K = Integer.parseInt(prop.getProperty(dataset + "." + "clusters", "200"));
        J = Integer.parseInt(prop.getProperty(dataset + "." + "numclasses"));
        clusterClassMatrix = new long[K][J];
        initClusterIdMap();
        initStats(clusterClassMatrix);
    }

    void initSums(long[][] classClusterMatrix) throws Exception {
        clusterCardinalities = new long[K];
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < J; j++) {
                clusterCardinalities[k] += clusterClassMatrix[k][j];
            }
        }

        classCardinalities = new long[J];
        for (int j = 0; j < J; j++) {
            for (int k = 0; k < K; k++) {
                classCardinalities[j] += clusterClassMatrix[k][j];
            }
        }
    }

    void initStats(long[][] clusterClassMatrix) throws Exception {
        String domainIdWithDoubleQuotes;
        String domainIdWODoubleQuotes;
        // Compute freq of each cluster id
        for (int i = 0; i < numDocs; i++) {
            domainIdWithDoubleQuotes = reader.document(i).get(WMTIndexer.FIELD_DOMAIN_ID);
            domainIdWODoubleQuotes = domainIdWithDoubleQuotes.replaceAll("^\"|\"$", "");
            int k = clusterIdMap.get(i);
            if (k == -100) {
                k = (int) (Math.random() * J);
            }
            int j = Integer.parseInt(domainIdWODoubleQuotes);
//            System.out.printf("initStats %d %d", k, j);
            clusterClassMatrix[k][j]++;
        }
        initSums(clusterClassMatrix);
    }

    double computePurity() throws Exception {
        long sum = 0;
        long maxCount;
        for (int k = 0; k < K; k++) {
            maxCount = 0;
            for (int j = 0; j < J; j++) {
                if (clusterClassMatrix[k][j] > maxCount) {
                    maxCount = clusterClassMatrix[k][j];
                }
            }
            sum += maxCount;
        }

        return sum / (double) numDocs;
    }

    double computeNMI() throws IOException {
        double i_w_c = 0, h_w = 0, h_c = 0;
        double p_cluster_class;
        double log_component;

        for (int k = 0; k < K; k++) {
            for (int j = 0; j < J; j++) {

                if (clusterCardinalities[k] == 0 || classCardinalities[j] == 0) {
                    continue;
                }
                if (clusterClassMatrix[k][j] == 0) {
                    continue;
                }

                p_cluster_class = clusterClassMatrix[k][j] / (double) numDocs;
                log_component = (double) (Math.log((numDocs * clusterClassMatrix[k][j]) / (double) (clusterCardinalities[k] * classCardinalities[j])));
                i_w_c += p_cluster_class * log_component;

            }
        }

        for (int k = 0; k < K; k++) {
            if (clusterCardinalities[k] == 0) {
                continue;
            }
            double p_w_k = clusterCardinalities[k] / (double) numDocs;
            h_w += p_w_k * Math.log(p_w_k);
        }
        h_w = -h_w;

        for (int j = 0; j < J; j++) {
            if (classCardinalities[j] == 0) {
                continue;
            }
            double p_c_j = classCardinalities[j] / (double) numDocs;
            h_c += p_c_j * Math.log(p_c_j);
        }
        h_c = -h_c;

        return 2 * i_w_c / (h_w + h_c);
    }

    long nC2(long n) {
        return ((n - 1) * n) >> 1;
    }

    long countTP() {
        long sum = 0;
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < J; j++) {
                sum += nC2(clusterClassMatrix[k][j]);
            }
        }
        return sum;
    }

    long countTPPlusFP() {
        long sum = 0;
        for (int k = 0; k < K; k++) {
            sum += nC2(clusterCardinalities[k]);
        }
        return sum;
    }

    long countFN() {
        long sum = 0;
        for (int j = 0; j < J; j++) {
            for (int k = 0; k < K - 1; k++) {
                for (int k1 = k + 1; k1 < K; k1++) {
                    sum += clusterClassMatrix[k][j] * clusterClassMatrix[k1][j];
                }
            }
        }
        return sum;
    }

    long countTN() {
        return countTNPlusFN() - countFN();
    }

    long countTNPlusFN() {
        long sum = 0;
        for (int k = 0; k < K - 1; k++) {
            for (int k1 = k + 1; k1 < K; k1++) {
                sum += clusterCardinalities[k] * clusterCardinalities[k1];
            }
        }
        return sum;
    }

    double computeRandIndex() {
        double tp_fp = countTP() + countTN();
        double tn_fn = countTPPlusFP() + countTNPlusFN();

        return tp_fp / tn_fn;
    }

    void showClusterClassMatrix() {
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < J; j++) {
                System.out.print(clusterClassMatrix[k][j] + " ");
            }
            System.out.println();
        }
    }

    public ArrayList<String> showNewMeasures() {
        ArrayList<String> measures = new ArrayList<>();
        float accuracy = (countTP() + countTN()) / (float) (countTP() + (countTPPlusFP() - countTP()) + countFN() + countTN());
        float prec = (countTP()) / (float) (countTP() + (countTPPlusFP() - countTP()));
        float recall = (countTP()) / (float) (countTP() + countFN());
        float fscore = 2 * prec * recall / (prec + recall);

        try {
            double ri = computeRandIndex();
            double purity = computePurity();
            double nmi = computeNMI();
            System.out.println("RI  Recall  Precision   FScore  Purity  NMI");
            measures.add(String.valueOf(ri));
            measures.add(String.valueOf(recall));
            measures.add(String.valueOf(prec));
            measures.add(String.valueOf(fscore));
            measures.add(String.valueOf(purity));
            measures.add(String.valueOf(nmi));
            System.out.println(ri + " " + recall + " " + prec + " " + fscore + " " + purity + " " + nmi);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        System.out.println(accuracy + " " + prec + " " + recall + " " + fscore);
        return measures;
    }

    void testFunctionalities() throws Exception {
        K = 3;
        J = 3;
        numDocs = 17;
        clusterClassMatrix = new long[K][J];
        clusterClassMatrix[0][0] = 5;
        clusterClassMatrix[0][1] = 1;
        clusterClassMatrix[0][2] = 0;
        clusterClassMatrix[1][0] = 1;
        clusterClassMatrix[1][1] = 4;
        clusterClassMatrix[1][2] = 1;
        clusterClassMatrix[2][0] = 2;
        clusterClassMatrix[2][1] = 0;
        clusterClassMatrix[2][2] = 3;

        initSums(clusterClassMatrix);

        System.out.println("TP: " + countTP());
        System.out.println("FP  : " + (countTPPlusFP() - countTP()));
        System.out.println("FN: " + countFN());
        System.out.println("TN : " + countTN());
        System.out.println("NMI: " + computeNMI());
        System.out.println("RI: " + computeRandIndex());
        System.out.println("Purity: " + computePurity());
    }

}

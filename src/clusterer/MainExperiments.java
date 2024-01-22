/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import clusterer.utils.ClusterEvaluator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ivan
 */
public class MainExperiments {

    public static void main(String[] args) {
        try {

            String properties = "./run_properties/config.properties";

            Properties prop = new Properties();
            prop.load(new FileReader(properties));
            String[] datasets = prop.get("datasets").toString().split(",");
            List<List<Integer>> initialCentroids[] = new ArrayList[1];
            //Orden 0
            for (int i = 0; i < datasets.length; i++) {

                //Para el archivo HTML
                //////////////////////////////////////////////
                FileWriter fileWriter = new FileWriter(new File("./experimentos/txt/" + datasets[i] + ".txt"), true);
                PrintWriter printWriter = new PrintWriter(fileWriter);
                String initialHTMLString = "<!DOCTYPE html><html><head><style>table, th, td {  border: 1px solid black;  border-collapse: collapse;}th, td {  padding: 15px;  text-align: left;}</style></head><body>";
                String endHTMLString = "</body></html>";

                //printWriter.printf(initialHTMLString);
                ///////////////////////////////////////////////
                PrintStream out;

                int clusters = Integer.parseInt(prop.get(datasets[i] + "." + "clusters").toString());
                int repetir = Integer.parseInt(prop.get("repetir").toString());
                //printWriter.printf("<h2>Order %d</h2>", i + 1);
                int registros = Integer.parseInt(prop.get(datasets[i] + "." + "registros").toString());
                String centroides[] = prop.get("multiplo").toString().split(",");
                for (int j = 0; j < repetir; j++) {
                    //printWriter.printf("<h2>Seed %d</h2>", j + 1);
                    ArrayList<Integer> semillas = new ArrayList();

                    for (int k = 0; k < clusters; k++) {
                        semillas.add((int) (Math.random() * registros));
                    }

                    printWriter.printf("Dataset,Tiempo,Iteraciones,Asignados aleatoriamente,RI,Recall,Precision,FScore,Purity,NMI,Centroides\n");
                    out = new PrintStream(new FileOutputStream("./experimentos/tiempos/" + datasets[i] + "_order_" + i + "_seed_" + j + ".txt"));
                    System.setOut(out);
                    for (int n = 0; n < centroides.length; n++) {
                        LuceneClusterer fkmc = new FPAC_TC_with_L_centroids(prop, semillas, datasets[i], Integer.parseInt(centroides[n]));
                        ArrayList<String> resultsToBePrinted = fkmc.cluster(1);

                        ClusterEvaluator ceval = new ClusterEvaluator(prop, datasets[i]);
                        ArrayList<String> measures = ceval.showNewMeasures();
                        printWriter.printf("%s,", datasets[i]);

                        for (String s : resultsToBePrinted) {
                            printWriter.printf("%s,", s);
                        }
                        for (String s : measures) {
                            printWriter.printf("%s,", s);
                        }
                        printWriter.printf("%s\n", fkmc.numberOfCentroidsByGroup);
                        printWriter.flush();
                    }
                }
                printWriter.close();
            }
        } catch (Exception ex) {
            Logger.getLogger(MainExperiments.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

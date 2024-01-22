/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package clusterer.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

/**
 *
 * @author Inti
 */
public class EvaluarWilcoxon {

    HashMap<String, HashMap<String, HashMap<String, double[]>>> datasets = new HashMap<>();
    String[] listDatasets = null;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        EvaluarWilcoxon wilcoxon = new EvaluarWilcoxon();
        wilcoxon.leerArchivo();
        wilcoxon.recorrerAreglos();

    }

    public void leerArchivo() {
        File archivo = new File("./wilcoxon.txt");
        try {
            // Crea un objeto Scanner para leer el archivo
            Scanner lector = new Scanner(archivo);
            // Lee línea por línea el archivo
            while (lector.hasNextLine()) {
                String linea = lector.nextLine();
                if (linea.contains("datasets=")) {
                    listDatasets = linea.replaceAll("datasets=", "").split(",");
                }
                if (linea.contains("dataset=")) {
                    String datasetName = linea.replaceAll("dataset=", "");
                    HashMap<String, HashMap<String, double[]>> dataset = new HashMap();
                    lector.nextLine();
                    for (int i = 0; i < 3; i++) {
                        HashMap<String, double[]> medidas = new HashMap<>();
                        String metodo = lector.nextLine().replaceAll("metodo=", "");
                        double[] ri = new double[10];
                        double[] recall = new double[10];
                        double[] precision = new double[10];
                        double[] fscore = new double[10];
                        double[] purity = new double[10];
                        double[] nmi = new double[10];
                        for (int j = 0; j < 10; j++) {
                            String[] columnas = lector.nextLine().split("\t");
                            ri[j] = Double.parseDouble(columnas[0]);
                            recall[j] = Double.parseDouble(columnas[1]);
                            precision[j] = Double.parseDouble(columnas[2]);
                            fscore[j] = Double.parseDouble(columnas[3]);
                            purity[j] = Double.parseDouble(columnas[4]);
                            nmi[j] = Double.parseDouble(columnas[5]);
                        }
                        medidas.put("ri", ri);
                        medidas.put("recall", recall);
                        medidas.put("precision", precision);
                        medidas.put("fscore", fscore);
                        medidas.put("purity", purity);
                        medidas.put("nmi", nmi);
                        dataset.put(metodo.trim(), medidas);
                    }
                    datasets.put(datasetName, dataset);
                }
            }
            // Cierra el objeto Scanner
            lector.close();
        } catch (FileNotFoundException e) {
            System.out.println("No se encontró el archivo");
            e.printStackTrace();
        }
    }

    public static double wilcoxon(double[] a, double[] b) {
        // Comprobamos que las dos muestras tienen la misma longitud
        if (a.length != b.length) {
            throw new IllegalArgumentException("Las dos muestras deben tener la misma longitud");
        }

        // Calculamos las diferencias entre los valores de las dos muestras
        double[] differences = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            differences[i] = b[i] - a[i];
        }

        // Ordenamos las diferencias de menor a mayor
        Arrays.sort(differences);

        // Calculamos el estadístico de Wilcoxon
        double sumRank = 0;
        int count = 0;
        for (int i = 0; i < differences.length; i++) {
            if (differences[i] != 0) {
                sumRank += i + 1;
                count++;
            }
        }

        double wilcoxon = sumRank / Math.sqrt(count);
        System.out.println("Resultado:" + wilcoxon);
        return wilcoxon;
    }

    public void recorrerAreglos() {

        datasets.forEach((dataSetName, corpus) -> {
            System.out.println("\nDataset:" + dataSetName);
            HashMap<String, double[]> ourMethod = corpus.get("our_method");
            HashMap<String, double[]> improvedFPAC = corpus.get("improved_fpac");
            HashMap<String, double[]> varyingL = corpus.get("variyng_l");
            ourMethod.forEach((medida,ourMethodFS)->{
            double[] other = varyingL.get(medida);
            System.out.print(medida+"\t");
            wilcoxonSignedRankTest(ourMethodFS, other);
            });
        });
    }

    public static double wilcoxonSignedRankTest(double[] a, double[] b) {
        // Comprobamos que las dos muestras tienen la misma longitud
        if (a.length != b.length) {
            throw new IllegalArgumentException("Las dos muestras deben tener la misma longitud");
        }

        // Calculamos las diferencias entre los valores de las dos muestras
        double[] differences = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            differences[i] = b[i] - a[i];
        }

        // Ordenamos las diferencias por valor absoluto y guardamos el signo original
        double[] absoluteDifferences = Arrays.stream(differences)
                .map(Math::abs)
                .toArray();
        int[] signs = new int[differences.length];
        for (int i = 0; i < differences.length; i++) {
            signs[i] = (int) Math.signum(differences[i]);
        }
        ArrayIndexComparator comparator = new ArrayIndexComparator(absoluteDifferences);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);

        // Calculamos el estadístico de Wilcoxon con signo
        double w = 0;
        int n = differences.length;
        for (int i = 0; i < n; i++) {
            double rank = i + 1;
            if (absoluteDifferences[indexes[i]] != 0) {
                w += signs[indexes[i]] * rank;
            }
        }
        double stdDev = Math.sqrt((double) n * (n + 1) * (2 * n + 1) / 6);
        double z = w / stdDev;
        System.out.println("W:"+w+"\tZ:"+z);
        return z;
    }
}

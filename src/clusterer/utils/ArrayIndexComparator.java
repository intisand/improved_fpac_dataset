/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package clusterer.utils;

import java.util.Comparator;

/**
 *
 * @author Inti
 */
public class ArrayIndexComparator implements Comparator {

    private final double[] array;

    public ArrayIndexComparator(double[] array) {
        this.array = array;
    }

    public Integer[] createIndexArray() {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            indexes[i] = i;
        }
        return indexes;
    }

    @Override
    public int compare(Object o1, Object o2) {
        double value1 = array[(Integer)o1];
        double value2 = array[(Integer)o2];
        if (value1 < value2) {
            return -1;
        } else if (value1 > value2) {
            return 1;
        } else {
            return (Integer)o1 - (Integer)o2;
        }
    }

}

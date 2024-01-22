/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer.utils;

/**
 *
 * @author inti
 */
public class ClaculoL {

    public ClaculoL() {
    }

    public static void main(String[] args) {
        double[] arrayX={211.87,75.76,1.30,46.63,20.58,57.68,2.82,9.96,108.51,17.88,44.41,44.03,7.35};
        for(int i=0;i<arrayX.length;i++){
        double x=arrayX[i];
        System.out.println("X:"+x+"\tL:"+(int)((-20 * Math.log(x)) +150)); //-16.41ln(x)
        //System.out.println("X:"+x+"\tL:"+(int) ((-35 * Math.log(x)) + 210)); //-16.41ln(x)
        //System.out.println("X:"+x+"\tL:"+(int)(0.0032 * Math.pow(x, 2) - (1.3479 * x) + 165));
        //System.out.println("X:"+x+"\tL:"+(int)((-0.00216 * Math.pow(x, 2)) + (1.3561 * x) + 150));
        }
        //y = -0.0216x2 + 1.3561x + 152.49

        
    }
}

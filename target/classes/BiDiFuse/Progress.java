/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package BiDiFuse;

import ij.IJ;
import java.util.LinkedHashMap;

/**
 *
 * @author Michael
 */
public class Progress {

    final static int START = 0;
    final static int INIT = 20;
    final static int ROT_XY = 40;
    final static int ROT_Z = 60;
    final static int TRIM = 70;
    final static int SIFT = 80;
    final static int MERGE = 90;
    final static int END = 100;
    
    /**
     * 
     * @param statusStr should give a readable pointer to the 
     */
    public static void update( int status ) {
        
        int i = status;
        int n = 100;
        IJ.showProgress( i, n );
    }

}

/*
 * BiDiFuse: FIJI plugin for bi-directional registration of 3D image stacks 
 * Copyright (C) 2016 Jan Detrez
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package BiDiFuse;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortBlitter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import mpicbg.ij.InverseTransformMapping;
import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NoninvertibleModelException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;


/**
 *
 * @author Michael
 */
public class Correlation {
    
    	public static ArrayList<PointMatch> roiToMatch( PointRoi roiSource, PointRoi roiTarget) {
		ArrayList<PointMatch> p = new ArrayList<PointMatch>(); 
		int n = roiTarget.getPolygon().npoints;
		int[] xs = roiSource.getPolygon().xpoints;
		int[] ys = roiSource.getPolygon().ypoints;
		int[] xt = roiTarget.getPolygon().xpoints;
		int[] yt = roiTarget.getPolygon().ypoints;
		for (int i = 0; i < n; i++) {
			double xSource = (double) xs[i];
			double ySource = (double) ys[i];
			double xTarget = (double) xt[i];
			double yTarget = (double) yt[i];
			PointMatch match = new PointMatch( new Point( new double[]{xSource, ySource} ), new Point( new double[]{xTarget, yTarget} ) );
			p.add(match);
		}
		return p;
	}
	
	public static ArrayList<PointMatch> siftRoi( ImagePlus impSource, ImagePlus impTarget ) {

		// ------------------------------------------
		// Obtain the class and class method through reflection since it is in the default package
		// ------------------------------------------
		String className = "SIFT_ExtractPointRoi";
		String methodName = "exec";
		Class[] paramObjectArray = new Class[]{ ImagePlus.class, ImagePlus.class, int.class };
		Class SiftClass = null;
		
 		try {
 			SiftClass = Class.forName( className );
 		} catch (ClassNotFoundException e2) {
 			e2.printStackTrace();
 		}
         Method sift = null;
 		try {
 			sift = SiftClass.getMethod( methodName, paramObjectArray ); //new Class[] { String.class });
 		} catch (NoSuchMethodException | SecurityException e1) {
 			e1.printStackTrace();
 		}
		
		// Rigid model 1, Affine model 3
        int modelId = 3;
		// Invoke the method (reflection)
		try {
			sift.invoke( SiftClass.newInstance(), impTarget, impSource, modelId );
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
                    e.printStackTrace();
                    Logger.getLogger(Correlation.class.getName()).log(Level.SEVERE, null, e);
                }
		PointRoi roiTarget = (PointRoi) impTarget.getRoi();
		PointRoi roiSource = (PointRoi) impSource.getRoi();
		// Convert roi's to pointmatch array for function AlignStacksWithLandmarks
		ArrayList<PointMatch> p = null;
		if (roiSource == null) {
			p = new ArrayList<PointMatch>();
		} else {
			p = roiToMatch( roiSource, roiTarget);
		}
		return p;
	}

	// stackSource will be verso = stackB (the stack which will be transformed)
	public static ImagePlus alignStack( ImagePlus sliceSource, ImagePlus sliceTarget, ImagePlus stackSource ) {

            IJ.log("--------------------------------------------------------");
            //sliceSource.show();
            //sliceTarget.show();
            double cc_preSift = computeCorrelation( sliceSource, sliceTarget );
            
	    ImagePlus impAligned = null;

	    // Get SIFT points from these two slices
	    final Collection< PointMatch > pointMatches = siftRoi( sliceSource, sliceTarget );
		double[] middle_post_P1_XY = new double[]{ sliceSource.getWidth()/2.0, sliceSource.getHeight()/2.0 };
	    if ( pointMatches.size() < 7 ) {
	    	IJ.log("Not enough SIFT correspondences found for post-registration SIFT method, cancelling post-registration.");
		    stackSource.setRoi( (int) Math.round( middle_post_P1_XY[0] ), (int) Math.round( middle_post_P1_XY[1] ),1,1 );
	    	return stackSource;
	    }

	    // Get the transformation from the SIFT points
	    try {
	    	AffineModel2D model = new AffineModel2D();
	    	model.fit( pointMatches );
	    	ImagePlus impS1 = sliceSource.duplicate();
	    	ImagePlus impT1 = sliceTarget.duplicate();
	    	impS1.getProcessor().setInterpolationMethod( ImageProcessor.BILINEAR );
	    	impT1.getProcessor().setInterpolationMethod( ImageProcessor.BILINEAR );
		InverseTransformMapping mapping = new InverseTransformMapping( model );
		middle_post_P1_XY = mapping.getTransform().applyInverse( new double[]{ impS1.getWidth()/2.0, impS1.getHeight()/2.0 } );
	
	        // Apply the transformation to all the slices of the source stack (verso) (in a virtual stack way)
		ImageStack stack = stackSource.getStack();
		ImageStack stackAligned = ImageStack.create( impS1.getWidth(), impS1.getHeight(), stack.getSize(), stack.getBitDepth() );
		stack = stackSource.getStack();
		for ( int i = 1; i <= stack.getSize(); i++ ) {
                    ImageProcessor ipAligned = impS1.getProcessor().createProcessor( impS1.getWidth(), impS1.getHeight() );
                    mapping.mapInterpolated( stack.getProcessor(i), ipAligned );
                    stackAligned.setProcessor(ipAligned, i);
		}
                //impS1.show();
                impAligned = new ImagePlus( "Aligned", stackAligned );

                // post-registration correlation
                ImageProcessor sliceSourceReg =  impS1.getProcessor().createProcessor( impS1.getWidth(), impS1.getHeight() );
                mapping.mapInterpolated( impS1.getProcessor(), sliceSourceReg );
                double cc_postSift = computeCorrelation( new ImagePlus( "imp sliceSourceReg", sliceSourceReg ), sliceTarget );
                IJ.log("--------------------------------------------------------");
                IJ.log("SIFT results:");
                IJ.log("Before (Pearson correlation): " + String.format("%6f", cc_preSift) );
                IJ.log("After (Pearson correlation): " + String.format("%6f", cc_postSift) );

            } catch (NotEnoughDataPointsException | IllDefinedDataPointsException e) {
                impAligned = stackSource;
                e.printStackTrace();
            } catch (NoninvertibleModelException e) {
                impAligned = stackSource;
                e.printStackTrace();
            }
	    // TODO is dit roi correct?
	    impAligned.setRoi( (int) Math.round( middle_post_P1_XY[0] ), (int) Math.round( middle_post_P1_XY[1] ) ,1,1 );

            return impAligned;
	}

    /**
     * The normalized crossCorrelation is defined as 
     * NCC(X,Y) = sum_i((Xi-X)*(Yi-Y)) / sqrt( sum_i(Xi-X)^2 * sum_i(Yi-Y)^2 )
     * 
     * @return normalized crossCorrelation
     */
    public static double computeCorrelation( ImagePlus imp1, ImagePlus imp2 ) {
        double ncc = 0;
        ImageProcessor ip1 = imp1.getProcessor();
        ImageProcessor ip2 = imp2.getProcessor();
        ImageStatistics stats1 = ip1.getStatistics();
	ImageStatistics stats2 = ip2.getStatistics();
	ImageProcessor ipd1 = ip1.duplicate().convertToFloat();
	ImageProcessor ipd2 = ip2.duplicate().convertToFloat();
	ipd1.subtract(stats1.mean);
	ipd2.subtract(stats2.mean);
	double denom = Math.sqrt( sumOfSquares( ipd1 ) * sumOfSquares( ipd2 ) );
	double num = sumOfProduct( ipd1, ipd2 );
	ncc = num / denom;

        return ncc;
    }
        
    public static double sumOfSquares( ImageProcessor ip ) {

        ip = ip.duplicate();
        ip.resetRoi();
        ip.sqr();
        ImageStatistics stats = ip.getStatistics();

        return stats.area * stats.mean;
    }

    public static double sumOfProduct( ImageProcessor ip1, ImageProcessor ip2 ) {
	
        ImageProcessor ip1Temp = ip1.duplicate().convertToFloat();
        ImageProcessor ip2Temp = ip2.duplicate().convertToFloat();
        ip1Temp.resetRoi();
        ip2Temp.resetRoi();
        ip1Temp.copyBits(ip2Temp, 0, 0, Blitter.MULTIPLY);
        ImageStatistics stats = ip1Temp.getStatistics();

        return stats.mean * stats.area;
    }
}

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

import static BiDiFuse.BiDiFuse_Fusion.ORIENTATION_XY;
import static BiDiFuse.BiDiFuse_Fusion.ORIENTATION_YZ;
import static BiDiFuse.BiDiFuse_Fusion.ORIENTATION_XZ;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.gui.NewImage;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
//import bdv.img.virtualstack.*;
//import mpicbg.spim.data.generic.sequence.BasicImgLoader;
//import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import ij.ImageJ;
import imagescience.image.Image;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
//import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import java.util.logging.Level;
import java.util.logging.Logger;
import mpicbg.models.AffineModel2D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.InvertibleBoundable;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
//import net.imglib2.algorithm.transformation.ImageTransform;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
//import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
//import net.imglib2.ui.overlay.LogoPainter;
//import net.imglib2.ui.viewer.InteractiveViewer3D;
import net.imglib2.view.TransformView;
import net.imglib2.view.Views;
import net.imglib2.Cursor;
import net.imglib2.FinalRealInterval;

/**
 *
 * @author Michael
 */
public class XVirtualStack extends VirtualStack {

    /** Default constructor. */
    //public XVirtualStack() { }

    /** Creates a new, empty virtual stack. */
    /*public XVirtualStack(int width, int height, ColorModel cm, String path) {
        super(width, height, cm, path);
    }*/
    
    /** 
     * Wrap virtual stack into Img (Can we use it directly in imglib2?)
     */
/*    public void virtualImg( ImagePlus imp ) {
        // create ImgLoader wrapping the image
        final BasicImgLoader imgLoader;
	if ( imp.getStack().isVirtual() ) {
            switch ( imp.getType() ) {
            case ImagePlus.GRAY8:
		imgLoader = VirtualStackImageLoader.createUnsignedByteInstance( imp );
                break;
            case ImagePlus.GRAY16:
		imgLoader = VirtualStackImageLoader.createUnsignedShortInstance( imp );
                break;
            case ImagePlus.GRAY32:
		imgLoader = VirtualStackImageLoader.createFloatInstance( imp );
                break;
            case ImagePlus.COLOR_RGB:
                imgLoader = VirtualStackImageLoader.createARGBInstance( imp );
                break;
            default:
                break;
            }
        }
        
    }*/
    
    /** Does nothing.. */
/*    public void addSlice(String sliceLabel, ImageProcessor ip) {
    }
    
    public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
    }

    public int saveChanges(int n) {
        return -1;
    }
*/
    
/*    
    public static ImagePlus selectSlice(ImagePlus virtualStack, String orientation, int slicePosition) {

        ImagePlus imp = null;
        ImagePlus impSlice = null;
        ImageProcessor ip = null;
        int w, h;
        ImageStack stack = virtualStack.getStack();
        int n = stack.getSize();

        if (orientation.equals(ORIENTATION_XY)) {
            impSlice = new ImagePlus("xy - slice", stack.getProcessor(slicePosition));
        }
        if (orientation.equals(ORIENTATION_XZ)) {
            w = stack.getWidth();
            h = 1;
            impSlice = NewImage.createImage("xz - slice", w, n, 1, stack.getBitDepth(), NewImage.FILL_BLACK);
            for (int i = 0; i < n; i++) {
                ip = stack.getProcessor(i + 1);
                ip.setRoi(new Rectangle(0, slicePosition - 1, w, h));
                int xloc = 0;
                int yloc = i;
                impSlice.getProcessor().copyBits(ip.crop(), xloc, yloc, Blitter.COPY);
            }
        }
        if (orientation.equals(ORIENTATION_YZ)) {
            w = 1;
            h = stack.getHeight();
            impSlice = NewImage.createImage("yz - slice", h, n, 1, stack.getBitDepth(), 0);
            for (int i = 0; i < n; i++) {
                ip = stack.getProcessor(i + 1);
                ip.setRoi(new Rectangle(slicePosition - 1, 0, w, h));
                ImageProcessor ipTemp = ip.crop();
                ipTemp = ipTemp.rotateLeft();
                int xloc = 0;
                int yloc = i;
                impSlice.getProcessor().copyBits(ipTemp, xloc, yloc, Blitter.COPY);
            }
        }
        return impSlice;
    }
*/
 
/**
 * Create a new ImgLib2 Img of Type FloatType
 */
    public static void Example1c() {
        // create the ImgFactory based on cells (cellsize = 5x5x5...x5) that will
        // instantiate the Img
        final ImgFactory< FloatType > imgFactory = new CellImgFactory< FloatType >( 5 );
 
        // create an 3d-Img with dimensions 20x30x40 (here cellsize is 5x5x5)Ø
        final Img< FloatType > img1 = imgFactory.create( new long[]{ 20, 30, 40 }, new FloatType() );
 
        // create another image with the same size
        // note that the input provides the size for the new image as it implements
        // the Interval interface
        final Img< FloatType > img2 = imgFactory.create( img1, img1.firstElement() );
 
        // display both (but they are empty)
        ImageJFunctions.show( img1 );
        ImageJFunctions.show( img2 );
    }
 
    
    /*
    public static void Example1d() throws ImgIOException
    {
        // open file as float with ImgOpener
        Img< ShortType > img =
            new ImgOpener().openImgs( "c:/Users/Michael/Desktop/test.tif", new ShortType() ).get(0);
 
        // display image
        ImageJFunctions.show( img );
 
        // use a View to define an interval (min and max coordinate, inclusive) to display
        //ClampingNLinearInterpolatorFactory interpol = new ClampingNLinearInterpolatorFactory();
        
        //RealRandomAccess ra = interpol.create( img );
                //Views.interval( img, new long[] { 200, 200 }, new long[]{ 500, 350 } );
        //ImageJFunctions.show( (RandomAccessibleInterval) ra);

        AffineModel2D transform = new AffineModel2D();
        transform.set(1, 0, 2, 0, 0, 0);
        //transform.rotate(1, 1);
        ImageTransform it = new ImageTransform( img,
                img,
                transform,
                new ClampingNLinearInterpolatorFactory(),
                new ArrayImgFactory< ShortType >());
        it.process();
        Img img2 = it.getResult();
        ImageJFunctions.show( img2 );

        // display only the part of the Img
        //ImageJFunctions.show( view );

        // or the same area rotated by 90 degrees (x-axis (0) and y-axis (1) switched)
//        ImageJFunctions.show( Views.rotate( view, 0, 1 ) );

        final AffineTransform3D sourceTransform = new AffineTransform3D();
        double pw = 10.0;
        double ph = 5.0;
        double pd = 3.0;
        sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );
        //sourceTransform.
        //ImageJFunctions.show( new TransformView( view, sourceTransform) );
    }
    */
    
    /*
    public static void ExampleCatmaid() throws ImgIOException {
        Img< FloatType > img;
        img = new ImgOpener().openImgs( "c:/Users/Michael/Desktop/test.tif", new FloatType() ).get(0);
 
        // display image
        ImageJFunctions.show( img );
 
        // use a View to define an interval (min and max coordinate, inclusive) to display
        RealRandomAccessible< FloatType > view = 
                Views.interval( img, new long[] { 200, 200 }, new long[]{ 500, 350 } );

        final int w = 400, h = 300;
	final double yScale = 1.0, zScale = 12.0;
	final AffineTransform3D initial = new AffineTransform3D();
	initial.set(
		1.0, 0.0, 2.0, ( w ) / 2.0,
		-1.0, yScale, 2.0, ( h * yScale ) / 2.0,
        	0.0, 0.0, zScale, - ( 1 - 0.5 ) * zScale );
        RealViews.affine( view, initial);
	final RandomAccessible< FloatType > extended = Views.extendValue( view, new FloatType() );
        
        ImageJFunctions.show( view );
        //ImageJFunctions.show( new TransformView( view, initial ) );
	//final InteractiveViewer3D< FloatType > viewer = new InteractiveViewer3D< FloatType >( w, h, extended, view, initial, null );
	//viewer.getDisplayCanvas().addOverlayRenderer( new LogoPainter() );
        //viewer.requestRepaint();
    }*/

    public static < T extends Type< T > > Img< T > transformImage( RealRandomAccessible< T > source,
        RealInterval interval, ImgFactory< T > factory, AffineTransform3D transform ) {
        
        int numDimensions = interval.numDimensions();
        // compute the number of pixels of the output and the size of the real interval
        long[] pixelSize = new long[ numDimensions ];
        pixelSize = new long[]{100,100,5};
        
        // create the output image
        Img< T > output = factory.create( pixelSize, source.realRandomAccess().get() );
 
        // cursor to iterate over all pixels
        Cursor< T > cursor = output.localizingCursor();
 
        // create a RealRandomAccess on the source (interpolator)
        RealRandomAccess< T > realRandomAccess = source.realRandomAccess();
 
        // the temporary array to compute the position
        double[] tmp = new double[ numDimensions ];
 
        // for all pixels of the output image
        double[] tmpLoc = {0,0,0};
        while ( cursor.hasNext() )
        {
            cursor.fwd();
 
            // compute the appropriate location of the interpolator
            cursor.localize(tmpLoc);
            transform.apply( tmp, tmpLoc );
 
            // set the position
            realRandomAccess.setPosition( tmp );
 
            // set the new value
            cursor.get().set( realRandomAccess.get() );
        }
        
        return output;
    }
    
    public static void main( String[] args )
    {
        try {
            // open an ImageJ window
            new ImageJ();
            
            String pathRect = "F:/MB/BiDiFuse_7008brainx10Wz1Ex543stackrecto.tif";
            //ImagePlus imp = IJ.openImage(pathRect);
            //imp.show();
            
            Img< ShortType > img;
            img = new ImgOpener().openImgs( pathRect, new ShortType() ).get(0);
            
            // display image
            ImageJFunctions.show( img );
           
            // use a View to define an interval (min and max coordinate, inclusive) to display
            RandomAccessibleInterval< ShortType > view = 
                    Views.interval( img, new long[] { 200, 200, 5 }, new long[]{ 500, 350, 10 } );
            
            ImageJFunctions.show( view );
                  
            // create an InterpolatorFactory RealRandomAccessible using linear interpolation
            NLinearInterpolatorFactory< ShortType > facInterpolation =
            new NLinearInterpolatorFactory< ShortType >();
            
            RealRandomAccessible< ShortType > interpolant = Views.interpolate(
            Views.extendMirrorSingle( img ), facInterpolation );
            
            // define the area in the interpolated image
            double[] min = new double[]{ 300, 400, 5 };
            double[] max = new double[]{ 300, 400, 10 };
 
            FinalRealInterval interval = new FinalRealInterval( min, max );
            
            
            //Interval<FloatType> i = new Interval
            //
            final double yScale = 1.0, zScale = 1.0;
            final AffineTransform3D transform = new AffineTransform3D();
            transform.set(
		1.0, 0.0, 0, 0,
		0, yScale, 0, 0,
        	0.0, 0.0, zScale, 0 );
            transform.rotate(2, 45);
            
            Img< ShortType > out = transformImage( interpolant, interval, new ArrayImgFactory< ShortType >(), transform );
            
            ImageJFunctions.show( out );

            
            
            
            
            
            
            
            
            
            
            
            
            
            
            //Views.extendZero();//.affine( view, initial);

            
            // use a View to define an interval (min and max coordinate, inclusive) to display
            //RandomAccessible< FloatType > view =
            //        Views.interval( img, new long[] { 200, 200 }, new long[]{ 500, 350 } );
            
            //ImageJFunctions.show((RandomAccessibleInterval<FloatType>) view);
            
            //Image img = imagescience.image.Image.wrap(imp);
            
            //img.imageplus().show();
            
            // run the example
            //Example1c();
            //try {
            //Example1d();
            //ExampleCatmaid();
            //} catch (ImgIOException ex) {
//            Logger.getLogger(XVirtualStack.class.getName()).log(Level.SEVERE, null, ex);
//      }
        } catch (ImgIOException ex) {
            Logger.getLogger(XVirtualStack.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

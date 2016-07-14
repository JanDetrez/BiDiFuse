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

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.MessageDialog;
import ij.gui.NewImage;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.Concatenator;
import ij.plugin.WindowOrganizer;
import ij.plugin.Duplicator;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.StackReverser;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.util.Tools;
import imagescience.transform.Rotate;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import static java.lang.Double.NaN;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class BiDiFuse_Fusion implements PlugIn {
    int width;
    int height;
    int channels;
    int stackA_slices;
    int stackB_slices;
    int frames;
    ImagePlus stackA;
    ImagePlus stackA_Channel;
    ImagePlus stackB;
    ImagePlus stackB_Channel;
    ImagePlus smoothImage;

    String stackA_title;
    String stackB_title;
    int xStackA_P1_pixels;
    int yStackA_P1_pixels;
    int xStackB_P1_pixels;
    int yStackB_P1_pixels;
    int zStackA_P1;
    int zStackB_P1;
    int zStackB_P1_reversed;
    int zStackB_P1_original;
    float X_angle_deg;
    float Y_angle_deg;
    float Z_angle_deg_B_P1P2;
    float Z_angle_deg;
    float Z_angle_deg_B_P1P2_inv;
    float Z_angle_total_deg;

    // Options
    boolean rotateVirtual;
    boolean postRegistration;
    boolean showComposite;
    int interpolation;
    int rangeSmooth;
    String methodSmooth;

    int stackA_selectedTransitionPoint;
    int stackB_selectedTransitionPoint;
    int stackB_selectedTransitionPoint_original;

    final static String ORIENTATION_XZ = "xz-plane";
    final static String ORIENTATION_YZ = "yz-plane";
    final static String ORIENTATION_XY = "xy-plane";

    boolean debug = false;

    String imagedir = null;
    String tempDir = null;
    
    public void run(String arg) {
        IJ.log("---- BiDiFuse start fusion -----");
        //try{
           fusion(); 
        //}catch (Exception e){
        //    IJ.log("An error occured, from which BiDiFuse could not recover. Please contact Jan Detrez (Jan.Detrez@uantwerpen.be) with the following error attached: \n" + e.getMessage());
        //}
        new WindowOrganizer().run("tile");
        IJ.log("---- BiDiFuse end fusion -----");
    }

    public void fusion() {
        new WindowOrganizer().run("tile");
        //***************************************//
        //*****Find txt files for open images****//
        //***************************************//
        boolean txtpathfound = false;
        String txtpath = "";
        int[] idList = WindowManager.getIDList();
        if (idList == null) {
            MessageDialog md = new MessageDialog(new Frame(), "BiDiFuse", "No images found. BiDiFuse Fusion requires that two images are open.");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        } else if (idList.length < 2) {
            MessageDialog md = new MessageDialog(new Frame(), "BiDiFuse", "No images found. BiDiFuse Fusion requires that two images are open.");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        } else if (!WindowManager.getImage(idList[0]).getTitle().contains("BiDiFuse") || !WindowManager.getImage(idList[1]).getTitle().contains("BiDiFuse")) {
            MessageDialog md = new MessageDialog(new Frame(), "BiDiFuse", "Run BiDiFuse Registration or open BiDiFuse images");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        } else {
            this.imagedir = IJ.getDirectory("image");
            if ( this.imagedir == null ) {
                this.imagedir = IJ.getDirectory("Select folder with fusion coordinates:");
            }
            this.tempDir = IJ.getDirectory("temp") + "/BiDiFuse_temp";

            for ( int i = 0; i < idList.length; i++ ) {
                ImagePlus image = WindowManager.getImage(idList[i]);
                String imtitle = image.getTitle();

                if (imagedir.charAt(imagedir.length() - 1) != '/') {
                    imagedir = imagedir + "/";
                }
                txtpath = imagedir + imtitle + " Fusion coordinates.txt";
                File file = new File(txtpath);
                if (file.exists()) {
                    txtpathfound = true;
                    break;
                }
            }
        }

        //****************************************//
        //*****Read coordinates from txt file*****//
        //****************************************//
        if (txtpathfound) {
            IJ.log("Reading " + txtpath);
        } else {
            MessageDialog md = new MessageDialog(new Frame(), "BiDiFuse", "No coordinates file (*.txt) found. Set landmarks first using BiDiFuse Registration!");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        }
        //Split txtfile in array of strings
        String txtpath_string = IJ.openAsString(txtpath);
        String[] txtpath_split_onreturn = Tools.split(txtpath_string, ";\n");
        String[] txtpath_split_ontab_variables = new String[txtpath_split_onreturn.length];
        for (int i = 0; i < txtpath_split_onreturn.length; i++) {
            String[] txtpath_split_ontab = Tools.split(txtpath_split_onreturn[i], "\t");
            txtpath_split_ontab_variables[i] = txtpath_split_ontab[1];
        }
        //Assign parameters
        stackA_title = txtpath_split_ontab_variables[0];
        stackB_title = txtpath_split_ontab_variables[1];
        xStackA_P1_pixels = Integer.parseInt(txtpath_split_ontab_variables[2]);
        yStackA_P1_pixels = Integer.parseInt(txtpath_split_ontab_variables[3]);
        xStackB_P1_pixels = Integer.parseInt(txtpath_split_ontab_variables[4]);
        yStackB_P1_pixels = Integer.parseInt(txtpath_split_ontab_variables[5]);
        zStackA_P1 = Integer.parseInt(txtpath_split_ontab_variables[6]);
        zStackB_P1 = Integer.parseInt(txtpath_split_ontab_variables[7]);
        X_angle_deg = Float.parseFloat(txtpath_split_ontab_variables[8]);
        Y_angle_deg = Float.parseFloat(txtpath_split_ontab_variables[9]);
        Z_angle_deg_B_P1P2 = Float.parseFloat(txtpath_split_ontab_variables[10]);
        Z_angle_deg = Float.parseFloat(txtpath_split_ontab_variables[11]);
        zStackB_P1_original = zStackB_P1;
        //zStackB_P1_reversed = stackB_slices - zStackB_P1 + 1;

        //*********************//
        //*****Read images*****//
        //*********************//
        stackA = WindowManager.getImage(stackA_title);
        stackB = WindowManager.getImage(stackB_title);

        
        //Extract dimensions
        int[] dim = stackB.getDimensions();
        width = dim[0];
        height = dim[1];
        channels = dim[2];
        stackB_slices = dim[3];
        frames = dim[4];
        dim = stackA.getDimensions();
        stackA_slices = dim[3];

        //***********************************************************//
        //*****GUI setup: Fusion Point, Smoothing, Interpolation*****//
        //***********************************************************//
        String radiobuttonChannnelChoice = "";
        GenericDialog gd = new GenericDialog("BiDiFuse Fusion");
        gd.addMessage("BiDiFuse", new Font(Font.DIALOG, Font.BOLD, 14), Color.BLACK);
        gd.addMessage("A plugin for fusing bi-directionally recorded microscopic image volumes", new Font(Font.DIALOG, Font.ITALIC, 12), Color.BLACK);
        gd.addMessage("Transition", new Font(Font.DIALOG, Font.BOLD, 13), Color.BLACK);
        int offset_StackB_StackA = zStackB_P1 - zStackA_P1;
        //Enable choice of channel
        if (channels > 1) {
            String[] items = new String[channels + 1];
            items[0] = "all";
            for (int i = 1; i < channels + 1; i++) {
                items[i] = Integer.toString(i);
            }
            gd.addRadioButtonGroup("Multiple channels detected.\nWhich channels should be fused?", items, 1, 4, "all");
            gd.addMessage("");
            radiobuttonChannnelChoice = gd.getNextRadioButton();
        } else {
            radiobuttonChannnelChoice = "1";
        }
        //Choose transition point
        gd.addSlider("                Transition point ", 1, stackA_slices, zStackA_P1);
        //Determine fusion point based on image intensity & image sharpness (range: 10%-90% of size stack)

        // -----------------------------------------------------------------------------------------------------
        //showTransitionCurves( offset_StackB_StackA, 0 );
        // -----------------------------------------------------------------------------------------------------
        
        int[] fusionPoints = DetermineFusionPoint(offset_StackB_StackA);
        gd.setInsets(0, 20, 0);
        gd.addMessage("Transition point based on first landmark: " + zStackA_P1);
        gd.setInsets(0, 20, 0);
        if (fusionPoints[0] < 0.10 * stackA_slices || fusionPoints[0] > 0.90 * stackA_slices) {
            gd.addMessage("Transition point based on image intensity: not found");
        } else {
            gd.addMessage("Transition point based on image intensity: " + fusionPoints[0]);
        }
        gd.setInsets(0, 20, 0);
        if (fusionPoints[1] < 0.10 * stackA_slices || fusionPoints[1] > 0.90 * stackA_slices) {
            gd.addMessage("Transition point based on image sharpness: not found");
        } else {
            gd.addMessage("Transition point based on image sharpness: " + fusionPoints[1]);
        }
        gd.setInsets(10, 20, 0);

        //Choose smoothing method & range
        gd.addMessage("Blending", new Font(Font.DIALOG, Font.BOLD, 13), Color.BLACK);
        gd.addSlider("Blending range", 0, stackA_slices * 0.9, 10);
        final String[] itemsSmooth = {"Linear weighted sum", "Max", "Min", "Average", "Sum"};
        gd.addChoice("Blending method", itemsSmooth, "Linear");

        // Options of the registration
        // ---------------------------
        gd.addMessage("Additional Options", new Font(Font.DIALOG, Font.BOLD, 13), Color.BLACK);
        //Choose interpolation method
        final String[] itemsInterpolation = {"None", "Linear"};
        final int[] interpolationTypes = {Rotate.NEAREST, Rotate.LINEAR};
        gd.addChoice("Rotation interpolation", itemsInterpolation, "None");
        //Enable virtual computation
        gd.setInsets(5, 20, 0);
        gd.addCheckbox("Virtual rotation (slow!)", false);
        // Post registration step
        gd.setInsets(0, 20, 0);
        gd.addCheckbox("Post-hoc registration", false);

        // show the dialog
        gd.showDialog();
        if (gd.wasCanceled()) {
            IJ.log("BiDiFuse Cancelled");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        }


        //**********************************//
        //*****Read user-input from GUI*****//
        //**********************************//
        stackA_selectedTransitionPoint = (int) gd.getNextNumber();
        rangeSmooth = (int) gd.getNextNumber();
        methodSmooth = gd.getNextChoice();
        interpolation = interpolationTypes[gd.getNextChoiceIndex()];
        rotateVirtual = gd.getNextBoolean();
        postRegistration = gd.getNextBoolean();
        stackB_selectedTransitionPoint = stackA_selectedTransitionPoint + offset_StackB_StackA; //TODO
        stackB_selectedTransitionPoint_original = stackB_selectedTransitionPoint;
        //stackB_selectedTransitionPoint_reversed = stackB_slices - stackB_selectedTransitionPoint +1;

        if (channels > 1) {
            radiobuttonChannnelChoice = gd.getNextRadioButton();
        }

        //****************//
        //*****Fusion*****//
        //****************//
        //All channels
        if (radiobuttonChannnelChoice.equalsIgnoreCase("all")) {
            for (int channel_to_rotate = 1; channel_to_rotate < channels + 1; channel_to_rotate++) {
                //Duplicator duplicator = new Duplicator();
                //stackB_Channel = duplicator.run(stackB, channel_to_rotate, channel_to_rotate, 1, stackB_slices, 0, 0);
                //stackA_Channel = duplicator.run(stackA, channel_to_rotate, channel_to_rotate, 1, stackA_slices, 0, 0);
                this.stackA_Channel = new ImagePlus( "stackA_channel", ChannelSplitter.getChannel(stackA, channel_to_rotate) );
                this.stackB_Channel = new ImagePlus( "stackB_channel", ChannelSplitter.getChannel(stackB, channel_to_rotate) );
                BiDiFuseFusion(channel_to_rotate);
            }
        } //One channel
        else {
            int channel_to_rotate = Integer.parseInt(radiobuttonChannnelChoice);
            //Duplicator duplicator = new Duplicator();
            //stackB_Channel = duplicator.run(stackB, channel_to_rotate, channel_to_rotate, 1, stackB_slices, 0, 0);
            //stackA_Channel = duplicator.run(stackA, channel_to_rotate, channel_to_rotate, 1, stackA_slices, 0, 0);
            this.stackA_Channel = new ImagePlus( "stackA_channel", ChannelSplitter.getChannel(stackA, channel_to_rotate) );
            this.stackB_Channel = new ImagePlus( "stackB_channel", ChannelSplitter.getChannel(stackB, channel_to_rotate) );
            BiDiFuseFusion(channel_to_rotate);
        }
    }

    /**
     * Fusion of 2 stacks (one channel) based on coordinates retrieved from
     * registration.
     *
     * @param channel_to_rotate
     * @return
     */
    public void BiDiFuseFusion(int channel_to_rotate) {
        IJ.log("Starting image fusion, channel " + channel_to_rotate + " ...");
        Calibration cal = stackB.getCalibration();
        int original_bitdepth = stackB.getBitDepth();
        int maximum_canvas = width * 2;

        StackReverser sr = new StackReverser();
        stackB_selectedTransitionPoint = stackB_selectedTransitionPoint_original;
        zStackB_P1 = zStackB_P1_original;

        if (debug) {
            stackB_Channel.show();
        }

        //***************************************//
        //*****New image to perform rotation*****// 
        //***************************************//
        ImagePlus stackB_translate;

        
        int nPath = 0;
        String base = this.tempDir;
        try {
            deletePath(base);
        } catch( Exception e) {
        }
        if (rotateVirtual) {
            IJ.log("Prepare image stack B for rotation (virtual)");
            IJ.log("Temporary folder for virtual stack saving = " + this.tempDir);
            //VirtualStack vs = new VirtualStack(virtualStack.getWidth(), virtualStack.getHeight(), null, "outputPath");
            //ImageStack stack = virtualStack.getStack();
            //int n = stack.getSize();
            //Calibration cal = virtualStack.getCalibration();
            String path = makePath(base, nPath); nPath++;
            VirtualStack vs = new VirtualStack( maximum_canvas, maximum_canvas, stackB_Channel.getProcessor().getColorModel(), path);
            for (int i = 1; i < stackB_slices + 1; i++) {
                ImagePlus empty = IJ.createImage("", maximum_canvas, maximum_canvas, 1, stackB_Channel.getProcessor().getBitDepth() );
                String pathi = path + "/" + pad(i);
                vs.addSlice(pathi);
                this.stackB_Channel.setSlice(i);
                empty.getProcessor().copyBits( this.stackB_Channel.getProcessor(), maximum_canvas / 2 - xStackB_P1_pixels, maximum_canvas / 2 - yStackB_P1_pixels, Blitter.COPY);
                IJ.save( empty , pathi );
            }
            FolderOpener fo = new FolderOpener();
            fo.openAsVirtualStack(true);
            stackB_translate = fo.openFolder(path);
            stackB_translate.setTitle("stackB_translate - channel " + channel_to_rotate);
//new ImagePlus( "stackB_translate - channel " + channel_to_rotate, vs);
            //        IJ.createVirtualStack("stackB_translate - channel " + channel_to_rotate, original_bitdepth + "-bit black", maximum_canvas, maximum_canvas, stackB_slices);
        } else {
            IJ.log("Prepare image stack B for rotation (in memory)");
            stackB_translate = IJ.createImage("stackB_translate - channel " + channel_to_rotate, original_bitdepth + "-bit black", maximum_canvas, maximum_canvas, stackB_slices);
        }
        //ImagePlus stackB_translate = IJ.createImage("stackB_translate - channel " + channel_to_rotate, original_bitdepth + "-bit black", maximum_canvas, maximum_canvas, 2*halfSize);
        stackB_translate.setCalibration(cal);
        for (int i = 1; i < stackB_slices + 1; i++) {
            this.stackB_Channel.setSlice(i);
            stackB_translate.setSlice(i);
            stackB_translate.getProcessor().copyBits( this.stackB_Channel.getProcessor(), maximum_canvas / 2 - xStackB_P1_pixels, maximum_canvas / 2 - yStackB_P1_pixels, Blitter.COPY);
            stackB_translate.getProcessor().resetRoi();
        }
        //Debug
        if (debug) {
            stackB_translate.show();
        }

        //******************//
        //*****Rotation*****// 
        //******************//
        ImagePlus stackB_transformJ = stackB_translate;
        stackB_translate = null;
        //Excecute rotation of image in XYZ 
        Z_angle_deg_B_P1P2_inv = Z_angle_deg_B_P1P2 * -1;
        Z_angle_total_deg = Z_angle_deg + Z_angle_deg_B_P1P2_inv;
        //Virtual
        if (rotateVirtual) {
            IJ.log("Virtual rotation");
            Calibration cm = cal;
            String format = "TIFF";
            FolderOpener fo = new FolderOpener();
            String path = makePath(base, nPath);
            String oldPath = path;
            nPath++;
            stackB_transformJ.getProcessor().resetRoi();

            // Z_ANGLE 1
            IJ.log("Preparing image stack B for rotation (Virtual)");
            String stackB_transformJ_String = rotateVirtualStack( stackB_transformJ, Z_angle_deg_B_P1P2, interpolation, path + "/", format );
            stackB_transformJ = fo.openFolder(stackB_transformJ_String);
            
            
            IJ.log("Rotation over X-axis and Y-axis (Virtual)");
            // X_ANGLE - Y_ANGLE
            if ((Math.abs(Y_angle_deg) + Math.abs(X_angle_deg)) > 0) {
 
/*               
                // Paste origin (xStackB_P1_pixels,yStackB_P1_pixels,zStackB_P1) in image centre 
                // Add extra slices
                int extraSlice=0;
                int halfSize = Math.max( zStackB_P1, stackB_slices - zStackB_P1 ) + extraSlice;
                int indexTranslated;
                //Calculate new positons z
                if( zStackB_P1 < (stackB_slices - zStackB_P1) ) {

                    indexTranslated = halfSize - zStackB_P1; //+ 1
                    //Add extra slices
                    ImagePlus stackB_pre = IJ.createImage("stackB_pre", original_bitdepth + "-bit black", maximum_canvas, maximum_canvas, indexTranslated-1 );
                    ImageStack s1 = stackB_transformJ.getImageStack();
                    s1 = concat( stackB_pre.getImageStack(), s1 );
                    //stackB_transformJ = null;
                    stackB_transformJ = new ImagePlus( "B added slices", s1 );
                    stackB_pre = null;
                    s1 = null;
                    stackB_transformJ.setCalibration(cal);
                    
                    // Rotation
                    if (debug) {
                        stackB_transformJ.duplicate().show();
                    }
                    stackB_transformJ = (rt.run(imagescience.image.Image.wrap(stackB_transformJ), 0, Y_angle_deg, X_angle_deg, interpolation, false, false, false)).imageplus();
                    if (debug) {
                        stackB_transformJ.duplicate().show();
                    }
                    
                    //Remove extra slices again
                    ImageStack s = stackB_transformJ.getImageStack();
                    stackB_transformJ.setStack( stackB_transformJ.getImageStack().crop(0, 0, indexTranslated-1, s.getWidth(), s.getHeight(), stackB_slices) );
*/
                
                
                
                // X_ANGLE
                Calibration cx = cm;
                cx.pixelHeight = cm.pixelDepth;
                cx.pixelDepth = cm.pixelWidth;
                oldPath = path;
                path = makePath(base, nPath);
                nPath++;
                IJ.log("Rotation over X-axis and Y-axis: X-axis (Virtual)");
                stackB_transformJ_String = turnVirtualStack(stackB_transformJ, ORIENTATION_YZ, path + "/", format);
                //deletePath(oldPath);
                stackB_transformJ = fo.openFolder(stackB_transformJ_String);
                stackB_transformJ.setCalibration(cx);
                oldPath = path;
                path = makePath(base, nPath);
                nPath++;
                stackB_transformJ_String = rotateVirtualStack(stackB_transformJ, X_angle_deg, interpolation, path + "/", format);
                //deletePath(oldPath);
                stackB_transformJ = fo.openFolder(stackB_transformJ_String);
                oldPath = path;
                path = makePath(base, nPath);
                nPath++;
                stackB_transformJ_String = turnVirtualStack(stackB_transformJ, ORIENTATION_XZ, path + "/", format);
                //deletePath(oldPath);
                stackB_transformJ = fo.openFolder(stackB_transformJ_String);
                IJ.run(stackB_transformJ, "Rotate 90 Degrees Right", "");
                IJ.run(stackB_transformJ, "Flip Horizontally", "stack");

                // Y_ANGLE
                IJ.log("Rotation over X-axis and Y-axis: Y-axis (Virtual)");
                oldPath = path;
                path = makePath(base, nPath);
                nPath++;
                stackB_transformJ_String = turnVirtualStack(stackB_transformJ, ORIENTATION_XZ, path + "/", format);
                //deletePath(oldPath);
                stackB_transformJ = fo.openFolder(stackB_transformJ_String);
                Calibration cy = cm;
                cy.pixelWidth = cm.pixelDepth;
                cy.pixelDepth = cm.pixelHeight;
                stackB_transformJ.setCalibration(cy);
                oldPath = path;
                path = makePath(base, nPath);
                nPath++;
                stackB_transformJ_String = rotateVirtualStack(stackB_transformJ, -Y_angle_deg, interpolation, path + "/", format);
                //deletePath(oldPath);
                stackB_transformJ = fo.openFolder(stackB_transformJ_String);
                oldPath = path;
                path = makePath(base, nPath);
                nPath++;
                stackB_transformJ_String = turnVirtualStack(stackB_transformJ, ORIENTATION_XZ, path + "/", format);
                //deletePath(oldPath);
                stackB_transformJ = fo.openFolder(stackB_transformJ_String);
            }
            // Z_ANGLE 2
            IJ.log("Rotation over Z-axis (Virtual)");
            oldPath = path;
            path = makePath(base, nPath);
            nPath++;
            stackB_transformJ_String = rotateVirtualStack(stackB_transformJ, Z_angle_total_deg, interpolation, path + "/", format);
            //deletePath(oldPath);
            stackB_transformJ = fo.openFolder(stackB_transformJ_String);
            stackB_transformJ.setTitle("After Virtual TransformJ - channel " + channel_to_rotate);
            IJ.log("Removing virtual stacks from disk (Virtual)");
            //deletePath(tempDir);

        } // Non virtual
        else {
            
            //Align O-P with X-axis
            Rotate rt = new Rotate();
            stackB_transformJ = (rt.run(imagescience.image.Image.wrap(stackB_transformJ), Z_angle_deg_B_P1P2, 0, 0, interpolation, false, false, false)).imageplus();
            if (debug) {
                stackB_transformJ.duplicate().show();
            }
            
            IJ.log("Rotation about X-axis and Y-axis.");
            // Do the rotation along the x- and y-axis
            if ((Math.abs(Y_angle_deg) + Math.abs(X_angle_deg)) > 0) {

                //Paste origin (xStackB_P1_pixels,yStackB_P1_pixels,zStackB_P1) in image centre 
                //Add extra slices
                int extraSlice=0;
                int halfSize = Math.max( zStackB_P1, stackB_slices - zStackB_P1 ) + extraSlice;
                int indexTranslated;
                //Calculate new positons z
                if( zStackB_P1 < (stackB_slices - zStackB_P1) ) {

                    indexTranslated = halfSize - zStackB_P1; //+ 1
                    //Add extra slices
                    ImagePlus stackB_pre = IJ.createImage("stackB_pre", original_bitdepth + "-bit black", maximum_canvas, maximum_canvas, indexTranslated-1 );
                    ImageStack s1 = stackB_transformJ.getImageStack();
                    s1 = concat( stackB_pre.getImageStack(), s1 );
                    //stackB_transformJ = null;
                    stackB_transformJ = new ImagePlus( "B added slices", s1 );
                    stackB_pre = null;
                    s1 = null;
                    stackB_transformJ.setCalibration(cal);
                    
                    // Rotation
                    if (debug) {
                        stackB_transformJ.duplicate().show();
                    }
                    stackB_transformJ = (rt.run(imagescience.image.Image.wrap(stackB_transformJ), 0, Y_angle_deg, X_angle_deg, interpolation, false, false, false)).imageplus();
                    if (debug) {
                        stackB_transformJ.duplicate().show();
                    }
                    
                    //Remove extra slices again
                    ImageStack s = stackB_transformJ.getImageStack();
                    stackB_transformJ.setStack( stackB_transformJ.getImageStack().crop(0, 0, indexTranslated-1, s.getWidth(), s.getHeight(), stackB_slices) );

                }
                else {

                    //Add extra slices
                    indexTranslated = extraSlice + 1;
                    ImagePlus stackB_post = IJ.createImage("stackB_post", original_bitdepth + "-bit black", maximum_canvas, maximum_canvas, 2*halfSize - stackB_transformJ.getNSlices() );
                    ImageStack s1 = stackB_transformJ.getImageStack();
                    s1 = concat( s1, stackB_post.getImageStack() );
                    stackB_transformJ = new ImagePlus( "B added slices", s1 );
                    stackB_transformJ.setCalibration(cal);
                    
                    // Rotation
                    if (debug) {
                        stackB_transformJ.duplicate().show();
                    }
                    stackB_transformJ = (rt.run(imagescience.image.Image.wrap(stackB_transformJ), 0, Y_angle_deg, X_angle_deg, interpolation, false, false, false)).imageplus();
                    if (debug) {
                        stackB_transformJ.duplicate().show();
                    }

                    // Remove extra slices again
                    ImageStack s = stackB_transformJ.getImageStack();
                    stackB_transformJ.setStack( stackB_transformJ.getImageStack().crop(0, 0, indexTranslated-1, s.getWidth(), s.getHeight(), stackB_slices) );

                }

            }
            Progress.update(Progress.ROT_XY);

            IJ.log("Rotation about Z-axis.");
            stackB_transformJ = (rt.run(imagescience.image.Image.wrap(stackB_transformJ), Z_angle_total_deg, 0, 0, interpolation, false, false, false)).imageplus();
            stackB_transformJ.setTitle("stackB_transformJ - channel " + channel_to_rotate);
            Progress.update(Progress.ROT_Z);
        }

        //Debug
        if (debug) {
            stackB_transformJ.show();
        }

        //****************************************//
        //*****Crop rotated stackB to VOF*****//
        //****************************************//
        //Calculate VOF
        stackB_transformJ.setSlice(stackB_transformJ.getStackSize() / 2);
        ImagePlus dup = new ImagePlus("Dup", stackB_transformJ.getProcessor());
        ThresholdToSelection tts = new ThresholdToSelection();
        dup.getProcessor().setThreshold(1, 1e99, ImageProcessor.NO_LUT_UPDATE);
        Roi roiGlobal = tts.convert(dup.getProcessor());
        stackB_transformJ.setRoi(roiGlobal);
        float[] xcoord = roiGlobal.getFloatPolygon().xpoints;
        float[] ycoord = roiGlobal.getFloatPolygon().ypoints;
        int minX = dup.getWidth();
        int maxX = 0;
        int minY = dup.getHeight();
        int maxY = 0;
        for (int i = 0; i < xcoord.length; i++) {
            if (xcoord[i] > maxX) {
                maxX = (int) xcoord[i];
            }
            if (xcoord[i] < minX) {
                minX = (int) xcoord[i];
            }
        }
        for (int i = 0; i < ycoord.length; i++) {
            if (ycoord[i] > maxY) {
                maxY = (int) ycoord[i];
            }
            if (ycoord[i] < minY) {
                minY = (int) ycoord[i];
            }
        }
        //Trim stackB stack to FOV
        int distance_stackB_P1_minX = stackB_transformJ.getWidth()/2 - minX;
        int distance_stackB_P1_minY = stackB_transformJ.getHeight()/2 - minY;
        //int distance_stackB_P1_minX = centerX - minX;
        //int distance_stackB_P1_minY = centerY - minY;
        int pastelengthX = maxX - minX;
        int pastelengthY = maxY - minY;
        stackB_transformJ.setRoi(new Rectangle(minX, minY, pastelengthX, pastelengthY));
        ImagePlus stackB_transformJCrop = stackB_transformJ.duplicate();
        stackB_transformJCrop.setTitle("stackB_transformJCrop - channel " + channel_to_rotate);

        //stackB_transformJ = null;
        if (debug) {
            stackB_transformJCrop.show();
        }

        //****************************************//
        //*****  SIFT post-registration step *****//
        //****************************************//
        Rectangle rect = null;
        if ( postRegistration ) {
            //stackB_transformJCrop.duplicate().show();
            stackB_transformJCrop.setSlice( zStackB_P1 );
            stackA_Channel.setSlice( zStackA_P1 );
            ImagePlus dupA = new ImagePlus("stack A registration slice P1", stackA_Channel.getProcessor());
            ImagePlus dupB = new ImagePlus("stack B registration slice P1", stackB_transformJCrop.getProcessor());
            //IJ.cr
            ImagePlus translated_dupA = IJ.createImage("translated A slice", dupB.getWidth(), dupB.getHeight(), 1, dupB.getBitDepth());
            translated_dupA.getProcessor().copyBits( dupA.getProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.COPY);
            //translated_dupA.show();
            ImagePlus alignedStack = Correlation.alignStack( dupB, translated_dupA, stackB_transformJCrop );
            //Roi centerRoi = alignedStack.getRoi();
            //rect = centerRoi.getBounds();
            //dupA.show();
            //dupB.show();
            //alignedStack.show();
            stackB_transformJCrop = alignedStack;
            stackB_transformJCrop.deleteRoi();
            if ( rect != null ) {
                int centerX = rect.x;
                int centerY = rect.y;
                distance_stackB_P1_minX = centerX;
                distance_stackB_P1_minY = centerY;
            }
        }
        Progress.update(Progress.SIFT);
        
        //**********************************//
        //***  Generate Composite image  ***//
        //**********************************//
        //Concatenator c = new Concatenator();
        
        //ImagePlus impMergedA = IJ.createImage("A", width, height, nPath, width)c.concatenate( stackB_Channel, stackB_transformJCrop, true );
        //for (int i = 1; i < stackA_slices+1; i++) {
        //    stackA_Channel.setSlice(i);
        //    impMergedA.setSlice(index);
        //    merge.getProcessor().copyBits(stackA_Channel.getChannelProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.COPY);
        //    merge.getProcessor().resetRoi();
        //    index++;
        //}

        
        //ImagePlus impMerged = 
	//CompositeImage impComp = new CompositeImage( impMerged, CompositeImage.COMPOSITE ); 
        //impComp.show();
        Progress.update(Progress.MERGE);

        //***************************//
        //*****Smooth Transition*****//
        //***************************//
        if (rangeSmooth > 0) {
            IJ.log("Smooth transition");
            //Duplicate slices to smooth
            int halfRange = Math.round(rangeSmooth / 2);
            Duplicator duplicator = new Duplicator();
            ImagePlus stackB_sub = duplicator.run(stackB_transformJCrop, stackB_selectedTransitionPoint - halfRange, stackB_selectedTransitionPoint + halfRange);
            stackB_sub.setTitle("stackB_sub - channel " + channel_to_rotate);
            ImagePlus stackA_sub = duplicator.run(stackA, channel_to_rotate, channel_to_rotate, stackA_selectedTransitionPoint - halfRange, stackA_selectedTransitionPoint + halfRange, 0, 0);
            stackA_sub.setTitle("stackA_sub- channel " + channel_to_rotate);
            //Adjust trimming of stacks to include smooth slices
            stackA_selectedTransitionPoint = stackA_selectedTransitionPoint - (halfRange + 1);
            stackB_selectedTransitionPoint = stackB_selectedTransitionPoint + (halfRange + 1);
            //Debug
            if (debug) {
                stackA_sub.show();
                stackB_sub.show();
            }
            //Linear smoothing
            if (methodSmooth.equals("Linear weighted sum")) {
                //Make duplicates
                ImagePlus stackA_subSmooth = stackA_sub.duplicate();
                stackA_subSmooth.setTitle("stackA_subSmooth - channel " + channel_to_rotate);
                ImagePlus stackB_subSmooth = stackB_sub.duplicate();
                stackB_subSmooth.setTitle("stackB_subSmooth - channel " + channel_to_rotate);
                stackA_sub = null;
                stackB_sub = null;
                //Reverse stackA
                sr.flipStack(stackA_subSmooth);
                //% of intensity
                for (int i = 1; i <= stackB_subSmooth.getStackSize(); i++) {
                    stackB_subSmooth.setSlice(i);
                    stackA_subSmooth.setSlice(i);
                    double PctIntensity = ((double) i / stackB_subSmooth.getStackSize());
                    stackB_subSmooth.getProcessor().multiply(PctIntensity);
                    stackA_subSmooth.getProcessor().multiply(PctIntensity);
                }
                //Re-reverse stackA
                sr.flipStack(stackA_subSmooth);
                //Debug
                if (debug) {
                    stackA_subSmooth.show();
                    stackB_subSmooth.show();
                }
                //Combine both stacks: add stackA to stackB	
                smoothImage = stackB_subSmooth.duplicate();
                for (int i = 1; i <= smoothImage.getStackSize(); i++) {
                    smoothImage.setSlice(i);
                    stackA_subSmooth.setSlice(i);
                    smoothImage.getProcessor().copyBits(stackA_subSmooth.getProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.ADD);
                }
            } else {
                //Other smoothing methods: max, min, average, sum
                //SmoothImage=stackB -> Copy intensities stackA onto smoothImage
                smoothImage = stackB_sub.duplicate();
                for (int i = 1; i <= stackA_sub.getStackSize(); i++) {
                    smoothImage.setSlice(i);
                    stackA_sub.setSlice(i);
                    if (methodSmooth.equals("Max")) {
                        smoothImage.getProcessor().copyBits(stackA_sub.getProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.MAX);
                    } else if (methodSmooth.equals("Min")) {
                        smoothImage.getProcessor().copyBits(stackA_sub.getProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.MIN);
                    } else if (methodSmooth.equals("Average")) {
                        smoothImage.getProcessor().copyBits(stackA_sub.getProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.AVERAGE);
                    } else if (methodSmooth.equals("Sum")) {
                        smoothImage.getProcessor().copyBits(stackA_sub.getProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.ADD);
                    }
                }
            }
            //Debug
            if (debug) {
                smoothImage.show();
            }
        }

        //***************************************************************//
        //*****Trim slices of stackB stack up to the transition point*****//
        //***************************************************************//   
        ImageStack temp1 = stackB_transformJCrop.getStack();
        if (rangeSmooth > 0) {
            for (int i = 1; i < stackB_selectedTransitionPoint; i++) {
                temp1.deleteSlice(1);
            }
        } else {
            //Extra slice if no smoothing is performed; same slice in stackA & stackB
            for (int i = 1; i <= stackB_selectedTransitionPoint; i++) {
                temp1.deleteSlice(1);
            }
        }
        ImagePlus stackB_transformJTrim = new ImagePlus("stackB_transformJTrim - channel " + channel_to_rotate, temp1);
        stackB_transformJCrop = null;
        temp1 = null;
        //Debug
        if (debug) {
            stackB_transformJTrim.show();
        }
        Progress.update(Progress.TRIM);

        //**************************************//
        //*****Merge stacks*****//
        //**************************************//
        //Calculate #slices
        int nSlices = 0;
        if (rangeSmooth > 0) {
            nSlices = stackB_transformJTrim.getStackSize() + smoothImage.getStackSize() + stackA_selectedTransitionPoint;
        } else {
            nSlices = stackB_transformJTrim.getStackSize() + stackA_selectedTransitionPoint;
        }
        //Create new stack
        ImagePlus merge = IJ.createHyperStack("Merge", stackB_transformJTrim.getWidth(), stackB_transformJTrim.getHeight(), 1, nSlices, 1, stackB_transformJTrim.getBitDepth());

        //Reverse stackB, necessary since 'add slice' can only add slices after (not before) the active slice
        //Add stackB slices
        sr.flipStack(stackB_transformJTrim);
        stackB_transformJTrim.setSlice(stackB_transformJTrim.getStackSize());
        for (int i = 1; i <= stackB_transformJTrim.getStackSize(); i++) {
            merge.setSlice(i);
            stackB_transformJTrim.setSlice(i);
            merge.getProcessor().copyBits(stackB_transformJTrim.getProcessor(), 0, 0, Blitter.COPY);
        }
        //Add smoothed slices if necessary
        if (rangeSmooth > 0) {
            sr = new StackReverser();
            sr.flipStack(smoothImage);

            int index = stackB_transformJTrim.getStackSize() + 1;
            for (int i = 1; i <= smoothImage.getStackSize(); i++) {
                smoothImage.setSlice(i);
                merge.setSlice(index);
                merge.getProcessor().copyBits(smoothImage.getProcessor(), 0, 0, Blitter.COPY);
                merge.getProcessor().resetRoi();
                index++;
            }
        }
        //Debug
        if (debug) {
            merge.show();
        }
        //Add stackA slices with correct translation (with respect to stack B)
        int index = 0;
        if (rangeSmooth > 0) {
            index = stackB_transformJTrim.getStackSize() + smoothImage.getStackSize() + 1;
        } else {
            index = stackB_transformJTrim.getStackSize() + 1;
        }
        for (int i = stackA_selectedTransitionPoint; i > 0; i--) {
            stackA_Channel.setSlice(i);
            merge.setSlice(index);
            merge.getProcessor().copyBits(stackA_Channel.getChannelProcessor(), distance_stackB_P1_minX - xStackA_P1_pixels, distance_stackB_P1_minY - yStackA_P1_pixels, Blitter.COPY);
            merge.getProcessor().resetRoi();
            index++;
        }
        //Re-Reverse stack to normal order
        sr = new StackReverser();
        sr.flipStack(merge);

        //*********************//
        //*****Show Result*****//
        //*********************//
        merge.setTitle("BiDiFuse, channel " + channel_to_rotate);
        merge.setCalibration(cal);
        merge.show();
        IJ.log("Finished image fusion, channel " + channel_to_rotate);
        Progress.update(Progress.END);
    }

    /**
     * Inverse order of elements in array: (a_1 .. a_n) --> (a_n .. a_1)
     * 
     * @param input double array
     * @return array with the indices inverted in order
     */
     public static double[] invertArrayIndices( double[] d ) {
         int n = d.length;
         double[] a = new double[d.length];
         
         for (int i = 0; i < n; i ++) {
             a[i] = d[n-i-1];
         }
         
         return a;
     }

     /**
     * minimum value in array
     * 
     * @param input double array
     * @return minimum
     */
     public static double getMinimum( double[] d ) {
         int n = d.length;
         double min = d[0];

         for (int i = 0; i < n; i ++) {
             if (d[i] < min) {
                min = d[i];
            }
         }
         
         return min;
     }

     /**
     * maximum value in array
     * 
     * @param input double array
     * @return maximum
     */
     public static double getMaximum( double[] d ) {
         int n = d.length;
         double max = d[0];

         for ( int i = 0; i < n; i++ ) {
             if ( d[i] > max ) {
                max = d[i];
            }
         }
         
         return max;
     }

     /**
     * Inverse order of elements in array: (a_1 .. a_n) --> (a_n .. a_1)
     * 
     * @param input double array
     * @return array with the indices inverted in order
     */
     public static double[] addScalarToArray( double[] d, double s ) {
         int n = d.length;
         double[] a = new double[d.length];
         
         for (int i = 0; i < n; i ++) {
             a[i] = d[i] + s;
         }
         
         return a;
     }

     /**
     *  Generate array seq: (a_1 .. a_n) = (n .. m)
     * 
     * @param int n, int m
     * @return array with the sequence of values
     */
     public static double[] arraySequence( int m, int n ) {
         double[] a = new double[n-m+1];
         for (int i = 0; i < n; i ++)
             a[i] = (double) (m + i);
         return a;
     }

     
    /**
     * Determine transition point based on intensities and sharpness of images
     *
     * @param offset_stackB_stackA
     * @return transition point based on intensity and sharpness
     */
    public int[] DetermineFusionPoint(int offset_stackB_stackA ) {
        //*********************************//
        //*****Duplicate first channel*****//
        //*********************************//
        Duplicator duplicator = new Duplicator();
        int channelFusionPoint = 1;
        ImagePlus stackA_dup = duplicator.run(stackA, channelFusionPoint, channelFusionPoint, 1, stackA_slices, 0, 0);
        ImagePlus stackB_dup = duplicator.run(stackB, channelFusionPoint, channelFusionPoint, 1, stackB_slices, 0, 0);

        //*************************************//
        //*****Calculate transition points*****//
        //*************************************//

        //Compare intensity and edge information between stacks
        int IntensityTransitionFound = 0;
        int EdgeTransitionFound = 0;
        int[] rtn = new int[2];
        for (int i = 1; i <= stackA_slices; i++) {
            int stackA_slice = i;
            int stackB_slice = i - offset_stackB_stackA;
            if (stackB_slice > 0 && stackB_slice <= stackB_slices) {
                //Compare intensity
                if (IntensityTransitionFound == 0) {
                    stackA_dup.setSlice(stackA_slice);
                    double stackA_mean = stackA_dup.getStatistics().mean;
                    stackB_dup.setSlice(stackB_slice);
                    double stackB_mean = stackB_dup.getStatistics().mean;
                    double stackA_stackB_intensitydiff = stackA_mean - stackB_mean;
                    if (stackA_stackB_intensitydiff < 0) {
                        int stackA_SuggestedTransitionIntensity = stackA_slice;
                        rtn[0] = stackA_SuggestedTransitionIntensity;
                        IntensityTransitionFound = 1;
                    }
                }
            }
        }

        //Calculate edge images
        ImagePlus stackB_dupEdge = stackB_dup;
        for (int i = 1; i <= stackB_slices; i++) {
            stackB_dupEdge.setSlice(i);
            stackB_dupEdge.getProcessor().findEdges();
        }
        ImagePlus stackA_dupEdge = stackA_dup;
        for (int i = 1; i <= stackA_slices; i++) {
            stackA_dupEdge.setSlice(i);
            stackA_dupEdge.getProcessor().findEdges();
        }
        for (int i = 1; i <= stackA_slices; i++) {
            int stackA_slice = i;
            int stackB_slice = i - offset_stackB_stackA;
            if (stackB_slice > 0 && stackB_slice <= stackB_slices) {
                //Compare edge
                if (EdgeTransitionFound == 0) {
                    stackA_dupEdge.setSlice(stackA_slice);
                    double stackA_mean = stackA_dupEdge.getStatistics().mean;
                    stackB_dupEdge.setSlice(stackB_slice);
                    double stackB_mean = stackB_dupEdge.getStatistics().mean;
                    double stackA_stackB_intensitydiff = stackA_mean - stackB_mean;
                    if (stackA_stackB_intensitydiff < 0) {
                        int stackA_SuggestedTransitionEdgePoint = stackA_slice;
                        rtn[1] = stackA_SuggestedTransitionEdgePoint;
                        EdgeTransitionFound = 1;
                    }
                }
            }
        }
        //Return
        return rtn;
    }


    /**
     * Showing the curves Debugging the automated suggestions
     */
    public void showTransitionCurves( int offset_stackB_stackA, int channel ) {
        //****************************************//
        //*****Duplicate channel of interest *****//
        //****************************************//
        Duplicator duplicator = new Duplicator();
        int channelFusionPoint = channel;
        ImagePlus stackA_dup = duplicator.run(stackA, channelFusionPoint, channelFusionPoint, 1, stackA_slices, 0, 0);
        ImagePlus stackB_dup = duplicator.run(stackB, channelFusionPoint, channelFusionPoint, 1, stackB_slices, 0, 0);

            //Calculate edge images
            ImagePlus stackB_dupEdge = stackB_dup.duplicate();
            for (int i = 1; i <= stackB_slices; i++) {
                stackB_dupEdge.setSlice(i);
                stackB_dupEdge.getProcessor().findEdges();
            }
            ImagePlus stackA_dupEdge = stackA_dup.duplicate();
            for (int i = 1; i <= stackA_slices; i++) {
                stackA_dupEdge.setSlice(i);
                stackA_dupEdge.getProcessor().findEdges();
            }

            double[] stackA_slice_nr_array = new double[stackA_slices];
            double[] stackB_slice_nr_array = new double[stackB_slices];
            double[] stackA_mean_array = new double[stackA_slices];
            double[] stackB_mean_array = new double[stackB_slices];
            double[] stackA_edge_array = new double[stackA_slices];
            double[] stackB_edge_array = new double[stackB_slices];
            for (int i = 1; i <= stackA_slices; i++) {
                int stackA_slice = i;
                stackA_slice_nr_array[i-1] = stackA_slice;
                stackB_slice_nr_array[i-1] = stackB_slices - i;
                int stackB_slice = i + offset_stackB_stackA;
                stackA_Channel.setSlice(i);
                stackA_mean_array[i-1] = stackA_Channel.getStatistics().mean;
                stackB_Channel.setSlice(i);
                stackB_mean_array[i-1] = stackB_Channel.getStatistics().mean;

                stackA_dupEdge.setSlice(i);
                stackA_edge_array[i-1] = stackA_dupEdge.getStatistics().mean;
                stackB_dupEdge.setSlice(i);
                stackB_edge_array[i-1] = stackB_dupEdge.getStatistics().mean;
            }
            stackB_slice_nr_array = invertArrayIndices(stackA_slice_nr_array);
            stackB_slice_nr_array = addScalarToArray( stackB_slice_nr_array, offset_stackB_stackA );
            Plot p_mean = new Plot("A,B mean", "slice nr", "Mean/edge intensity", stackA_slice_nr_array, stackA_mean_array);
            Plot p_edge = new Plot("A,B edges", "slice nr", "Mean/edge intensity", stackA_slice_nr_array, stackA_edge_array);
            int iMinA = (int) getMinimum(stackA_slice_nr_array);
            int iMinB = (int) getMinimum(stackB_slice_nr_array);
            int iMaxA = (int) getMaximum(stackA_slice_nr_array);
            int iMaxB = (int) getMaximum(stackB_slice_nr_array);
            double xMin = Math.min( iMinA, iMinB );
            double xMax = Math.max( iMaxA, iMaxB );
            p_mean.setLimits( xMin, xMax, NaN, NaN );
            p_mean.addPoints( stackB_slice_nr_array, stackB_mean_array, Plot.LINE );
            p_edge.setLimits( xMin, xMax, NaN, NaN );
            p_edge.addPoints( stackB_slice_nr_array, stackB_edge_array, Plot.LINE );
            double[] a = new double[ (int) (xMax-xMin) + 1 ];
            double[] b = new double[ (int) (xMax-xMin) + 1 ];
            double[] sliceMeanDiff = new double[ (int) (xMax-xMin) + 1 ];
            for ( int i = 0; i < sliceMeanDiff.length; i++ ) {
                if ( i >= iMinA & i <= iMaxA ) a[i] = stackA_mean_array[i-iMinA];
                else a[i] = 0;
                if ( i >= iMinB & i <= iMaxB ) b[i] = stackB_mean_array[iMaxB-i];
                else b[i] = 0;
                sliceMeanDiff[i] = a[i] - b[i];
            }
            double[] sliceEdgeDiff = new double[ (int) (xMax-xMin) + 1 ];
            for ( int i = 0; i < sliceEdgeDiff.length; i++ ) {
                if ( i >= iMinA & i <= iMaxA ) a[i] = stackA_edge_array[i-iMinA];
                else a[i] = 0;
                if ( i >= iMinB & i <= iMaxB ) b[i] = stackB_edge_array[iMaxB-i];
                else b[i] = 0;
                sliceEdgeDiff[i] = a[i] - b[i];
            }

            double[] x = arraySequence( (int) xMin, (int) xMax );
            
            p_mean.show();
            p_edge.show();
    }
    
    public static String pad(int n) {
        String str = "" + n;
        while (str.length() < 5) {
            str = "0" + str;
        }
        return str;
    }

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

    public static String rotateVirtualXY(ImagePlus virtualStack, double angle, int interpolation, String outputPath, String format) {

        rotateVirtualStack(virtualStack, angle, interpolation, outputPath, format);

        return outputPath;
    }

    public static String rotateVirtualYZ(ImagePlus virtualStack, double angle, int interpolation, String outputPath, String format) {

        turnVirtualStack(virtualStack, ORIENTATION_XY, outputPath, format);
        rotateVirtualStack(virtualStack, angle, interpolation, outputPath, format);

        return outputPath;
    }

    public static String rotateVirtualStack(ImagePlus virtualStack, double angle, int interpolation, String outputPath, String format) {
        VirtualStack vs = new VirtualStack(virtualStack.getWidth(), virtualStack.getHeight(), null, "outputPath");
        ImageStack stack = virtualStack.getStack();
        int n = stack.getSize();
        Calibration cal = virtualStack.getCalibration();
        for (int i = 1; i <= n; i++) {
            IJ.showProgress(i, n);
            ImageProcessor ip = stack.getProcessor(i);
            ImagePlus imp = new ImagePlus("", ip);
            imp.setCalibration(cal);
            imp.setRoi(virtualStack.getRoi());

            Rotate rt = new Rotate();
            imp = (rt.run(imagescience.image.Image.wrap(imp), angle, 0, 0, interpolation, false, false, false)).imageplus();
            //IJ.run(imp, "Rotate... ", "angle="+angle+" grid=1 interpolation=Bicubic");
            //imp.getProcessor().rotate(angle);
            if (!outputPath.equals("")) {
                IJ.saveAs(imp, format, outputPath + pad(i));
                vs.addSlice(outputPath + pad(i) + ".tif");
            }
        }
        return outputPath;
    }

    public static String turnVirtualStack(ImagePlus virtualStack, String orientation, String outputPath, String format) {
        VirtualStack vs = new VirtualStack(virtualStack.getWidth(), virtualStack.getHeight(), null, "outputPath");
        ImageStack stack = virtualStack.getStack();
        int n = stack.getSize();
        if (orientation.equals(ORIENTATION_XZ)) {
            n = stack.getHeight();
        }
        if (orientation.equals(ORIENTATION_YZ)) {
            n = stack.getWidth();
        }

        for (int i = 1; i <= n; i++) {
            ImagePlus imp = selectSlice(virtualStack, orientation, i);
            IJ.saveAs(imp, format, outputPath + pad(i));
        }

        return outputPath;
    }

    public static String makePath(String base, int nPath) {
        String path = base + "/" + "temp_" + (nPath + 1);
        new File(path).mkdirs();

        return path;
    }

    public static String deletePath(String path) {
        Path directory = Paths.get(path);
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            //e.printStackTrace();
        }

        return path;
    }

    // taken from WSR's Concatenator_.java
    public ImageStack concat(ImageStack stack2, ImageStack stack1) {
        int slice = 1;
        int size = stack1.getSize();
        for (int i = 1; i <= size; i++) {
            ImageProcessor ip = stack1.getProcessor(slice);
            String label = stack1.getSliceLabel(slice);
            stack1.deleteSlice(slice);
            stack2.addSlice(label, ip);
        }
        return stack2;
    } 

    public static void main(final String... args) {
        // start ImageJ
        new ij.ImageJ();
        // Open two images for testing
        //String pathRect = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629_MV/test BiDiFuse/BiDiFuse_Stack A.tif";
        //String pathVers = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629_MV/test BiDiFuse/BiDiFuse_Stack B.tif";
        
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/bol/BiDiFuse_7008brainx10Wz1Ex543stackrecto-ZC-NS.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/bol/BiDiFuse_7008brainx10Wz1Ex543stackverso-ZC-NS.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/_Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/BiDiFuse_7008brainx10Wz1Ex543stackrecto-ZC.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/_Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/BiDiFuse_7008brainx10Wz1Ex543stackverso-ZC.tif";
//        String pathRect = "F:/MB/BiDiFuse_7008brainx10Wz1Ex543stackrecto.tif";
//        String pathVers = "F:/MB/BiDiFuse_7008brainx10Wz1Ex543stackverso.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/BiDiFuse_nstack A.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/BiDiFuse_nstack B.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/JD/BiDiFuse_Stack A.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/JD/BiDiFuse_Stack B.tif";

        String pathRect = "C:/Users/Michael/Desktop/MB/BiDiFuse_Stack A.tif";
        String pathVers = "C:/Users/Michael/Desktop/MB/BiDiFuse_Stack B.tif";

        //String pathRect = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629/test BiDiFuse/BiDiFuse_Stack A2.tif";
        //String pathVers = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629/test BiDiFuse/BiDiFuse_Stack B2.tif";
        IJ.openImage(pathRect).show();
        IJ.openImage(pathVers).show();
        new BiDiFuse_Fusion().run("");
    }
}

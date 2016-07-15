package BiDiFuse;

import java.awt.Color;

//package be.ua.jdetrez.fiji.bidifuse;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Font;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Vector;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.MessageDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.YesNoCancelDialog;
import ij.plugin.*;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class BiDiFuse_Registration implements PlugIn {

    // Input images
    ImagePlus im1;
    ImagePlus im2;
    ImagePlus stackA_id;
    ImagePlus stackB_id;
    String imPath1;
    String imPath2;
    // Output coordinates/angles
    File outputFile;
    // GUI help
    boolean enable_help = true;
    // GUI: stackA ROI point coords
    int xStackA_P1_pixels;
    int yStackA_P1_pixels;
    int stackA_P1_sliceNr;
    int xStackA_P2_pixels;
    int yStackA_P2_pixels;
    int stackA_P2_sliceNr;
    int xStackA_P3_pixels;
    int yStackA_P3_pixels;
    int stackA_P3_sliceNr;
    //
    double xStackA_P1;
    double yStackA_P1;
    double zStackA_P1;
    double xStackA_P2;
    double yStackA_P2;
    double zStackA_P2;
    double xStackA_P3;
    double yStackA_P3;
    double zStackA_P3;
    //
    int registration_channel;
    int radius;
    // GUI: stackB ROI point coords
    int xStackB_P1_pixels;
    int yStackB_P1_pixels;
    int stackB_P1_sliceNr;
    int xStackB_P2_pixels;
    int yStackB_P2_pixels;
    int stackB_P2_sliceNr;
    int xStackB_P3_pixels;
    int yStackB_P3_pixels;
    int stackB_P3_sliceNr;
    //
    double xStackB_P1;
    double yStackB_P1;
    double zStackB_P1;
    double xStackB_P2;
    double yStackB_P2;
    double zStackB_P2;
    double xStackB_P3;
    double yStackB_P3;
    double zStackB_P3;
    // Image properties
    double pixelWidth;
    double pixelHeight;
    double pixelDepth;
    //
    String stackA_title;
    int stackA_width;
    int stackA_height;
    int stackA_slices;
    //
    String stackB_title;
    int stackB_width;
    int stackB_height;
    int stackB_slices;


    /**
     * Function to unzoom images
     *
     * @param imp ImagePlus, the image to unzoom
     */
    public void unzoom(ImagePlus imp) {
        int i = 1;
        while (i < 5) {
            IJ.run(imp, "Out", "");
            i++;
        }
    }

    /**
     * Take opposing angle in radians (-90 or +90, if over/under 90)
     *
     * @param uncorrected angle in radians
     *
     * @return corrected angle in radians
     */
    public double opposing_angle(double angle_in_radians) {
        double PI = Math.PI;
        while (angle_in_radians > (PI / 2)) {
            angle_in_radians = (PI - angle_in_radians) * (-1.0);
        }
        while (angle_in_radians < (-PI / 2)) {
            angle_in_radians = angle_in_radians + PI;
        }
        return angle_in_radians;
    }

    /**
     * Extract rotation angles and save them to file.
     *
     * @param File in the angles/parameters will be saved.
     */
    public void logRotation(File logFile) {

        double PI = Math.PI;
        //******************************************************************//
        //*****Calculate angle of Z-rotation to align P1 (left)-P2(right) with X-axis*****//
        //******************************************************************//
        double Z_angle_rad_A_P1P2 = Math.atan2(yStackA_P1 - yStackA_P2, xStackA_P2 - xStackA_P1); //y reversed
        double Z_angle_deg_A_P1P2 = Z_angle_rad_A_P1P2 * 180 / PI;
        double Z_angle_rad_B_P1P2 = Math.atan2(yStackB_P1 - yStackB_P2, xStackB_P2 - xStackB_P1); //y reversed
        double Z_angle_deg_B_P1P2 = Z_angle_rad_B_P1P2 * 180 / PI;

        //************************************************************//
        //******Calculate rotation about Y-axis (P1-P2 is X-axis)*****//
        //************************************************************//
        //Calculate new coordinates of xStackB_P2
        double xStackA_P2_afterXYrot = (xStackA_P2 - xStackA_P1) * Math.cos(Z_angle_rad_A_P1P2) - (yStackA_P2 - yStackA_P1) * Math.sin(Z_angle_rad_A_P1P2) + xStackA_P1; //y not reversed
        double xStackB_P2_afterXYrot = (xStackB_P2 - xStackB_P1) * Math.cos(Z_angle_rad_B_P1P2) - (yStackB_P2 - yStackB_P1) * Math.sin(Z_angle_rad_B_P1P2) + xStackB_P1; //y not reversed

        //Calculate angle of rotation
        double Y_angle_rad = Math.atan2(zStackB_P2 - zStackB_P1, xStackB_P2_afterXYrot - xStackB_P1) - Math.atan2(zStackA_P2 - zStackA_P1, xStackA_P2_afterXYrot - xStackA_P1);
        Y_angle_rad = opposing_angle(Y_angle_rad);
        double Y_angle_deg = Y_angle_rad * 180 / PI;

        //***********************************************************//
        //*****Calculate rotation about X-axis (P1-P3 is Y-axis)*****//
        //***********************************************************//
        //Calculate new coordinates of yStackB_P3
        double yStackA_P3_afterXYrot = (yStackA_P3 - yStackA_P1) * Math.cos(Z_angle_rad_A_P1P2) + (xStackA_P3 - xStackA_P1) * Math.sin(Z_angle_rad_A_P1P2) + yStackA_P1; //y not reversed
        double yStackB_P3_afterXYrot = (yStackB_P3 - yStackB_P1) * Math.cos(Z_angle_rad_B_P1P2) + (xStackB_P3 - xStackB_P1) * Math.sin(Z_angle_rad_B_P1P2) + yStackB_P1; //y not reversed
        //Calculate angle of rotation
        double X_angle_rad = Math.atan2(zStackB_P3 - zStackB_P1, yStackB_P1 - yStackB_P3_afterXYrot) - Math.atan2(zStackA_P3 - zStackA_P1, yStackA_P1 - yStackA_P3_afterXYrot); //y reversed
        X_angle_rad = opposing_angle(X_angle_rad);
        double X_angle_deg = X_angle_rad * 180 / PI;

        //****************************************//
        //****Calculate rotation about Z-axis ****//
        //****************************************//
        //Express P2 as origin of respective images
        double P1corrected_xStackA_P2 = xStackA_P2 - xStackA_P1;
        double P1corrected_yStackA_P2 = yStackA_P1 - yStackA_P2; //reversed
        double P1corrected_xStackB_P2 = xStackB_P2 - xStackB_P1;
        double P1corrected_yStackB_P2 = yStackB_P1 - yStackB_P2; //reversed
        //Find angle between vector P1-P2(stackA) and P1-P2 (stackB)
        double Z_angle_rad = Math.atan2(P1corrected_yStackB_P2, P1corrected_xStackB_P2) - Math.atan2(P1corrected_yStackA_P2, P1corrected_xStackA_P2); //y not reversed (already done in previous lines)
        double Z_angle_deg = Z_angle_rad * 180 / PI;

        //*******************************//
        //*****Write fusion data txt*****//
        //*******************************//
        ArrayList<String> al = new ArrayList<String>();
        al.add("stackA_title\t" + this.stackA_title);
        al.add("stackB_title\t" + this.stackB_title);
        al.add("xStackA_P1_pixels\t" + this.xStackA_P1_pixels);
        al.add("yStackA_P1_pixels\t" + this.yStackA_P1_pixels);
        al.add("xStackB_P1_pixels\t" + this.xStackB_P1_pixels);
        al.add("yStackB_P1_pixels\t" + this.yStackB_P1_pixels);
        al.add("zStackA_P1\t" + (int) this.stackA_P1_sliceNr);
        al.add("zStackB_P1\t" + (int) this.stackB_P1_sliceNr);
        al.add("X_angle_deg\t" + X_angle_deg);
        al.add("Y_angle_deg\t" + Y_angle_deg);
        al.add("Z_angle_deg_B_P1P2\t" + Z_angle_deg_B_P1P2);
        al.add("Z_angle_deg\t" + Z_angle_deg);

        PrintWriter writer;
        try {
            writer = new PrintWriter(logFile.getAbsolutePath());
            for (String s : al) {
                writer.println(s);
            }
            writer.close();
//            IJ.log("---- output start -----");
//            for (String s : al) {
//                IJ.log(s);
//            }
//            IJ.log("---- output end  ------");
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Initiate the information parameters (title, pixel size, width, height, nr
     * of slices, of the stacks.
     *
     */
    public void initImageInfo() {
        this.stackA_title = stackA_id.getTitle();
        this.stackA_width = stackA_id.getWidth();
        this.stackA_height = stackA_id.getHeight();
        this.stackA_slices = stackA_id.getNSlices();

        this.stackB_title = stackB_id.getTitle();
        this.stackB_width = stackB_id.getWidth();
        this.stackB_height = stackB_id.getHeight();
        this.stackB_slices = stackB_id.getNSlices();

        this.pixelWidth = stackB_id.getCalibration().pixelWidth;
        this.pixelHeight = stackB_id.getCalibration().pixelHeight;
        this.pixelDepth = stackB_id.getCalibration().pixelDepth;
    }

    /**
     * Draw a line on the imageplus image imp from (x1,y1) to (x2,y2)
     *
     * @param imp ImagePlus image on to which to draw the line
     * @param x1 x-coordinate of the start point
     * @param y1 y-coordinate of the start point
     * @param x2 x-coordinate of the end point
     * @param y2 y-coordinate of the end point
     */
    public void makeLine(ImagePlus imp, int x1, int y1, int x2, int y2) {
        Overlay overlay = new Overlay();
        Roi roi = new Line(x1, y1, x2, y2);
        roi.setStrokeColor(Color.red);
        double lineThicknessFactor = 1.0 / 200.0;
        double lineWidth = lineThicknessFactor * (imp.getWidth() + imp.getHeight())/2.0;
        roi.setStrokeWidth( lineWidth );
        overlay.add(roi);
        imp.setOverlay(overlay);
        imp.setHideOverlay(false);
        IJ.run(imp, "Select None", "");
    }

    /**
     * draw a line orthogonal to the line between xy1 an xy2, and crossing xy1.
     *
     * @param stackA
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     */
    public void drawlineORTH(ImagePlus stackA, int x1, int y1, int x2, int y2) {
        //Avoid division by zero: TODO replace by alternative
        if (y1 == y2) {
            y1 = y1 + 1;
        }
        if (x1 == x2) {
            x1 = x1 + 1;
        }
        double lineslope = -1.0 / (((double) y2 - (double) y1) / ((double) x2 - (double) x1));
        double height = (double) stackA.getHeight();
        double width = (double) stackA.getWidth();
        double Xzero = 0;
        double Yzero = 0;
        double YforXzero = y1 - (lineslope * (x1 - Xzero));
        double XforYzero = (Yzero - y1 + (lineslope * x1)) / lineslope;
        //Check for points outside image
        if (YforXzero < 0) {
            YforXzero = y1 - (lineslope * (x1 - width));
            Xzero = width;
        }
        if (XforYzero < 0) {
            XforYzero = (height - y1 + (lineslope * x1)) / lineslope;
            Yzero = height;
        }
        //Grays to RGB to draw the line
        //IJ.run(stackA, "RGB Color", "");
        IJ.run(stackA, "Select None", "");
        makeLine(stackA, (int) Math.round(XforYzero), (int) Math.round(Yzero), (int) Math.round(Xzero), (int) Math.round(YforXzero));
    }

    /**
     * GUI asking the user for input coordinates for the 3 corresponding points
     * in stackB.
     *
     * @param stackB
     */
    public void stackBUserInput(ImagePlus stackB) {
        IJ.setTool("point");
        RoiManager rm = RoiManager.getRoiManager();
        rm.reset();
        int ROIcount = 0;
        stackB.setSlice(stackB.getNSlices() / 2);
        stackB.getWindow().requestFocus();
        if (enable_help) {
            GenericDialog gd3 = new GenericDialog("BiDiFuse");
            gd3.addMessage("Find the 3 characterstic points (P1, P2, P3) in stack B, and add to the ROI manager [T]");
            gd3.showDialog();
        }
        while (ROIcount < 3) {
            rm = RoiManager.getRoiManager();
            Roi[] rois = rm.getRoisAsArray();
            if (rois.length >= 3 && (rois[1] != null && rois[2] != null)) {
                ROIcount = rois.length;
                rois[0].setImage(stackB);
                rois[1].setImage(stackB);
                rois[2].setImage(stackB);
                this.xStackB_P1_pixels = rois[0].getPolygon().xpoints[0];
                this.yStackB_P1_pixels = rois[0].getPolygon().ypoints[0];
                this.xStackB_P2_pixels = rois[1].getPolygon().xpoints[0];
                this.yStackB_P2_pixels = rois[1].getPolygon().ypoints[0];
                this.xStackB_P3_pixels = rois[2].getPolygon().xpoints[0];
                this.yStackB_P3_pixels = rois[2].getPolygon().ypoints[0];
//                this.stackB_P1_sliceNr = rois[0].getPosition();
//                this.stackB_P2_sliceNr = rois[1].getPosition();
//                this.stackB_P3_sliceNr = rois[2].getPosition();
                rm.select(0);
                this.stackB_P1_sliceNr = stackB.getSlice();
                rm.select(1);
                this.stackB_P2_sliceNr = stackB.getSlice();
                rm.select(2);
                this.stackB_P3_sliceNr = stackB.getSlice();
                rm.getSliceNumber("");
                
            }
        }
        this.xStackB_P1 = this.xStackB_P1_pixels * this.pixelWidth;
        this.yStackB_P1 = this.yStackB_P1_pixels * this.pixelHeight;
        this.zStackB_P1 = this.stackB_P1_sliceNr * this.pixelDepth;
        this.xStackB_P2 = this.xStackB_P2_pixels * this.pixelWidth;
        this.yStackB_P2 = this.yStackB_P2_pixels * this.pixelHeight;
        this.zStackB_P2 = this.stackB_P2_sliceNr * this.pixelDepth;
        this.xStackB_P3 = this.xStackB_P3_pixels * this.pixelWidth;
        this.yStackB_P3 = this.yStackB_P3_pixels * this.pixelHeight;
        this.zStackB_P3 = this.stackB_P3_sliceNr * this.pixelDepth;
        
        
    }

    /**
     * GUI asking the user for input coordinates for 3 characteristic points in
     * stackA which can be easily detected later on stackB.
     *
     * @param stackA
     * @return stackA_dup
     */
    public ImagePlus stackAUserInput(ImagePlus stackA, int slice) {
        ImagePlus stackA_dup = new ImagePlus();
        if (stackA.isHyperStack()) {
            stackA_dup = new ImagePlus("Stack A", ChannelSplitter.getChannel( stackA, this.registration_channel ) );
            //stackA_dup = new Duplicator().run(stackA, this.registration_channel, this.registration_channel, 1, stackA_dup.getStackSize(), 0, 0);
            //stackA_dup.setTitle("Stack A");
        } else {
            stackA_dup = stackA.duplicate();
            stackA_dup.setTitle("Stack A");
        }
        IJ.run(stackA_dup, "Grays", "");
        stackA_dup.setSlice(slice);
        stackA_dup.show();
        IJ.setTool("point");
        if (enable_help) {
            GenericDialog gd2 = new GenericDialog("BiDiFuse: input");
            gd2.addMessage("Add 2 characterstic points (P1, P2) to the ROI manager [T]");
            gd2.showDialog();
        }
        RoiManager rm = RoiManager.getInstance();
        if (RoiManager.getInstance() == null) {
            rm = new RoiManager();
        } else {
            rm.reset();
        }
        int ROIcount = 0;
        stackA_dup.getWindow().requestFocus();
        while (ROIcount < 2) {
            rm = RoiManager.getRoiManager();
            Roi[] rois = rm.getRoisAsArray();
            if (rois.length == 2 && (rois[1] != null)) {
                ROIcount = rois.length;
                this.xStackA_P1_pixels = rois[0].getPolygon().xpoints[0];
                this.yStackA_P1_pixels = rois[0].getPolygon().ypoints[0];
                this.xStackA_P2_pixels = rois[1].getPolygon().xpoints[0];
                this.yStackA_P2_pixels = rois[1].getPolygon().ypoints[0];
                drawlineORTH(stackA_dup, this.xStackA_P1_pixels, this.yStackA_P1_pixels, this.xStackA_P2_pixels, this.yStackA_P2_pixels);
            }
        }
        if (enable_help) {
            GenericDialog gd3 = new GenericDialog("BiDiFuse: input");
            gd3.addMessage("Add a third characterstic point (P3) to the ROI manager [T]");
            gd3.showDialog();
        }
        while (ROIcount < 3) {
            ROIcount = rm.getRoisAsArray().length;
        }
        Roi[] rois = rm.getRoisAsArray();
        if (rois.length > 2 && (!(rois[2].equals(null)))) {
            this.xStackA_P3_pixels = rm.getRoi(2).getPolygon().xpoints[0];
            this.yStackA_P3_pixels = rm.getRoi(2).getPolygon().ypoints[0];

            rm.select(0);
            this.stackA_P1_sliceNr = stackA_dup.getSlice();
            rm.select(1);
            this.stackA_P2_sliceNr = stackA_dup.getSlice();
            rm.select(2);
            this.stackA_P3_sliceNr = stackA_dup.getSlice();
            rm.getSliceNumber("");
            
            //IJ.run(stackA_dup, "RGB Color", "");
            Overlay overlay = new Overlay();
            double fontSizeFactor = 3;
            double radiusFactor = 2;
            double labelSizeFactor = 0.01;
            double labelSize = labelSizeFactor * ( stackA_dup.getWidth() + stackA_dup.getHeight() ) / 2.0 ;
            radius = (int) Math.round( radiusFactor * labelSize );
            int strokeWidth = 2;
            Roi roi = new OvalRoi(xStackA_P1_pixels - radius, yStackA_P1_pixels - radius, 2 * radius, 2 * radius);
            roi.setPosition(this.stackA_P1_sliceNr);
            roi.setName("1");
            roi.setStrokeWidth(strokeWidth);
            roi.setStrokeColor(Color.red);
            overlay.add(roi);
            roi = new OvalRoi(xStackA_P2_pixels - radius, yStackA_P2_pixels - radius, 2 * radius, 2 * radius);
            roi.setPosition(this.stackA_P2_sliceNr);
            roi.setName("2");
            roi.setStrokeWidth(strokeWidth);
            roi.setStrokeColor(Color.red);
            overlay.add(roi);
            roi = new OvalRoi(xStackA_P3_pixels - radius, yStackA_P3_pixels - radius, 2 * radius, 2 * radius);
            roi.setPosition(this.stackA_P3_sliceNr);
            roi.setName("3");
            roi.setStrokeWidth(strokeWidth);
            roi.setStrokeColor(Color.red);
            overlay.add(roi);
            int fontSize = (int) Math.round( fontSizeFactor * labelSize );
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize );
            overlay.setLabelFont(font);
            overlay.drawLabels(true);
            overlay.setLabelColor(Color.red);
            overlay.drawNames(true);
            stackA_dup.setOverlay(overlay);
            stackA_dup.setHideOverlay(false);
            IJ.run(stackA_dup, "Select None", "");
        } else {
            IJ.log("P2 cannot be defined, something went wrong: #rois > 2 but not equal to 3 or rois[2] does not exist) ");
        }
        this.xStackA_P1 = this.xStackA_P1_pixels * this.pixelWidth;
        this.yStackA_P1 = this.yStackA_P1_pixels * this.pixelHeight;
        this.xStackA_P2 = this.xStackA_P2_pixels * this.pixelWidth;
        this.yStackA_P2 = this.yStackA_P2_pixels * this.pixelHeight;
        this.xStackA_P3 = this.xStackA_P3_pixels * this.pixelWidth;
        this.yStackA_P3 = this.yStackA_P3_pixels * this.pixelHeight;

        zStackA_P1 = stackA_P1_sliceNr * this.pixelDepth;
        zStackA_P2 = stackA_P2_sliceNr * this.pixelDepth;
        zStackA_P3 = stackA_P3_sliceNr * this.pixelDepth;

        return stackA_dup;
    }

    public void initRegistration() {
        IJ.run("Line Width...", "line=4");
        IJ.setForegroundColor(255, 0, 0);
        IJ.setTool("rectangle");
        int nImages = WindowManager.getImageCount();
        if (nImages != 2) {
            MessageDialog md = new MessageDialog(new Frame(), "BiDiFuse", "Error: only two images should be open!");
            md.setVisible(true);
            throw new RuntimeException(Macro.MACRO_CANCELED);
        }

        //*************************************************//
        //***** Get image info & prepare for rotation *****//
        //*************************************************//
        //Get ImageIDs
        int[] imageIds = WindowManager.getIDList();
        this.im1 = WindowManager.getImage(imageIds[0]);
        this.imPath1 = IJ.getDirectory("image");
        this.im2 = WindowManager.getImage(imageIds[1]);
        this.imPath2 = IJ.getDirectory("image");
        new WindowOrganizer().run("tile");
        //Open GUI
        BiDiFuse_Orientation BiDiFuse_Orient = new BiDiFuse_Orientation();
        enable_help = BiDiFuse_Orient.run(im1, im2);
        //Get Channel and slice
        stackA_id = WindowManager.getCurrentImage();
        registration_channel = stackA_id.getC();
        int sliceA = stackA_id.getSlice();
        if (stackA_id == im1) {
            stackB_id = im2;
        } else {
            stackB_id = im1;
        }
        //Retrieve image information
        initImageInfo();
        //Isolate one channel from stackB_id
        ImagePlus stackB_onechannel = new ImagePlus("stackB_onechannel", ChannelSplitter.getChannel(stackB_id, registration_channel));
        stackB_onechannel.setTitle("Stack B");
        IJ.run(stackB_onechannel, "Grays", "");
        stackB_onechannel.show();
        new WindowOrganizer().run("tile");

        //*********************************//
        //******Retrieve ROI-userinput*****//
        //*********************************//
        ImagePlus stackA_dup = stackAUserInput(stackA_id,sliceA);
        stackBUserInput(stackB_onechannel);
        stackA_dup.changes = false;
        stackA_dup.close();
        stackB_onechannel.changes = false;
        stackB_onechannel.close();
        String outDir = "";
        if (this.imPath1 == null) {
            outDir = IJ.getDirectory("Select output directory:");
        } else {
            outDir = this.imPath1;
        }

        //*********************************//
        //******Write parameters to file*****//
        //*********************************//
        this.outputFile = new File(outDir, stackA_title + " Fusion coordinates.txt");
        logRotation(this.outputFile);
        IJ.log("Coordinates saved to " + this.outputFile.toString());

    }

    public void registration() {

        
        Progress.update(Progress.START);
        IJ.log("---- BiDiFuse start registration -----");
        initRegistration();
        Progress.update(Progress.INIT);

        IJ.log("---- BiDiFuse end registration -----");
        YesNoCancelDialog dialog = new YesNoCancelDialog(new Frame(), "BiDiFuse", "Start BiDiFuse Fusion?");
        if (dialog.cancelPressed()) {
            throw new RuntimeException(Macro.MACRO_CANCELED);
        } else if (dialog.yesPressed()) {
            try {
                BiDiFuse_Fusion fusion = new BiDiFuse_Fusion();
                fusion.run("");
            } catch (Exception e) {
                IJ.log("An error occured, from which BiDiFuse could not recover. Please contact Jan Detrez (Jan.Detrez@uantwerpen.be) with the following error attached: \n" + e.getMessage());
            }
        }

    }
            
    @Override
    public void run(String arg) {
        registration();
    }

    public static void main(String[] args) {
        //Set the plugins.dir property to make the plugin appear in the Plugins menu
        Class<?> clazz = BiDiFuse_Registration.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);
        // start ImageJ
        new ImageJ();
        // Open two images for testing
        //String pathRect = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629_MV/test BiDiFuse/Stack A.tif";
        //String pathVers = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629_MV/test BiDiFuse/Stack B.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/bol/7008brainx10Wz1Ex543stackrecto-ZC-NS.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/bol/7008brainx10Wz1Ex543stackverso-ZC-NS.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/7008brainx10Wz1Ex543stackrecto-ZC.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/7008brainx10Wz1Ex543stackverso-ZC.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/nstack A.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/MB/nstack B.tif";
//        String pathRect = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/JD/MC/Stack A.tif";
//        String pathVers = "C:/Users/Michael/Google Drive/Manuscripts/Submitted/Detrez et al., BI (BIOINF-2016-0580)/BiDiFuse Plugin/BiDiFuse Plugin Demo Data/JD/MC/Stack B.tif";
        //String pathRect = "C:/Users/Michael/Desktop/bidifuse_temp/BiDiFuse_Stack A-1.tif";
        //String pathVers = "C:/Users/Michael/Desktop/bidifuse_temp/BiDiFuse_Stack B-1.tif";
        String pathRect = "C:/Users/Michael/Desktop/MB/Stack A-1.tif";
        String pathVers = "C:/Users/Michael/Desktop/MB/Stack B-1.tif";

//        String pathRect = "C:/Users/Michael/Desktop/7008brainx10Wz1Ex543stackrecto.tif";
//        String pathVers = "C:/Users/Michael/Desktop/7008brainx10Wz1Ex543stackverso.tif";

        //String pathRect = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629_MV/test BiDiFuse/Stack A2.tif";
        //String pathVers = "/Users/marliesverschuuren/Dropbox/PhD/General_Scripts/Fiji/PlugIn/BiDiFuse_160629_MV/test BiDiFuse/Stack B2.tif";
        IJ.openImage(pathRect).show();
        IJ.openImage(pathVers).show();
        // run the plugin
        IJ.runPlugIn(clazz.getName(), "");
    }

    
}

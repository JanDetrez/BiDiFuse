package BiDiFuse;

import ij.IJ;
import ij.plugin.WindowOrganizer;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.WindowManager;
import ij.gui.YesNoCancelDialog;
import ij.plugin.ChannelSplitter;
import ij.plugin.StackReverser;
import ij.process.ImageProcessor;
import java.awt.*;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import javafx.scene.layout.Border;
import javax.swing.border.TitledBorder;

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

public class BiDiFuse_Orientation {

    JButton zFlipButton;
    JButton horizontalFlip;
    JButton verticalFlip;
    JButton startButton;
    JCheckBox helpCheckBox;
    JLabel label;
    boolean start = false;
    boolean enableHelp = false;

    ExecutorService exec = Executors.newFixedThreadPool(1);

    public boolean run(ImagePlus im1, ImagePlus im2) {
        //Setup Buttons
        zFlipButton = new JButton("Z-Flip");
        zFlipButton.setToolTipText("Reverse slice order of active image");
        zFlipButton.addActionListener(listener);
        horizontalFlip = new JButton("Mirror Horizontal");
        horizontalFlip.setToolTipText("Mirror Horizontal");
        horizontalFlip.addActionListener(listener);
        verticalFlip = new JButton("Mirror Vertical");
        verticalFlip.setToolTipText("Mirror Vertical");
        verticalFlip.addActionListener(listener);
        startButton = new JButton("Start");
        startButton.setToolTipText("Start BiDiFuse");
        startButton.addActionListener(listener);
        helpCheckBox = new JCheckBox("Help",true);
        helpCheckBox.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));

        // Button panel
        JPanel buttonJPanel = new JPanel(new GridBagLayout());
        GridBagConstraints buttonPanelConstraints = new GridBagConstraints();
        buttonPanelConstraints.anchor = GridBagConstraints.NORTHWEST;
        buttonPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
        buttonPanelConstraints.insets = new Insets(6, 5, 6, 5);//top 6
        buttonPanelConstraints.gridy = 0;
        buttonPanelConstraints.gridx = 0;
        buttonJPanel.add(zFlipButton, buttonPanelConstraints);
        buttonPanelConstraints.gridy++;
        buttonJPanel.add(horizontalFlip, buttonPanelConstraints);
        buttonPanelConstraints.gridy++;
        buttonJPanel.add(verticalFlip, buttonPanelConstraints);
        buttonPanelConstraints.gridx = 0;
        buttonPanelConstraints.gridy++;
        buttonPanelConstraints.gridwidth = 2;
        buttonPanelConstraints.anchor = GridBagConstraints.CENTER;

        // Start panel
        JPanel startJPanel = new JPanel(new GridBagLayout());
        GridBagConstraints startPanelConstraints = new GridBagConstraints();
        startPanelConstraints.anchor = GridBagConstraints.NORTHWEST;
        startPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
        startPanelConstraints.insets = new Insets(6, 5, 6, 5);
        startPanelConstraints.gridx = 0;
        startPanelConstraints.gridy = 0;
        startJPanel.add(startButton, startPanelConstraints);

        JFrame win = new JFrame("BiDiFuse");
        GridBagLayout winLayout = new GridBagLayout();
        win.setLayout(winLayout);
        GridBagConstraints winConstraints = new GridBagConstraints();
        winConstraints.insets = new Insets(6, 5, 6, 5);
        winConstraints.anchor = GridBagConstraints.NORTHWEST;
        winConstraints.fill = GridBagConstraints.NONE;
        winConstraints.gridx = 0;
        winConstraints.gridy = 0;
        label = new JLabel("BiDiFuse");
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        winConstraints.gridwidth = 3;
        label = new JLabel("A plugin for fusing bi-directionally recorded microscopic image volumes");
        label.setFont(new Font(Font.DIALOG, Font.ITALIC, 12));
        win.add(label, winConstraints);
        winConstraints.gridwidth = 1;
        winConstraints.gridy++;
        win.add(helpCheckBox, winConstraints);
        
        winConstraints.gridy++;
        winConstraints.gridwidth = 1;
        label = new JLabel("Step 1");
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        winConstraints.gridwidth = 2;
        label = new JLabel("Make sure the orientation of the stacks is as follows:");
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        winConstraints.insets = new Insets(6, 5, 0, 5);
        label = new JLabel("> Make sure the slices in stack A and B are in the same order.");
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        //winConstraints.insets = new Insets(0, 5, 6, 5);
        //label = new JLabel("    (Stack A: high to low quality; stack B: low to high quality)");
        //win.add(label, winConstraints);
        winConstraints.insets = new Insets(6, 5, 0, 5);
        winConstraints.gridy++;
        label = new JLabel("> Mirror the images, so the stacks have the same orientation");
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        winConstraints.insets = new Insets(0, 5, 6, 5);
        label = new JLabel("   (left = left; right = right)");
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        win.add(label, winConstraints);
        winConstraints.insets = new Insets(6, 5, 6, 5);
        winConstraints.gridy++;
        //label = new JLabel("Changes can be made using the flip & mirror buttons");
        //label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        //win.add(label, winConstraints);
        
        winConstraints.gridy=4;
        winConstraints.gridx=3;
        winConstraints.gridheight=6;
        win.add(buttonJPanel,winConstraints);
        
        winConstraints.gridy=10;
        winConstraints.gridx=0;
        winConstraints.gridheight=1;
        winConstraints.gridwidth=1;
        label = new JLabel("Step 2");
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        winConstraints.gridwidth=2;
        label = new JLabel("Set one of the image stacks (and channel) as active window");
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        win.add(label, winConstraints);
        winConstraints.gridy++;
        //label = new JLabel("To continue, press START");
        //win.add(label, winConstraints);
        
        winConstraints.gridy--;
        winConstraints.gridheight=2;
        winConstraints.gridx=3;
        winConstraints.fill=GridBagConstraints.HORIZONTAL;
        win.add(startJPanel,winConstraints);

        win.pack();
        win.setVisible(true);

        while (!start) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
        }
        win.dispatchEvent(new WindowEvent(win, WindowEvent.WINDOW_CLOSING));
        int[] imageIds = WindowManager.getIDList();
        im1 = WindowManager.getImage(imageIds[0]);
        String imPath1 = IJ.getDirectory("image");
        IJ.log("Saving oriented images to " + imPath1);
        IJ.saveAsTiff(im1, imPath1 + "BiDiFuse_" + im1.getTitle());
        im2 = WindowManager.getImage(imageIds[1]);
        String imPath2 = IJ.getDirectory("image");
        IJ.saveAsTiff(im2, imPath2 + "BiDiFuse_" +  im2.getTitle());

        win.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                //IJ.log("closing window");
                //cleanup
                exec.shutdownNow();
                zFlipButton.removeActionListener(listener);
                startButton.removeActionListener(listener);
                verticalFlip.removeActionListener(listener);
                horizontalFlip.removeActionListener(listener);
            }
        }
        );
        if (helpCheckBox.isSelected()) {
            enableHelp = true;
        } else {
            enableHelp = false;
        }
        return enableHelp;
    }

    private ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
            // listen to the buttons
            exec.submit(new Runnable() {
                public void run() {
                    ImagePlus imp_all_channels = WindowManager.getCurrentImage();
                    ImageStack is = ChannelSplitter.getChannel(imp_all_channels, 1);
                    ImagePlus imp = new ImagePlus( "temp_channel", is );
                    int nChannels = imp_all_channels.getNChannels();
                    for ( int j = 1; j <= nChannels; j++ ) {
                        imp = new ImagePlus( "temp stack for orientation channel", ChannelSplitter.getChannel(imp_all_channels, j) );
                        if (e.getSource() == zFlipButton) {
                            StackReverser sr = new StackReverser();
                            sr.flipStack(imp);
                            //for (int i = 1; i <= imp.getNSlices(); i++) {
                            //    is = imp.getImageStack();
                            //    ImageStack is2 = new ImageStack();
                            //    ImageProcessor tip = imp.getImageStack().getProcessor(i);
                            //    ImageProcessor tip_last = imp.getImageStack().getProcessor(imp.getNSlices()-i+1)
                            //    imp.getImageStack().setProcessor( , i);
                            //    imp.setSlice(i);
                            //    imp.getProcessor().flipHorizontal();
                            //}
                            //IJ.run(imp, "Reverse", "");
                            //IJ.run(imp, "Flip Z", "");
                        } else if (e.getSource() == horizontalFlip) {
                            int sliceNumber = imp.getCurrentSlice();
                            for (int i = 1; i <= imp.getNSlices(); i++) {
                                imp.setSlice(i);
                                imp.getProcessor().flipHorizontal();
                            }
                            imp.setSlice(sliceNumber);
                        } else if (e.getSource() == verticalFlip) {
                            int sliceNumber = imp.getCurrentSlice();
                            for (int i = 1; i <= imp.getNSlices(); i++) {
                                imp.setSlice(i);
                                imp.getProcessor().flipVertical();
                            }
                            imp.setSlice(sliceNumber);
                        } else if (e.getSource() == helpCheckBox) {
                            // TODO: we do this for every channel: this is not necessary
                            enableHelp = true;
                        } else if (e.getSource() == startButton) {
                            // TODO: we do this for every channel: this is not necessary
                            start = true;
                        }
                    }
                }
            }
            );
        }

    };

}

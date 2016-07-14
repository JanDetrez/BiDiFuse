var SuggestedTransitionIntensityPointRecto="";
var SuggestedTransitionEdgePointRecto="";
var rotation_interpolation="None "	//"None" = fast, "Bilinear" or "Bicubic"  = better result
setBatchMode(1);

macro "BiDiFuseFusing [2]" {
	//Find fusion data txt
	///Find txt files for open images
	txtpathfound=0;
	for (i=1;i<(nImages+1);i++){
		selectImage(i);
		imtitle=getTitle;
		imagedir=getDirectory("image");
		txtpath=imagedir+"\\"+imtitle+" Fusion coordinates.txt";
		if (File.exists(txtpath)) {
			txtpathfound=1;
			i=1e99;
		}
	}

	//Read coordinates from txt	
	if (txtpathfound) {print("Reading "+txtpath);}
	else {waitForUser("No coordinates file (*.txt) found. Set landmarks first!");error;}
	txtpath_string=File.openAsString(txtpath);
	txtpath_split=split(txtpath_string,";\n");
	txtpath_split_onreturn=split(txtpath_string,";\n");
	txtpath_split_ontab_variables=newArray(lengthOf(txtpath_split_onreturn));
	for (i=0;i<lengthOf(txtpath_split_onreturn);i++){
		txtpath_split_ontab=split(txtpath_split_onreturn[i],"\t");
		txtpath_split_ontab_variables[i]=txtpath_split_ontab[1];
	}
	
	recto_title=txtpath_split_ontab_variables[0];
	verso_title=txtpath_split_ontab_variables[1];
	xrecto_O_pixels=parseFloat(txtpath_split_ontab_variables[2]);
	yrecto_O_pixels=parseFloat(txtpath_split_ontab_variables[3]);
	xverso_O_pixels=parseFloat(txtpath_split_ontab_variables[4]);
	yverso_O_pixels=parseFloat(txtpath_split_ontab_variables[5]);
	recto_O_slicenr=parseFloat(txtpath_split_ontab_variables[6]);
	verso_O_slicenr=parseFloat(txtpath_split_ontab_variables[7]);
	X_angle_deg=parseFloat(txtpath_split_ontab_variables[8]);
	Y_angle_deg=parseFloat(txtpath_split_ontab_variables[9]);
	Z_angle_deg_OP1=parseFloat(txtpath_split_ontab_variables[10]);
	Z_angle_deg=parseFloat(txtpath_split_ontab_variables[11]);
	checkboxFlipZ=txtpath_split_ontab_variables[12];
	radiobuttonFlipping=txtpath_split_ontab_variables[13];

	//Ask user: Which channels should be fused?, what should be the transition point
	selectImage(recto_title);
	Stack.getDimensions(width, height, channels, rectoslices, frames);
	selectImage(verso_title);
	Stack.getDimensions(width, height, channels, versoslices, frames);

	offset_recto_verso=verso_O_slicenr-recto_O_slicenr;
	DetermineFusionPoint(recto_title, verso_title, offset_recto_verso);

	Dialog.create("BiDiFuse Fusion");
	
	if (channels>1) {
		items = newArray(channels+1);
		items[0]="all";
		for (i=1;i<channels+1;i++){
			items[i]=toString(i);
		}
		Dialog.addRadioButtonGroup("Multiple channels detected.\nWhich channels should be fused?", items, 1, 4, "all");	
		Dialog.addMessage("____________________________________________"); 
	}
	else {
		radiobuttonChannnelChoice = "1" ;
	}
	
	Dialog.addSlider("Select the transition point ", 1, rectoslices, recto_O_slicenr);		
	Dialog.addMessage("Transition point based on first landmark: "+recto_O_slicenr); 
	Dialog.addMessage("Transition point based on image intensity: "+SuggestedTransitionIntensityPointRecto); 
	Dialog.addMessage("Transition point based on image sharpness: "+SuggestedTransitionEdgePointRecto); 
	Dialog.addMessage("____________________________________________"); 
	Dialog.addCheckbox("Smooth Transition", 1);
	Dialog.show;
	
	SelectedTransitionPointRecto = Dialog.getNumber();
	SmoothTransition = Dialog.getCheckbox();
	SelectedTransitionPointVerso = SelectedTransitionPointRecto + offset_recto_verso;
	if (channels>1) {radiobuttonChannnelChoice = Dialog.getRadioButton();}

	//Rotate image stacks
	if (radiobuttonChannnelChoice=="all"){
		for (channel_to_rotate=1; channel_to_rotate<channels+1; channel_to_rotate++) {
			setBatchMode(1);
			BiDiFuseFusion(channel_to_rotate);
		}
	}
	else{
		setBatchMode(1);
		channel_to_rotate=radiobuttonChannnelChoice;
		BiDiFuseFusion(channel_to_rotate);
	}
}

function BiDiFuseFusion(channel_to_rotate) {
	print("Starting image fusion, channel "+toString(channel_to_rotate));
	
	///////////////////////////////////////////////////////////////////
	//Set origin of verso image in centre//////////////////////////////
	//calculate maximum enlargement
	/*
	side_a = abs(xverso_O-width/2);
	side_b = abs(xverso_O-width/2);
	distance_of_centre = abs(sqrt(pow(side_a,2)+pow(side_b,2)));
	maximum_distance_after_rotation = abs(sqrt(pow(height,2)+pow(width,2)));
	maximum_canvas=distance_of_centre+maximum_distance_after_rotation;
	*/
	selectImage(verso_title);
	Stack.getDimensions(width, height, channels, slices, frames);
	getVoxelSize(pixelWidth, pixelHeight, pixelDepth, unit);
	original_bitdepth=bitDepth();
	maximum_canvas=width*2;
	
	//Make new image to perform rotation, paste origin (xverso_O_pixels,yverso_O_pixels) in image centre
	newImage("Translated_Verso_Image", original_bitdepth+"-bit black", maximum_canvas, maximum_canvas, versoslices);
	run("Properties...", "pixel_width="+pixelWidth+" pixel_height="+pixelHeight+" voxel_depth="+pixelDepth);
	translated_verso_image=getImageID;

	selectImage(verso_title);
	Stack.setChannel(channel_to_rotate);
	
	for(i=1; i<versoslices+1; i++){
		selectImage(verso_title);
		Stack.setSlice(i);
		run("Select All");
		run("Copy");
		selectImage(translated_verso_image);
		Stack.setSlice(i);
		//Paste with origin (O) in image centre
		makeRectangle(maximum_canvas/2-xverso_O_pixels, maximum_canvas/2-yverso_O_pixels, width, height);
		run("Paste");
	}

	//Reverse and flip to have the same orientation as registration image stack
	selectImage(translated_verso_image);

	if (radiobuttonFlipping=="Vertically") {run("Flip Vertically", "stack");};
	else if (radiobuttonFlipping=="Horizontally") {run("Flip Horizontally", "stack");};

	if (checkboxFlipZ=="true") {run("Reverse");};


	///////////////////////////////////////////////////////////////////
	//Allign O-P with X-axis///////////////////////////////////////////
	selectImage(translated_verso_image);
	run("Select None");
	run("Rotate... ", "angle="+Z_angle_deg_OP1+" grid=1 interpolation="+rotation_interpolation+"  stack");
	rename("Ready for TransformJ");
setBatchMode(0);waitForUser;

	///////////////////////////////////////////////////////////////////
	//Excecute rotation of image in XYZ ///////////////////////////////
	Z_angle_deg_OP1_inv = Z_angle_deg_OP1*-1;
	Z_angle_total_deg = Z_angle_deg + Z_angle_deg_OP1_inv;
	
	selectImage(translated_verso_image);
	run("Select None");
	if ((abs(Y_angle_deg)+abs(X_angle_deg))>0) {run("TransformJ Rotate", "z-angle=0 y-angle="+Y_angle_deg+" x-angle="+X_angle_deg+" interpolation=Linear");}
	run("Rotate... ", "angle="+Z_angle_total_deg+" grid=1 interpolation="+rotation_interpolation+"  stack"); //now done after X and Y rotation
	rename("After TransformJ - channel "+channel_to_rotate);
	rotatedbytransformJ=getImageID();
setBatchMode(0);waitForUser;

	///////////////////////////////////////////////////////////////////
	//Merge recto-verso iamges ////////////////////////////////////////
	//Get selection bounds of image 'rotatedbytransformJ'
	selectImage(rotatedbytransformJ);
	setSlice(nSlices/2);
	run("Duplicate...", " ");
	rotatedbytransformJ_dup=getImageID();
	selectImage(rotatedbytransformJ_dup);
	setAutoThreshold("Default");
	setThreshold(1, 1e99);
	run("Convert to Mask");
	run("Create Selection");
	getSelectionCoordinates(xCoordinates, yCoordinates);
	Array.getStatistics(xCoordinates, minX, maxX, mean, stdDev);
	Array.getStatistics(yCoordinates, minY, maxY, mean, stdDev);
	selectImage(rotatedbytransformJ_dup);close();

	//Trim verso stack to FOV of recto stack
	selectImage(rotatedbytransformJ);
	getDimensions(rotatedbytransformJ_width, rotatedbytransformJ_height, channels, versoslices, frames);
	distance_versO_minX=rotatedbytransformJ_width/2-minX;
	distance_versO_minY=rotatedbytransformJ_height/2-minY;
	pastelengthX=maxX-minX;
	pastelengthY=maxY-minY;

	selectImage(rotatedbytransformJ);
	makeRectangle(minX, minY, pastelengthX, pastelengthY);	
	run("Crop");	
	
	//Make smooth transition between stacks
	if (SmoothTransition) {		
		print("Smooth transition enabled");
			
		//Make verso stack with lineair intensity increase	(Z already is flipped)
		selectImage(rotatedbytransformJ);
		run("Duplicate...", "duplicate range="+(SelectedTransitionPointVerso-4)+"-"+(SelectedTransitionPointVerso+4));
		SmoothImageVerso_id=getImageID();
		selectImage(SmoothImageVerso_id);

		for (i=1;i<10;i++){
			setSlice(i);
			PctIntensity=toString(i/10);
			run("Multiply...", "value="+PctIntensity+" slice");
		}

		//Make recto stack with lineair intensity increase
		selectImage(recto_title);
		if (Stack.isHyperstack) { //Duplicate for: hyperstack => slices=5; non-hyperstack => range=5-5
			run("Duplicate...", "duplicate channels="+channel_to_rotate+" slices="+(SelectedTransitionPointRecto-4)+"-"+(SelectedTransitionPointRecto+4));
		}
		else {
			run("Duplicate...", "duplicate range="+(SelectedTransitionPointRecto-4)+"-"+(SelectedTransitionPointRecto+4));
		}		
		SmoothImageRecto_id=getImageID();
		selectImage(SmoothImageRecto_id);

		for (i=1;i<10;i++){
			setSlice(i);
			PctIntensity=toString(1-(i/10));
			run("Multiply...", "value="+PctIntensity+" slice");
		}

		//Translate recto stack to verso stack
		selectImage(SmoothImageVerso_id);
		run("Duplicate...", "duplicate");
		SmoothImageRectoTranslated_id=getImageID();

		selectImage(SmoothImageRecto_id);
		getDimensions(RECTOwidth, RECTOheight, channels, rectoslices, frames);
		setForegroundColor(0, 0, 0);

		for(i=1; i<10; i++){
			selectImage(SmoothImageRecto_id);
			Stack.setSlice(i);
			run("Select All");
			run("Copy");
			
			selectImage(SmoothImageRectoTranslated_id);
			Stack.setSlice(i);
			run("Select All");
			run("Fill", "slice");
			makeRectangle(distance_versO_minX-xrecto_O_pixels, distance_versO_minY-yrecto_O_pixels, RECTOwidth, RECTOheight);
			run("Paste");
		}
			
		//Combine both stacks		
		selectImage(SmoothImageRectoTranslated_id); rename("SmoothImageRectoTranslated");	
		selectImage(SmoothImageVerso_id); rename("SmoothImageVerso");	
		imageCalculator("Add create stack", "SmoothImageRectoTranslated", "SmoothImageVerso");
		SmoothImageID=getImageID(); 
		selectImage(SmoothImageRecto_id);close();
		selectImage(SmoothImageRectoTranslated_id);close();
		selectImage(SmoothImageVerso_id);close();

		//Adjust trimming of recto/verso stack to include smooth slices
		WeirdDetour=SelectedTransitionPointRecto;SelectedTransitionPointRecto=WeirdDetour-4;
		WeirdDetour=SelectedTransitionPointVerso;SelectedTransitionPointVerso=WeirdDetour+4;
	}
	
	//Trim slices of verso stack up to the transition point
	selectImage(rotatedbytransformJ);
	setSlice(1);
	for(i=1; i<SelectedTransitionPointVerso+1; i++){
		run("Delete Slice");
	}

	//Reverse verso stack, necessary since 'add slice' can only add slices after (not before) the active slice
	selectImage(rotatedbytransformJ);
	run("Reverse");	
	setSlice(nSlices);

	//Add if SmoothTransition slices if required		
	if (SmoothTransition) {	
		selectImage(rotatedbytransformJ);
		setSlice(nSlices);

		selectImage(SmoothImageID);
		run("Reverse");

		for(i=1; i<10; i++){
			selectImage(SmoothImageID);
			Stack.setSlice(i);
			run("Select All");
			run("Copy");
			
			selectImage(rotatedbytransformJ);
			run("Add Slice");
			run("Select All");
			run("Paste");
		}
		
	}

	//Copy recto images to verso stack
	selectImage(recto_title);
	if (Stack.isHyperstack) {Stack.setChannel(channel_to_rotate);}
	getDimensions(RECTOwidth, RECTOheight, channels, rectoslices, frames);

	selectImage(rotatedbytransformJ);
	setSlice(nSlices);			
	
	for(i=SelectedTransitionPointRecto; i>0; i--){
		selectImage(recto_title);
		Stack.setSlice(i);
		run("Select All");
		run("Copy");
		
		selectImage(rotatedbytransformJ);
		run("Add Slice");
		makeRectangle(distance_versO_minX-xrecto_O_pixels, distance_versO_minY-yrecto_O_pixels, RECTOwidth, RECTOheight);
		run("Paste");
	}

	selectImage(recto_title);
	run("Select None");	
	selectImage(verso_title);
	run("Select None");	
	selectImage(rotatedbytransformJ);
	run("Select None");	
	
	//Re-Reverse verso stack to normal order
	selectImage(rotatedbytransformJ);
	run("Reverse");

	//Finish image rotation function
	setBatchMode(0);
	resetMinAndMax();	
	print("Finished image fusion, channel "+toString(channel_to_rotate));
}

//Compare intensity between image stacks
function DetermineFusionPoint (recto_title,verso_title, offset_recto_verso) {
	//Prepare intensity stacks
	selectImage(verso_title);
	run("Duplicate...", "duplicate channels=1");
	verso_dup=getImageID();
	selectImage(verso_dup);
	if (checkboxFlipZ=="true") {run("Reverse");};	
	Stack.getDimensions(width, height, channels, versoslices, frames);
	run("Select None");	

	selectImage(recto_title);
	Stack.getDimensions(width, height, channels, rectoslices, frames);
	run("Select None");

	//Prepare edge stacks
	selectImage(verso_dup);
	run("Duplicate...", "duplicate");
	verso_dup_edge=getImageID();
	selectImage(verso_dup_edge);	
	run("Find Edges", "stack");

	selectImage(recto_title);
	run("Duplicate...", "duplicate");
	recto_dup_edge=getImageID();
	selectImage(recto_dup_edge);	
	run("Find Edges", "stack");

	//Compare intensity and edge information betweens stacks
	IntensityTransitionFound=0;
	EdgeTransitionFound=0;
	
	for (i=1; i<rectoslices; i++){
		rectoslice=i;
		versoslice=i+offset_recto_verso;

		if (versoslice>0 && versoslice<versoslices) {
			//Compare intensity
			if (IntensityTransitionFound==0) {
				selectImage(recto_title);
				Stack.setSlice(rectoslice);
				getRawStatistics(nPixels, RECTOmean, min, max, std, histogram);
	
				selectImage(verso_title);
				Stack.setSlice(versoslice);
				getRawStatistics(nPixels, VERSOmean, min, max, std, histogram);			
	
				recto_verso_intensitydiff=RECTOmean-VERSOmean;
				if (recto_verso_intensitydiff<0) {
					SuggestedTransitionIntensityPointRecto=rectoslice;
					IntensityTransitionFound=1;
				}
			}

			//Compare edge
			if (EdgeTransitionFound==0) {
				selectImage(recto_dup_edge);
				Stack.setSlice(rectoslice);
				getRawStatistics(nPixels, RECTOmean, min, max, std, histogram);
	
				selectImage(verso_dup_edge);
				Stack.setSlice(versoslice);
				getRawStatistics(nPixels, VERSOmean, min, max, std, histogram);	
	
				recto_verso_intensitydiff=RECTOmean-VERSOmean;
				if (recto_verso_intensitydiff<0) {
					SuggestedTransitionEdgePointRecto=rectoslice;
					EdgeTransitionFound=1;
				}
			}
		}
	}	

	selectImage(verso_dup);close();
	selectImage(recto_dup_edge);close();
	selectImage(verso_dup_edge);close();
}

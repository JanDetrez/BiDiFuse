///////////////////////////////////////////////////////////////////
// SCRIPT INFO ////////////////////////////////////////////////////
// Title: BiDiFuse
// Written by: Jan Detrez
// Last update: 
// Description: This script will fuse two bi-directional recorded
// image stacks, provided 3 manually indicated landmark points.

///////////////////////////////////////////////////////////////////
// INITIAL PARAMETERS /////////////////////////////////////////////
var enable_help = 0;					//Enable/disable more dialogs to guide user true the workflow
var rotation_interpolation="Bilinear "	//"None" = fast, "Bilinear" or "Bicubic"  = better result

///////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////
// DO NO EDIT PAST THIS POINT /////////////////////////////////////
var checkboxFlipZ="";
var radiobuttonFlipping="";
var radiobuttonChannnelChoice="";

macro "BiDiFuseRegistration [1]" {
	///////////////////////////////////////////////////////////////////
	// Initialise script //////////////////////////////////////////////
	print("===========Starting BiDiFuse============");
	setBatchMode(0);
	run("Line Width...", "line=2");
	setForegroundColor(255, 0, 0);
	setTool("rectangle");
	if (nImages()!=2) {waitForUser("Error: 2 images should be open!");}

	///////////////////////////////////////////////////////////////////
	// Get image info & prepare for rotation //////////////////////////
	//Get ImageIDs
	selectImage(1);im1=getImageID;
	selectImage(2);im2=getImageID;
	run("Tile");
	
	//Get stack info
	selectImage(im1);
	Stack.getDimensions(width, height, channels, slices, frames);
	run("Select None");
	selectImage(im2);
	Stack.getDimensions(width, height, channels, slices, frames);
	run("Select None");
	
	//Get and Set reference stack
	waitForUser("1. Set the reference stack as active window\n2. Select the slice (and channel) to identify landmarks on\n3. Press OK");
	recto_id=getImageID;
	Stack.getPosition(registration_channel, not_used, not_used); //rebuttal
	if (recto_id==im1) {verso_id=im2;} 
	else {verso_id=im1;}
	
	//Get image info
	selectImage(recto_id);
	recto_title=getTitle();
	getDimensions(RECTOwidth, RECTOheight, channels, rectoslices, frames);
	selectImage(verso_id);
	verso_title=getTitle();
	getVoxelSize(pixelWidth, pixelHeight, pixelDepth, unit);
	getDimensions(VERSOwidth, VERSOheight, channels, versoslices, frames);

	//Retrieve information on flip and Reverse Z
	startGUI_flip_reverse();
		
	//Isolate one channel from verso_id, Flip and Reverse Z
	selectImage(verso_id);
	run("Duplicate...", "duplicate channels="+registration_channel);
	verso_id_onechannel=getImageID;
	selectImage(verso_id_onechannel);
	run("Grays");
	
	if (radiobuttonFlipping=="Vertically") {run("Flip Vertically", "stack");};
	else if (radiobuttonFlipping=="Horizontally") {run("Flip Horizontally", "stack");};
	
	if (checkboxFlipZ==1) {run("Reverse");}; //This operation can only be done on non-hyperstacks

	///////////////////////////////////////////////////////////////////
	//Retrieve ROI-userinput///////////////////////////////////////////
	//Prepare for user-input
	setTool("multipoint");
	roiManager("Reset");
	
	selectImage(recto_id);
	selectImage(verso_id);

	selectImage(recto_id);
	run("Duplicate...", "duplicate channels="+registration_channel);
	recto_id_onechannel=getImageID;   //Rebuttal
	run("Grays");
	
	//Get user-input for recto stack
	if(enable_help) {waitForUser("Add 2 characterstic points (O, P1) to the ROI manager [T]");}
	ROIcount=0;
	while (ROIcount<2) {
		ROIcount=roiManager("count");
		wait(250);
	
		if (ROIcount==2){
			roiManager("select", 0); xrecto_O_pixels=getROIcoordinates("x"); yrecto_O_pixels=getROIcoordinates("y");
			roiManager("select", 1); xrecto_P1_pixels=getROIcoordinates("x"); yrecto_P1_pixels=getROIcoordinates("y");
			
			drawlineORTH(recto_id_onechannel,xrecto_O_pixels,yrecto_O_pixels,xrecto_P1_pixels,yrecto_P1_pixels);
			recto_id_onechannel=getImageID;
			
			if(enable_help) {waitForUser("Add a third characterstic point (P2) to the ROI manager [T]");}
	
			while (ROIcount==2) {
				ROIcount=roiManager("count");	
				wait(250);
			}
		}
	}
	
	roiManager("select", 2); xrecto_P2_pixels=getROIcoordinates("x"); yrecto_P2_pixels=getROIcoordinates("y");
	xrecto_O=xrecto_O_pixels*pixelWidth;
	yrecto_O=yrecto_O_pixels*pixelHeight;
	xrecto_P1=xrecto_P1_pixels*pixelWidth;
	yrecto_P1=yrecto_P1_pixels*pixelHeight;
	xrecto_P2=xrecto_P2_pixels*pixelWidth;
	yrecto_P2=yrecto_P2_pixels*pixelHeight;

	roiManager("select", 0); zrecto_O=getSliceNumber()*pixelDepth;  //rebuttal
	roiManager("select", 1); zrecto_P1=getSliceNumber()*pixelDepth;
	roiManager("select", 2); zrecto_P2=getSliceNumber()*pixelDepth;

	print(xrecto_O);print(yrecto_O);print(xrecto_P1);
	print(yrecto_P1);print(xrecto_P2);print(yrecto_P2);
	print(zrecto_O);print(zrecto_P1);print(zrecto_P2);

	
	//Get user-input for verso stack
	selectImage(recto_id_onechannel);
	makeOval(xrecto_O_pixels-6, yrecto_O_pixels-6, 12, 12);run("Draw", "slice");
	makeOval(xrecto_P1_pixels-6, yrecto_P1_pixels-6, 12, 12);run("Draw", "slice");
	makeOval(xrecto_P2_pixels-6, yrecto_P2_pixels-6, 12, 12);run("Draw", "slice");
	run("Select None");
	
	roiManager("Reset");
	ROIcount=0;
	selectImage(verso_id_onechannel);
	if(enable_help) {waitForUser("Find the 3 characterstic points (O, P1, P2) in stack B, and add to the ROI manager [T]");}
	while (ROIcount<3) {
		ROIcount=roiManager("count");
		wait(250);
	}
	
	roiManager("select", 0); xverso_O_pixels=getROIcoordinates ("x"); yverso_O_pixels=getROIcoordinates ("y");
	roiManager("select", 1); xverso_P1_pixels=getROIcoordinates ("x"); yverso_P1_pixels=getROIcoordinates ("y");
	roiManager("select", 2); xverso_P2_pixels=getROIcoordinates ("x"); yverso_P2_pixels=getROIcoordinates ("y");
	
	xverso_O=xverso_O_pixels*pixelWidth;
	yverso_O=yverso_O_pixels*pixelHeight;
	xverso_P1=xverso_P1_pixels*pixelWidth;
	yverso_P1=yverso_P1_pixels*pixelHeight;
	xverso_P2=xverso_P2_pixels*pixelWidth;
	yverso_P2=yverso_P2_pixels*pixelHeight;

	roiManager("select", 0); zverso_O=getSliceNumber()*pixelDepth;  //rebuttal: overal variable 'verslo_slicenr' aanpassen
	roiManager("select", 1); zverso_P1=getSliceNumber()*pixelDepth;
	roiManager("select", 2); zverso_P2=getSliceNumber()*pixelDepth;

	print(xverso_O);print(yverso_O);print(xverso_P1);
	print(yverso_P1);print(xverso_P2);print(yverso_P2);
	print(zverso_O);print(zverso_P1);print(zverso_P2);
	
	//Close registration images
	selectImage(recto_id_onechannel);close();
	selectImage(verso_id_onechannel);close();
	setTool("rectangle");	
	
	waitForUser;
	
	///////////////////////////////////////////////////////////////////
	//Calculate angle of Z-rotation to allign O-P with X-axis//////////
	//Stack A     /Rebuttal
	Z_angle_rad_A_OP1 = atan2(yrecto_O-yrecto_P1, xrecto_P1-xrecto_O); //y reversed
	Z_angle_rad_A_OP1 = opposing_angle(Z_angle_rad_A_OP1);
	Z_angle_deg_A_OP1 = Z_angle_rad_A_OP1 * 180 / PI;
	
	//Stack B
	Z_angle_rad_B_OP1 = atan2(yverso_O-yverso_P1, xverso_P1-xverso_O); //y reversed
	Z_angle_rad_B_OP1 = opposing_angle(Z_angle_rad_B_OP1);
	Z_angle_deg_B_OP1 = Z_angle_rad_B_OP1 * 180 / PI;
	
	///////////////////////////////////////////////////////////////////
	//Calculate rotation about Y-axis (O-P1 is X-axis)/////////////////
	//Calculate new coordinates of xverso_P1
	xverso_P1_afterXYrot=(xverso_P1-xverso_O)*cos(Z_angle_rad_B_OP1)-(yverso_P1-yverso_O)*sin(Z_angle_rad_B_OP1)+xverso_O; //y not reversed
	xrecto_P1_afterXYrot=(xrecto_P1-xrecto_O)*cos(Z_angle_rad_A_OP1)-(yrecto_P1-yrecto_O)*sin(Z_angle_rad_A_OP1)+xrecto_O;  //y not reversed   //Rebuttal
	
	//Calculate angle of rotation
	Y_angle_rad = atan2(zverso_P1-zverso_O, xverso_P1_afterXYrot-xverso_O) - atan2(zrecto_P1-zrecto_O, xrecto_P1_afterXYrot-xrecto_O);   //Rebuttal
	Y_angle_rad = opposing_angle(Y_angle_rad);
	Y_angle_deg = Y_angle_rad * 180 / PI;	

/*
	Y_angle_rad = atan2(zverso_P1-zverso_O, xverso_P1_afterXYrot-xverso_O);   //Rebuttal
	Y_angle_rad = opposing_angle(Y_angle_rad);
	Y_angle_deg = Y_angle_rad * 180 / PI;	
print("Y_angle_deg "+Y_angle_deg);
*/

	///////////////////////////////////////////////////////////////////
	//Calculate rotation about X-axis (O-P2 is Y-axis)/////////////////
	//Calculate new coordinates of yverso_P2
	yverso_P2_afterXYrot=(yverso_P2-yverso_O)*cos(Z_angle_rad_B_OP1)+(xverso_P2-xverso_O)*sin(Z_angle_rad_B_OP1)+yverso_O; //y not reversed
	yrecto_P2_afterXYrot=(yrecto_P2-yrecto_O)*cos(Z_angle_rad_A_OP1)+(xrecto_P2-xrecto_O)*sin(Z_angle_rad_A_OP1)+yrecto_O; //y not reversed   //Rebuttal

	//Calculate angle of rotation
	X_angle_rad = atan2(zverso_P2-zverso_O, yverso_O-yverso_P2_afterXYrot) - atan2(zrecto_P2-zrecto_O, yrecto_O-yrecto_P2_afterXYrot); //y reversed    //Rebuttal
	X_angle_rad = opposing_angle(X_angle_rad);	
	X_angle_deg = X_angle_rad * 180 / PI;	

/*	
	X_angle_rad = atan2(zverso_P2-zverso_O, yverso_O-yverso_P2_afterXYrot); //y reversed   //Rebuttal
	X_angle_rad = opposing_angle(X_angle_rad);	
	X_angle_deg = X_angle_rad * 180 / PI;	
print("X_angle_deg "+X_angle_deg);
*/
	
	///////////////////////////////////////////////////////////////////
	//Calculate rotation about Z-axis /////////////////////////////////
	//Express P1 as origin of respective images
	Ocorrected_xrecto_P1=xrecto_P1-xrecto_O;
	Ocorrected_yrecto_P1=yrecto_O-yrecto_P1; //reversed
	Ocorrected_xverso_P1=xverso_P1-xverso_O;
	Ocorrected_yverso_P1=yverso_O-yverso_P1; //reversed
	
	//Find angle between vector O-P1 (recto) and O-P1 (verso)
	Z_angle_rad = atan2(Ocorrected_yverso_P1, Ocorrected_xverso_P1) - atan2(Ocorrected_yrecto_P1,Ocorrected_xrecto_P1); //y not reversed (already done in previous lines)
	Z_angle_rad = opposing_angle(Z_angle_rad);	
	Z_angle_deg = Z_angle_rad * 180 / PI;	
	
	///////////////////////////////////////////////////////////////////
	//Write fusion data txt ///////////////////////////////////////////
	print("\\Clear");
	print("recto_title\t"+recto_title);
	print("verso_title\t"+verso_title);
	print("xrecto_O_pixels\t"+xrecto_O_pixels);
	print("yrecto_O_pixels\t"+yrecto_O_pixels);
	print("xverso_O_pixels\t"+xverso_O_pixels);
	print("yverso_O_pixels\t"+yverso_O_pixels);
	print("zrecto_O\t"+zrecto_O);
	print("zverso_O\t"+zverso_O);
	print("X_angle_deg\t"+X_angle_deg);
	print("Y_angle_deg\t"+Y_angle_deg);
	print("Z_angle_deg_B_OP1\t"+Z_angle_deg_B_OP1); //rebuttal
	print("Z_angle_deg\t"+Z_angle_deg);
	print("checkboxFlipZ\t"+checkboxFlipZ); //Check if Z is already flipped when images are open
	print("radiobuttonFlipping\t"+radiobuttonFlipping);  //Check if already mirrorred when images are open

	imagedir=getDirectory("image");
	selectWindow("Log");
	saveAs("Text",imagedir+"\\"+recto_title+" Fusion coordinates.txt");
	
	print("===========BiDiFuse Finished============");
	selectWindow("Log");
}

//draw a line orthogonal to the line between xy1 an xy2, and crossing xy1.
function drawlineORTH(imageid,x1,y1,x2,y2) {
	lineslope=-1/((y2-y1)/(x2-x1));
	
	Xzero=0;
	Yzero=0;
	YforXzero=y1-(lineslope*(x1-Xzero));
	XforYzero=(Yzero-y1+(lineslope*x1))/lineslope;
	if (YforXzero<0) {
		YforXzero=y1-(lineslope*(x1-width));
		Xzero=width;
	}
	if (XforYzero<0) {
		XforYzero=(height-y1+(lineslope*x1))/lineslope;
		Yzero=height;
	}

	selectImage(imageid);
	run("RGB Color");
	run("Select None");
	makeLine(XforYzero, Yzero, Xzero, YforXzero); //(startx,starty,endx,endy)
	setForegroundColor(255, 0, 0);
	run("Fill", "stack");  //Rebuttal
	run("Select None");			
}

//Get coordinates from points in ROI tool
function getROIcoordinates (x_or_y) {
	if (x_or_y=="x"){
		Roi.getCoordinates(xpoints, ypoints); 	
		XP=xpoints[lengthOf(xpoints)-1];
		return XP
	}
	else if (x_or_y=="y") {
		Roi.getCoordinates(xpoints, ypoints); 	
		YP=ypoints[lengthOf(ypoints)-1];
		return YP
	}
			
}

//GUI to get Flip/Mirror settings
function startGUI_flip_reverse(){
	Dialog.create("BiDiFuse")
	Dialog.create("Radio Buttons");
	items = newArray("Horizontally", "Vertically", "Not flipped");
	Dialog.addRadioButtonGroup("In which direction is the stack flipped?", items, 3, 1, "Horizontally");
	Dialog.addCheckbox("Flip Z", 1);
	Dialog.show;
	checkboxFlipZ = Dialog.getCheckbox();
	radiobuttonFlipping = Dialog.getRadioButton();	  
}

//Take opposing angle in radians (-180 or +180°, if over/under (+/-)90°)
function opposing_angle(angle_in_radians){
	while(angle_in_radians>(PI/2)) {angle_in_radians=(PI-angle_in_radians)*-1;}
	while(angle_in_radians<(-PI/2)) {angle_in_radians=angle_in_radians+PI;}
	return angle_in_radians
}




/*******************************************************/
/*******************************************************/
/*******************************************************/
/*******************************************************/

//Paste last version of BiDiFuseFusion

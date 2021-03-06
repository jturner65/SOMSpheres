package SOM_Sphere_PKG;

import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;			//used for threading

import processing.core.*;
import processing.opengl.*;
/**
 * Experiment with self organizing maps in applications related to graphics
 * 
 * John Turner
 * 
 */
public class SOM_SphereMain extends PApplet {
	//project-specific variables
	public String prjNmLong = "Building Animation Via SOM Interaction", prjNmShrt = "SOM_VisAnim";
	
	//data in files creatued by somoclu separated by spaces
	public String SOM_FileToken = " ", csvFileToken = "\\s*,\\s*";
	//platform independent path separator
	public String dirSep = File.separator;
	
	//		public String CMUBaseDataDir = "E:/Research/Summer 2016/MS head model research/";		
			
	public String exeDir = Paths.get("").toAbsolutePath().toString();
	//base directory to write output from spheres/samples and from som analysis
	public String BaseWriteDir = exeDir + "\\data\\";
	//location of somoclu exe
	//NOTE this program uses a modified version of the somoclu library, modified to also build a file holding the per-feature best nodes
	public String SOM_Dir = BaseWriteDir;
	
	//		//build classifications and SOM maps of CMU mocap clips
	//		//class file data was from website and may not align with data used - seems some clips and subjects may be missing
	//		public String CMUclassFileName = CMUBaseDataDir + "SubjectsAndMotions.txt";
	//		public String outFileName = CMUBaseDataDir + "subjMotionClass.csv";
	//		
	//		//analyze mocap moments SOM results - file name MmntsHeadData_useAll_1_100_out_2548.bm
	//		public String MmntSOMResDir = CMUBaseDataDir + "MatlabResData/MocapHeadTreeAnalysis/SOM_MmntsData/SOM_results/";
	//		//base location in mocap data to save various formatted training data label csvs
	//		public String MmntSOMSrcDir = CMUBaseDataDir + "MatlabResData/MocapHeadTreeAnalysis/SOM_MmntsData/SourceData/";
	
	//		//TODO replace with generated names
	//		public String[] mSOMResFileAra = new String[]{(MmntSOMResDir+"MmntsHeadData_useAll_0_100_out_2548"),(MmntSOMResDir+"MmntsHeadData_useAll_1_100_out_2548")},
	//				mSOMSrcFileAra = new String[]{MmntSOMSrcDir+"ScaledHeadMtxData_useAll_0.csv",MmntSOMSrcDir+"ScaledHeadMtxData_useAll_1.csv"},
	//				mSOMSrcMinsAra = new String[]{MmntSOMSrcDir+"ScaledHeadMtxData_mins_0.csv",MmntSOMSrcDir+"ScaledHeadMtxData_mins_1.csv"},
	//				mSOMSrcDiffsAra = new String[]{MmntSOMSrcDir+"ScaledHeadMtxData_diffs_0.csv",MmntSOMSrcDir+"ScaledHeadMtxData_diffs_1.csv"};	
	//		public int useAllMmnts = 1;		//0 uses only MOM results, 1 uses both mean/var/skew/kurt and MOM	
			
	//holds training data
	public boolean init = false; 
	
	public final int drawnTrajEditWidth = 10; //TODO make ui component			//width in cntl points of the amount of the drawn trajectory deformed by dragging
	public final float
				PopUpWinOpenFraction = .40f,				//fraction of screen not covered by popwindow
				wScale = frameRate/5.0f,					//velocity drag scaling	
				trajDragScaleAmt = 100.0f;					//amt of displacement when dragging drawn trajectory to edit
			
	public String msClkStr = "";
			
	public int glblStartSimTime, glblLastSimTime;
	
	//CODE STARTS
	public static void main(String[] passedArgs) {		
		String[] appletArgs = new String[] { "SOM_Sphere_PKG.SOM_SphereMain" };
		    if (passedArgs != null) {
		    	PApplet.main(PApplet.concat(appletArgs, passedArgs));
		    } else {
		    	PApplet.main(appletArgs);
		    }
	}//main
	public void settings(){			
		size((int)(displayWidth*.95f), (int)(displayHeight*.9f),P3D);	
	}
	public void setup(){
		initOnce();
		setBkgrnd();	
	}//setup	
	
	public void setBkgrnd(){
		background(bground[0],bground[1],bground[2],bground[3]);		
		//add custom background stuff here - TODO move to myDispWindow	
	}
	
	//called once at start of program
	public void initOnce(){
		initVisOnce();						//always first
		sceneIDX = 1;//(flags[show3D] ? 1 : 0);
		glblStartSimTime = millis() ;
		glblLastSimTime =  millis();		
		numThreadsAvail = Runtime.getRuntime().availableProcessors();
		pr("# threads : "+ numThreadsAvail);
		th_exec = Executors.newFixedThreadPool(numThreadsAvail);
		//th_exec = Executors.newCachedThreadPool();
		
		focusTar = new myVector(sceneFcsVals[sceneIDX]);
		 
		initDispWins();
		setFlags(showUIMenu, true);					//show input UI menu	
		setFlags(showAnimRes, true);
		setCamView(); 
		initProgram();
	}//initOnce
	
	//called multiple times, whenever re-initing
	public void initProgram(){
		initVisProg();				//always first
		drawCount = 0;
	}//initProgram
	
	public void draw(){	
		animCntr = (animCntr + (baseAnimSpd )*animModMult) % maxAnimCntr;						//set animcntr - used only to animate visuals		
		//cyclModCmp = (drawCount % ((mySideBarMenu)dispWinFrames[dispMenuIDX]).guiObjs[((mySideBarMenu)dispWinFrames[dispMenuIDX]).gIDX_cycModDraw].valAsInt() == 0);
		//if ((!cyclModCmp) || (flags[runSim])) {drawCount++;}						//needed to stop draw update so that pausing sim retains animation positions			
		//simulation section
		glblStartSimTime = millis();
		float modAmtSec = (glblStartSimTime - glblLastSimTime)/1000.0f;
		glblLastSimTime = millis();
		if(flags[runSim] ){
			//outStr2Scr("Sim Time elapsed in seconds : " + modAmtSec);
			//run simulation
			drawCount++;									//needed to stop draw update so that pausing sim retains animation positions	
			for(int i =1; i<numDispWins; ++i){if((isShowingWindow(i)) && (dispWinFrames[i].getFlags(myDispWindow.isRunnable))){dispWinFrames[i].simulate(modAmtSec);}}
			//if(flags[singleStep]){flags[runSim]=false;}
			simCycles++;
		}		//play in current window

		//drawing section
		pushMatrix();pushStyle();
		drawSetup();																//initialize camera, lights and scene orientation and set up eye movement
		translate(focusTar.x,focusTar.y,focusTar.z);								//focus location center of screen					
		if((curFocusWin == -1) || (dispWinIs3D[curFocusWin])){		
			setBkgrnd();
			draw3D_solve3D();
			drawBoxBnds();
//			c.buildCanvas();
//			c.drawMseEdge();
//			popStyle();popMatrix(); 
		}	
			//2d windows paint window box so background is always cleared
		c.buildCanvas();
		c.drawMseEdge();
		popStyle();popMatrix(); 
		for(int i =1; i<numDispWins; ++i){if (isShowingWindow(i) && !(dispWinFrames[i].getFlags(myDispWindow.is3DWin))){dispWinFrames[i].draw2D();}}
		
		drawUI();																	//draw UI overlay on top of rendered results			
		if (flags[saveAnim]) {	savePic();}
		updateConsoleStrs();
		surface.setTitle(prjNmLong + " : " + (int)(frameRate) + " fps|cyc ");
	}//draw
	
	private void updateConsoleStrs(){
		++drawCount;
		if(drawCount % cnslStrDecay == 0){
			drawCount = 0;
			consoleStrings.poll();
		}			
	}
	
	public void draw3D_solve3D(){
		pushMatrix();pushStyle();
		for(int i =1; i<numDispWins; ++i){
			if((isShowingWindow(i)) && (dispWinFrames[i].getFlags(myDispWindow.is3DWin))){
				dispWinFrames[i].draw3D(myPoint._add(sceneCtrVals[sceneIDX],focusTar));
			}
		}
		popStyle();popMatrix();
		//fixed xyz rgb axes for visualisation purposes and to show movement and location in otherwise empty scene
		drawAxes(100,3, new myPoint(-c.viewDimW/2.0f+40,0.0f,0.0f), 200, false); 		
		//if((canShow3DBox[this.curFocusWin]) && (flags[clearBKG])) {drawBoxBnds();}
	}
	
	//		public void draw3D_solve2D(){
	//			if (cyclModCmp) {															//if drawing this frame, draw results of calculations								
	//				background(bground[0],bground[1],bground[2],bground[3]);				//if refreshing screen, this clears screen, sets background
	//				pushMatrix();pushStyle();
	//				translateSceneCtr();				//move to center of 3d volume to start drawing	
	//				popStyle();popMatrix();
	//				drawAxes(100,3, new myPoint(-c.viewDimW/2.0f+40,0.0f,0.0f), 200, false); 		//for visualisation purposes and to show movement and location in otherwise empty scene
	//			}
	//		}		
	
//	public void buildCanvas(){
//		c.buildCanvas();
//		c.drawMseEdge();
//	}
	
	//if should show problem # i
	public boolean isShowingWindow(int i){return flags[(i+this.showUIMenu)];}//showUIMenu is first flag of window showing flags
	public void drawUI(){					
		//for(int i =1; i<numDispWins; ++i){if ( !(dispWinFrames[i].getFlags(myDispWindow.is3DWin))){dispWinFrames[i].draw(sceneCtrVals[sceneIDX]);}}
		//dispWinFrames[0].draw(sceneCtrVals[sceneIDX]);
		for(int i =1; i<numDispWins; ++i){dispWinFrames[i].drawHeader();}
		//menu
		dispWinFrames[0].draw2D();
		dispWinFrames[0].drawHeader();
		drawOnScreenData();				//debug and on-screen data
		hint(PConstants.DISABLE_DEPTH_TEST);
		noLights();
		displayHeader();				//my pic and name
		lights();
		hint(PConstants.ENABLE_DEPTH_TEST);
	}//drawUI	
	public void translateSceneCtr(){translate(sceneCtrVals[sceneIDX].x,sceneCtrVals[sceneIDX].y,sceneCtrVals[sceneIDX].z);}
	
	//perform full translations for 3d render - scene center and focus tar
	public void translateFull3D(){
		translate(focusTar.x,focusTar.y,focusTar.z);
		translateSceneCtr();
	}
	
	public void setFocus(){
		focusTar.set(sceneFcsVals[(sceneIDX+sceneFcsVals.length)%sceneFcsVals.length]);
		switch (sceneIDX){//special handling for each view
		case 0 : {initProgram();break;} //refocus camera on center
		case 1 : {initProgram();break;}  
		}
	}
	
	public void setCamView(){//also sets idx in scene focus and center arrays
		sceneIDX = (curFocusWin == -1 || this.dispWinIs3D[curFocusWin]) ? 1 : 0;
		rx = (float)cameraInitLocs[sceneIDX].x;
		ry = (float)cameraInitLocs[sceneIDX].y;
		dz = (float)cameraInitLocs[sceneIDX].z;
		setFocus();
	}
	
	//////////////////////////////////////////////////////
	/// user interaction
	//////////////////////////////////////////////////////	
	
	public void keyPressed(){
		switch (key){
			case '1' : {break;}
			case '2' : {break;}
			case '3' : {break;}
			case '4' : {break;}
			case '5' : {break;}							
			case '6' : {break;}
			case '7' : {break;}
			case '8' : {break;}
			case '9' : {break;}
			case '0' : {setFlags(showUIMenu,true); break;}							//to force show UI menu
			case ' ' : {setFlags(runSim,!flags[runSim]); break;}							//run sim
			case 'f' : {setCamView();break;}//reset camera
			case 'a' :
			case 'A' : {setFlags(saveAnim,!flags[saveAnim]);break;}						//start/stop saving every frame for making into animation
			case 's' :
			case 'S' : {save(sketchPath() + "\\"+prjNmLong+dateStr+"\\"+prjNmShrt+"_img"+timeStr + ".jpg");break;}//save picture of current image			
	//				case ';' :
	//				case ':' : {((mySideBarMenu)dispWinFrames[dispMenuIDX]).guiObjs[((mySideBarMenu)dispWinFrames[dispMenuIDX]).gIDX_cycModDraw].modVal(-1); break;}//decrease the number of cycles between each draw, to some lower bound
	//				case '\'' :
	//				case '"' : {((mySideBarMenu)dispWinFrames[dispMenuIDX]).guiObjs[((mySideBarMenu)dispWinFrames[dispMenuIDX]).gIDX_cycModDraw].modVal(1); break;}//increase the number of cycles between each draw to some upper bound		
			default : {	}
		}//switch	
		
		if((!flags[shiftKeyPressed])&&(key==CODED)){setFlags(shiftKeyPressed,(keyCode  == KeyEvent.VK_SHIFT));}
		if((!flags[altKeyPressed])&&(key==CODED)){setFlags(altKeyPressed,(keyCode  == KeyEvent.VK_ALT));}
		if((!flags[cntlKeyPressed])&&(key==CODED)){setFlags(cntlKeyPressed,(keyCode  == KeyEvent.VK_CONTROL));}
	}
	public void keyReleased(){
		if((flags[shiftKeyPressed])&&(key==CODED)){ if(keyCode == KeyEvent.VK_SHIFT){endShiftKey();}}
		if((flags[altKeyPressed])&&(key==CODED)){ if(keyCode == KeyEvent.VK_ALT){endAltKey();}}
		if((flags[cntlKeyPressed])&&(key==CODED)){ if(keyCode == KeyEvent.VK_CONTROL){endCntlKey();}}
	}		
	public void endShiftKey(){
		clearFlags(new int []{shiftKeyPressed, modView});
		for(int i =0; i<numDispWins; ++i){dispWinFrames[i].endShiftKey();}
	}
	public void endAltKey(){
		clearFlags(new int []{altKeyPressed});
		for(int i =0; i<numDispWins; ++i){dispWinFrames[i].endAltKey();}			
	}
	public void endCntlKey(){
		clearFlags(new int []{cntlKeyPressed});
		for(int i =0; i<numDispWins; ++i){dispWinFrames[i].endCntlKey();}			
	}
	
	//2d range checking of point
	public boolean ptInRange(double x, double y, double minX, double minY, double maxX, double maxY){return ((x > minX)&&(x <= maxX)&&(y > minY)&&(y <= maxY));}	
	//gives multiplier based on whether shift, alt or cntl (or any combo) is pressed
	public double clickValModMult(){
		return ((flags[altKeyPressed] ? .1 : 1.0) * (flags[cntlKeyPressed] ? 10.0 : 1.0));			
	}
	public void mouseMoved(){for(int i =0; i<numDispWins; ++i){if (dispWinFrames[i].handleMouseMove(mouseX, mouseY,c.getMseLoc(sceneCtrVals[sceneIDX]))){return;}}}
	public void mousePressed() {
		//verify left button if(mouseButton == LEFT)
		setFlags(mouseClicked, true);
		if(mouseButton == LEFT){			mouseClicked(0);} 
		else if (mouseButton == RIGHT) {	mouseClicked(1);}
		//for(int i =0; i<numDispWins; ++i){	if (dispWinFrames[i].handleMouseClick(mouseX, mouseY,c.getMseLoc(sceneCtrVals[sceneIDX]))){	return;}}
	}// mousepressed	
	
	private void mouseClicked(int mseBtn){ for(int i =0; i<numDispWins; ++i){if (dispWinFrames[i].handleMouseClick(mouseX, mouseY,c.getMseLoc(sceneCtrVals[sceneIDX]),mseBtn)){return;}}}		
	public void mouseDragged(){//pmouseX is previous mouse x
		if((flags[shiftKeyPressed]) && (canMoveView[curFocusWin])){		//modifying view - always bypass HUD windows if doing this
			flags[modView]=true;
			if(mouseButton == LEFT){			rx-=PI*(mouseY-pmouseY)/height; ry+=PI*(mouseX-pmouseX)/width;} 
			else if (mouseButton == RIGHT) {	dz-=(float)(mouseY-pmouseY);}
		} else {
			if(mouseButton == LEFT){			mouseDragged(0);}
			else if (mouseButton == RIGHT) {	mouseDragged(1);}
		}
	}//mouseDragged()
	private void mouseDragged(int mseBtn){
		for(int i =0; i<numDispWins; ++i){if (dispWinFrames[i].handleMouseDrag(mouseX, mouseY, pmouseX, pmouseY,c.getMseLoc(sceneCtrVals[sceneIDX]),new myVector(c.getOldMseLoc(),c.getMseLoc()),mseBtn)) {return;}}		
	}
	
	public void mouseReleased(){
		clearFlags(new int[]{mouseClicked, modView});
		msClkStr = "";
		for(int i =0; i<numDispWins; ++i){dispWinFrames[i].handleMouseRelease();}
		flags[drawing] = false;
		//c.clearMsDepth();
	}//mouseReleased
	
	//these tie using the UI buttons to modify the window in with using the boolean tags - PITA but currently necessary
	public void handleShowWin(int btn, int val){handleShowWin(btn, val, true);}					//display specific windows - multi-select/ always on if sel
	public void handleShowWin(int btn, int val, boolean callFlags){//display specific windows - multi-select/ always on if sel
		if(!callFlags){//called from setflags - only sets button state in UI
			((mySideBarMenu)dispWinFrames[dispMenuIDX]).guiBtnSt[mySideBarMenu.btnShowWinIdx][btn] = val;
		} else {//called from clicking on buttons in UI
			boolean bVal = (val == 1?  false : true);
			setFlags(10+btn, bVal);
//			switch(btn){
//				case 0 : {setFlags(showAnimRes, bVal);break;}
//				case 1 : {setFlags(showSOMMapUI, bVal);break;}
//			}
		}
	}//handleShowWin
	
	//process to delete an existing component
	public void handleDBGSelCmp(int btn, int val){handleDBGSelCmp(btn, val, true);}					//display specific windows - multi-select/ always on if sel
	public void handleDBGSelCmp(int btn, int val, boolean callFlags){//{"Score","Staff","Measure","Note"},			//del - momentary
		if(!callFlags){
			((mySideBarMenu)dispWinFrames[dispMenuIDX]).guiBtnSt[mySideBarMenu.btnDBGSelCmpIdx][btn] = val;
		} else {
			dispWinFrames[curFocusWin].clickDebug(btn) ;
		}
	}//handleAddDelSelCmp	
	
	//process to handle file io	
	public void handleFileCmd(int btn, int val){handleFileCmd(btn, val, true);}					//display specific windows - multi-select/ always on if sel
	public void handleFileCmd(int btn, int val, boolean callFlags){//{"Load","Save"},							//load an existing score, save an existing score - momentary	
		if(!callFlags){
			((mySideBarMenu)dispWinFrames[dispMenuIDX]).guiBtnSt[mySideBarMenu.btnFileCmdIdx][btn] = val;
		} else {
			switch(btn){
				case 0 : {selectInput("Select a file to load parameters from : ", "loadFromFile");break;}
				case 1 : {selectOutput("Select a file to save parameters to : ", "saveToFile");break;}
			}		
			((mySideBarMenu)dispWinFrames[dispMenuIDX]).hndlMouseRelIndiv();
		}
	}//handleFileCmd
	
	public void loadFromFile(File file){
		if (file == null) {
		    outStr2Scr("Load was cancelled.");
		    return;
		} 
		String[] res = loadStrings(file.getAbsolutePath());
		//stop any simulations while file is loaded
		//load - iterate through for each window
		int[] stIdx = {0};//start index for a particular window - make an array so it can be passed by ref and changed by windows
		for(int i =0; i<numDispWins; ++i){
			while(!res[stIdx[0]].contains(dispWinFrames[i].name)){++stIdx[0];}			
			dispWinFrames[i].hndlFileLoad(res,stIdx);
		}//accumulate array of params to save
		//resume simulations		
	}//loadFromFile
	
	public void saveToFile(File file){
		if (file == null) {
		    outStr2Scr("Save was cancelled.");
		    return;
		} 
		ArrayList<String> res = new ArrayList<String>();
		//save - iterate through for each window
		for(int i =0; i<numDispWins; ++i){
			res.addAll(dispWinFrames[i].hndlFileSave());	
		}//accumulate array of params to save
		saveStrings(file.getAbsolutePath(), res.toArray(new String[0]));  
	}//saveToFile	
	
	public void initDispWins(){
		float popUpWinHeight = PopUpWinOpenFraction * height;		//how high is the InstEdit window when shown
		//instanced window dimensions when open and closed - only showing 1 open at a time
		winRectDimOpen[dispAnimResIDX] =  new float[]{menuWidth+hideWinWidth, 0,width-menuWidth-hideWinWidth,height-hidWinHeight};			
		winRectDimOpen[dispSOMMapIDX]  =  new float[]{menuWidth, popUpWinHeight, width-menuWidth, height-popUpWinHeight};
		//hidden
		winRectDimClose[dispAnimResIDX] =  new float[]{menuWidth, 0, hideWinWidth, height};				
		winRectDimClose[dispSOMMapIDX]  =  new float[]{menuWidth, height-hidWinHeight, width-menuWidth, hidWinHeight};
		
		winTrajFillClrs = new int []{gui_Black,gui_LightGray,gui_LightGreen};		//set to color constants for each window
		winTrajStrkClrs = new int []{gui_Black,gui_DarkGray,gui_White};				//set to color constants for each window			
		
		String[] winTitles = new String[]{"","Animation Result","SOM Map UI"},
				winDescr = new String[] {"", "Animation Result","Build Animation Trajectory on SOM Nodes"};
	//			//display window initialization	
		int wIdx = dispAnimResIDX , fIdx = showAnimRes;
		dispWinFrames[wIdx] = new mySOMAnimResWin(this, winTitles[wIdx], fIdx,winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx],canDrawInWin[wIdx]);
		wIdx = dispSOMMapIDX;fIdx=showSOMMapUI;
		dispWinFrames[wIdx] = new mySOMMapUIWin(this, winTitles[wIdx], fIdx,winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx],canDrawInWin[wIdx]);
		
		for(int i =0; i < numDispWins; ++i){
			dispWinFrames[i].initDrwnTrajs();
			dispWinFrames[i].setFlags(myDispWindow.is3DWin, dispWinIs3D[i]);
			dispWinFrames[i].setTrajColors(winTrajFillClrs[i], winTrajStrkClrs[i]);
		}				
		//load initial map data in mySOMMapUIWin
		//((mySOMMapUIWin)(dispWinFrames[dispSOMMapIDX])).SOM_Data.setAndInitLoadCMUData();	
		//assign refs - equate SOM refs in both windows
		((mySOMAnimResWin)(dispWinFrames[dispAnimResIDX])).SOMSpheres_Data = ((mySOMMapUIWin)(dispWinFrames[dispSOMMapIDX])).SOM_Data;	
		//load initial spheres
		((mySOMAnimResWin)(dispWinFrames[dispAnimResIDX])).initAllSpheres();
	}//initDispWins
	
	//get the ui rect values of the "master" ui region (another window) -> this is so ui objects of one window can be made, clicked, and shown displaced from those of the parent windwo
	public float[] getUIRectVals(int idx){
		//this.pr("In getUIRectVals for idx : " + idx);
		switch(idx){
		case dispMenuIDX 		: {return new float[0];}			//idx 0 is parent menu sidebar
		case dispAnimResIDX 	: {return dispWinFrames[dispMenuIDX].uiClkCoords;}
		case dispSOMMapIDX 	: {	return dispWinFrames[dispAnimResIDX].uiClkCoords;}
		default :  return dispWinFrames[dispMenuIDX].uiClkCoords;
		}
	}	
	
	private String[] sphereSOMLrnFN;		//idx 0 = file name for SOM training data -> .lrn file, 1 = file name for sphere sample data -> .csv file
	//call when spheres are first initialized in mySOMAnimResWin - sets file names for .lrn file  and testing file output from sphere UI
	public void setSphereLRNFileName(int _numSphrs, int _numSmpls, float _maxRad){
		sphereSOMLrnFN = new String[6];
		now = Calendar.getInstance();
		String nowDir = "SphrSOM_"+getDateTimeString(true,false,"_")+"\\", fileNow = getDateTimeString(false,false,"_");
		String[] tmp = {"Out_Sphr_"+_numSphrs+"_Smp_"+_numSmpls,
						"Train_Sphr_"+_numSphrs+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".lrn",
						"Test_Sphr_"+_numSphrs+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"Mins_Sphr_"+_numSphrs+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"Diffs_Sphr_"+_numSphrs+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".csv",
						"SOMImg_Sphr_"+_numSphrs+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".png"
						};
		for(int i =0; i<sphereSOMLrnFN.length;++i){	sphereSOMLrnFN[i] = new String(BaseWriteDir + nowDir + tmp[i]);}				
	}
	
	public String getSphereOutFileBase(){	return sphereSOMLrnFN[0];	}	
	public String getSphereLRNFileName(){	return sphereSOMLrnFN[1];	}
	public String getSphereTestFileName(){	return sphereSOMLrnFN[2];	}
	public String getSphereMinsFileName(){	return sphereSOMLrnFN[3];	}
	public String getSphereDiffsFileName(){	return sphereSOMLrnFN[4];	}
	
	public String getSOMLocClrImgFName(){	return sphereSOMLrnFN[5];}
	
	public boolean curSphereDatSaved(){	return dispWinFrames[dispAnimResIDX].getPrivFlags(mySOMAnimResWin.currSphrDatSavedIDX);	}
	//let anim res window know that map has been made for current sphere configuration
	public void setCurSphrMapMade(){dispWinFrames[dispAnimResIDX].setPrivFlags(mySOMAnimResWin.mapBuiltToCurSphrsIDX, true);}
	
	
	public dataPoint[] getSphrWinTrainData(){return ((mySOMAnimResWin)dispWinFrames[dispAnimResIDX]).sphereTrainData;}
	public dataPoint[] getSphrWinTestData(){return ((mySOMAnimResWin)dispWinFrames[dispAnimResIDX]).sphereTestData;}

	
	//////////////////////////////////////////
	/// graphics and base functionality utilities and variables
	//////////////////////////////////////////
	public static final int txtSz = 10;
	//constant path strings for different file types
	public static final String fileDelim = "\\";	
	//display-related size variables
	public final int grid2D_X=800, grid2D_Y=800;	
	public final int gridDimX = 800, gridDimY = 800, gridDimZ = 800;				//dimensions of 3d region
	//boundary regions for enclosing cube - given as min and difference of min and max
	public float[][] cubeBnds = new float[][]{//idx 0 is min, 1 is diffs
		new float[]{-gridDimX/2.0f,-gridDimY/2.0f,-gridDimZ/2.0f},//mins
		new float[]{gridDimX,gridDimY,gridDimZ}};			//diffs
	
	private final int cnslStrDecay = 3;			//how long a message should last before it is popped from the console strings deque
	
	public int scrWidth, scrHeight;			//set to be applet.width and applet.height unless otherwise specified below
	public final int scrWidthMod = 200, 
			scrHeightMod = 0;
	public final float frate = 120;			//frame rate - # of playback updates per second
	
	public int sceneIDX;			//which idx in the 2d arrays of focus vals and glbl center vals to use, based on state
	public myVector[] sceneFcsVals = new myVector[]{						//set these values to be different targets of focus
			new myVector(-grid2D_X/2,-grid2D_Y/1.75f,0),
			new myVector(0,0,100)
	};
	
	public myPoint[] sceneCtrVals = new myPoint[]{						//set these values to be different display center translations -
			new myPoint(0,0,0),										// to be used to calculate mouse offset in world for pick
			new myPoint(-gridDimX/2.0,-gridDimY/2.0,-gridDimZ/2.0)
	};
	
	private float dz=0, rx=-0.06f*TWO_PI, ry=-0.04f*TWO_PI;		// distance to camera. Manipulated with wheel or when,view angles manipulated when space pressed but not mouse	
	public final float camInitialDist = -200,		//initial distance camera is from scene - needs to be negative
			camInitRy = ry,
			camInitRx = rx;
	
	public myVector[] cameraInitLocs = new myVector[]{						//set these values to be different initial camera locations based on 2d or 3d
			new myVector(camInitRx,camInitRy,camInitialDist),
			new myVector(camInitRx,camInitRy,camInitialDist),
			new myVector(-0.47f,-0.61f,-gridDimZ*.25f)			
		};
	
	//static variables - put obj constructor counters here
	public static int GUIObjID = 0;										//counter variable for gui objs
	
	//visualization variables
	// boolean flags used to control various elements of the program 
	public boolean[] flags;
	//dev/debug flags
	public final int debugMode 			= 0;			//whether we are in debug mode or not	
	public final int saveAnim 			= 1;			//whether we are saving or not
	//interface flags	
	public final int shiftKeyPressed 	= 2;			//shift pressed
	public final int altKeyPressed  	= 3;			//alt pressed
	public final int cntlKeyPressed  	= 4;			//cntrl pressed
	public final int mouseClicked 		= 5;			//mouse left button is held down	
	public final int drawing			= 6; 			//currently drawing
	public final int modView	 		= 7;			//shift+mouse click+mouse move being used to modify the view
	
	public final int runSim				= 8;			//run simulation (if off localization progresses on single pose		
	public final int showUIMenu 		= 9;			//whether or not to show sidebar menu
	
	public final int showAnimRes		= 10;			//whether to show animation result of map traj
	public final int showSOMMapUI		= 11;			//whether to show SOM map nodes upon which we draw
	
	public final int flipDrawnTraj  	= 12;			//whether or not to flip the direction of the drawn melody trajectory
	
	public final int numFlags = 13;
	
	//flags to actually display in menu as clickable text labels - order does matter
	public List<Integer> flagsToShow = Arrays.asList( 
			debugMode, 			
			saveAnim
			);
	
	public final int numFlagsToShow = flagsToShow.size();
	
	public List<Integer> stateFlagsToShow = Arrays.asList( 
			 shiftKeyPressed,			//shift pressed
			 altKeyPressed  ,			//alt pressed
			 cntlKeyPressed ,			//cntrl pressed
			 mouseClicked 	,			//mouse left button is held down	
			 drawing		, 			//currently drawing
			 modView	 				//shift+mouse click+mouse move being used to modify the view					
			);
	public final int numStFlagsToShow = stateFlagsToShow.size();	
	
	
	//individual display/HUD windows for gui/user interaction
	public myDispWindow[] dispWinFrames;
	//idx's in dispWinFrames for each window
	public static final int dispMenuIDX = 0,
							dispAnimResIDX = 1,
							dispSOMMapIDX = 2;
	
	public static final int numDispWins = 3;	
			
	public int curFocusWin;				//which myDispWindow currently has focus 
	
	//whether or not the display windows will accept a drawn trajectory
	public boolean[] canDrawInWin = new boolean[]{false,false,true};		
	public boolean[] canShow3DBox = new boolean[]{false,true,false};		
	public boolean[] canMoveView = new boolean[]{false,true,false};		
	public static final boolean[] dispWinIs3D = new boolean[]{false,true,false};
	
	public static final int[][] winFillClrs = new int[][]{          
		new int[]{255,255,255,255},                                 	// dispMenuIDX = 0,
		new int[]{0,0,0,255},                                        	// dispAnimResIDX = 1;
		new int[]{0,0,0,255}                                        	// dispSOMMapIDX = 2
	};
	public static final int[][] winStrkClrs = new int[][]{
		new int[]{0,0,0,255},                                    		//dispMenuIDX = 0,
		new int[]{255,255,255,255},                               		//dispAnimResIDX = 1
		new int[]{255,255,255,255}                               		//dispSOMMapIDX = 2
	};
	
	public static int[] winTrajFillClrs = new int []{0,0,0};		//set to color constants for each window
	public static int[] winTrajStrkClrs = new int []{0,0,0};		//set to color constants for each window
	
	
	//unblocked window dimensions - location and dim of window if window is one\
	public float[][] winRectDimOpen;// = new float[][]{new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0}};
	//window dimensions if closed -location and dim of all windows if this window is closed
	public float[][] winRectDimClose;// = new float[][]{new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0}};
	
	public boolean showInfo;										//whether or not to show start up instructions for code		
	public myVector focusTar;										//target of focus - used in translate to set where the camera is looking - 
																	//set array of vector values (sceneFcsVals) based on application
	//private boolean cyclModCmp;										//comparison every draw of cycleModDraw			
	public final int[] bground = new int[]{244,244,244,255};		//bground color
			
	public myPoint mseCurLoc2D;
	//how many frames to wait to actually refresh/draw
	//public int cycleModDraw = 1;
	public final int maxCycModDraw = 20;	//max val for cyc mod draw		
	
	// path and filename to save pictures for animation
	public String screenShotPath;
	public int animCounter;	
	public final int scrMsgTime = 50;									//5 seconds to delay a message 60 fps (used against draw count)
	public ArrayDeque<String> consoleStrings;							//data being printed to console - show on screen
	
	public int drawCount,simCycles;												// counter for draw cycles		
	public float menuWidth,menuWidthMult = .15f, hideWinWidth, hideWinWidthMult = .03f, hidWinHeight, hideWinHeightMult = .05f;			//side menu is 15% of screen grid2D_X, 
	
	public ArrayList<String> DebugInfoAra;										//enable drawing dbug info onto screen
	public String debugInfoString;
	
	//animation control variables	
	public float animCntr = 0, animModMult = 1.0f;
	public final float maxAnimCntr = PI*1000.0f, baseAnimSpd = 1.0f;
	
	
	my3DCanvas c;												//3d interaction stuff and mouse tracking
	
	public float[] camVals;		
	public String dateStr, timeStr;								//used to build directory and file names for screencaps
	
	public PGraphicsOpenGL pg; 
	public PGL pgl;
	//public GL2 gl;
	
	public double eps = .000000001, msClkEps = 40;				//calc epsilon, distance within which to check if clicked from a point
	public float feps = .000001f;
	public float SQRT2 = sqrt(2.0f);
	
	public int[] rgbClrs = new int[]{gui_Red,gui_Green,gui_Blue};
	//3dbox stuff
	public myVector[] boxNorms = new myVector[] {new myVector(1,0,0),new myVector(-1,0,0),new myVector(0,1,0),new myVector(0,-1,0),new myVector(0,0,1),new myVector(0,0,-1)};//normals to 3 d bounding boxes
	private final float hGDimX = gridDimX/2.0f, hGDimY = gridDimY/2.0f, hGDimZ = gridDimZ/2.0f;
	private final float tGDimX = gridDimX*10, tGDimY = gridDimY*10, tGDimZ = gridDimZ*20;
	public myPoint[][] boxWallPts = new myPoint[][] {//pts to check if intersection with 3D bounding box happens
			new myPoint[] {new myPoint(hGDimX,tGDimY,tGDimZ), new myPoint(hGDimX,-tGDimY,tGDimZ), new myPoint(hGDimX,tGDimY,-tGDimZ)  },
			new myPoint[] {new myPoint(-hGDimX,tGDimY,tGDimZ), new myPoint(-hGDimX,-tGDimY,tGDimZ), new myPoint(-hGDimX,tGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,hGDimY,tGDimZ), new myPoint(-tGDimX,hGDimY,tGDimZ), new myPoint(tGDimX,hGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,-hGDimY,tGDimZ),new myPoint(-tGDimX,-hGDimY,tGDimZ),new myPoint(tGDimX,-hGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,tGDimY,hGDimZ), new myPoint(-tGDimX,tGDimY,hGDimZ), new myPoint(tGDimX,-tGDimY,hGDimZ)  },
			new myPoint[] {new myPoint(tGDimX,tGDimY,-hGDimZ),new myPoint(-tGDimX,tGDimY,-hGDimZ),new myPoint(tGDimX,-tGDimY,-hGDimZ)  }
	};
	//for multithreading 
	public ExecutorService th_exec;
	public int numThreadsAvail;	
	
	public Calendar now;
	
	///////////////////////////////////
	/// generic graphics functions and classes
	///////////////////////////////////
		//1 time initialization of things that won't change
	public void initVisOnce(){	
		dateStr = "_"+day() + "-"+ month()+ "-"+year();
		timeStr = "_"+hour()+"-"+minute()+"-"+second();
		now = Calendar.getInstance();
		scrWidth = width + scrWidthMod;
		scrHeight = height + scrHeightMod;		//set to be applet.width and applet.height unless otherwise specified below
		
		consoleStrings = new ArrayDeque<String>();				//data being printed to console		
		menuWidth = width * menuWidthMult;						//grid2D_X of menu region	
		hideWinWidth = width * hideWinWidthMult;				//dims for hidden windows
		hidWinHeight = height * hideWinHeightMult;
		c = new my3DCanvas(this);			
		winRectDimOpen = new float[numDispWins][];
		winRectDimClose = new float[numDispWins][];
		winRectDimOpen[0] =  new float[]{0,0, menuWidth, height};
		winRectDimClose[0] =  new float[]{0,0, hideWinWidth, height};
		
		strokeCap(SQUARE);//makes the ends of stroke lines squared off
		
		//display window initialization
		dispWinFrames = new myDispWindow[numDispWins];		
		//menu bar init
		dispWinFrames[dispMenuIDX] = new mySideBarMenu(this, "UI Window", showUIMenu,  winFillClrs[dispMenuIDX], winStrkClrs[dispMenuIDX], winRectDimOpen[dispMenuIDX],winRectDimClose[dispMenuIDX], "User Controls",canDrawInWin[dispMenuIDX]);			
		
		colorMode(RGB, 255, 255, 255, 255);
		mseCurLoc2D = new myPoint(0,0,0);	
		frameRate(frate);
		sphereDetail(4);
		initBoolFlags();
		camVals = new float[]{width/2.0f, height/2.0f, (height/2.0f) / tan(PI/6.0f), width/2.0f, height/2.0f, 0, 0, 1, 0};
		showInfo = true;
		textSize(txtSz);
		outStr2Scr("Current sketchPath " + sketchPath());
		textureMode(NORMAL);			
		rectMode(CORNER);	
		
		initCamView();
		simCycles = 0;
		screenShotPath = sketchPath() + "\\"+prjNmShrt+"_" + (int) random(1000)+"\\";
	}				
		//init boolean state machine flags for program
	public void initBoolFlags(){
		flags = new boolean[numFlags];
		for (int i = 0; i < numFlags; ++i) { flags[i] = false;}	
		((mySideBarMenu)dispWinFrames[dispMenuIDX]).initPFlagColors();			//init sidebar window flags
	}		
	
	//address all flag-setting here, so that if any special cases need to be addressed they can be
	public void setFlags(int idx, boolean val ){
		flags[idx] = val;
		switch (idx){
			case debugMode 			: { break;}//anything special for debugMode 			
			case saveAnim 			: { break;}//anything special for saveAnim 			
			case altKeyPressed 		: { break;}//anything special for altKeyPressed 	
			case shiftKeyPressed 	: { break;}//anything special for shiftKeyPressed 	
			case mouseClicked 		: { break;}//anything special for mouseClicked 		
			case modView	 		: { break;}//anything special for modView	 	
			case drawing			: { break;}
			case runSim			: {break;}// handleTrnsprt((val ? 2 : 1) ,(val ? 1 : 0),false); break;}		//anything special for runSim	
			//case flipDrawnTraj		: { dispWinFrames[dispPianoRollIDX].rebuildDrawnTraj();break;}						//whether or not to flip the drawn melody trajectory, width-wise
			case flipDrawnTraj		: { for(int i =1; i<dispWinFrames.length;++i){dispWinFrames[i].rebuildAllDrawnTrajs();}break;}						//whether or not to flip the drawn melody trajectory, width-wise
			case showUIMenu 	    : { dispWinFrames[dispMenuIDX].setFlags(myDispWindow.showIDX,val);    break;}											//whether or not to show the main ui window (sidebar)
			
			case showAnimRes		: {setWinFlagsXOR(dispAnimResIDX, val); break;}
			case showSOMMapUI 		: {dispWinFrames[dispSOMMapIDX].setFlags(myDispWindow.showIDX,val);handleShowWin(dispSOMMapIDX-1 ,(val ? 1 : 0),false); setWinsHeight(dispSOMMapIDX); break;}	//show InstEdit window
	
			//case useDrawnVels 		: {for(int i =1; i<dispWinFrames.length;++i){dispWinFrames[i].rebuildAllDrawnTrajs();}break;}
			default : {break;}
		}
	}//setFlags  
	
	//set the height of each window that is above the popup window, to move up or down when it changes size
	public void setWinsHeight(int popUpWinIDX){
		for(int i =0;i<winDispIdxXOR.length;++i){//skip first window - ui menu - and last window - InstEdit window
			dispWinFrames[winDispIdxXOR[i]].setRectDimsY( dispWinFrames[popUpWinIDX].getRectDim(1));
		}						
	}			//specify mutually exclusive flags here
	public int[] winFlagsXOR = new int[]{showAnimRes};//showSequence,showSphereUI};
	//specify windows that cannot be shown simultaneously here
	public int[] winDispIdxXOR = new int[]{dispAnimResIDX};//dispPianoRollIDX,dispSphereUIIDX};
	public void setWinFlagsXOR(int idx, boolean val){
		//outStr2Scr("SetWinFlagsXOR : idx " + idx + " val : " + val);
		if(val){//turning one on
			//turn off not shown, turn on shown				
			for(int i =0;i<winDispIdxXOR.length;++i){//skip first window - ui menu - and last window - InstEdit window
				if(winDispIdxXOR[i]!= idx){dispWinFrames[winDispIdxXOR[i]].setFlags(myDispWindow.showIDX,false);handleShowWin(i ,0,false); flags[winFlagsXOR[i]] = false;}
				else {
					dispWinFrames[idx].setFlags(myDispWindow.showIDX,true);
					handleShowWin(i ,1,false); 
					flags[winFlagsXOR[i]] = true;
					curFocusWin = winDispIdxXOR[i];
					setCamView();
				}
			}
		} else {				//if turning off a window - need a default uncloseable window - for now just turn on next window : idx-1 is idx of allowable winwdows (idx 0 is sidebar menu)
			setWinFlagsXOR((((idx-1) + 1) % winFlagsXOR.length)+1, true);
		}			
	}//setWinFlagsXOR
	
	//set flags appropriately when only 1 can be true 
	public void setFlagsXOR(int tIdx, int[] fIdx){for(int i =0;i<fIdx.length;++i){if(tIdx != fIdx[i]){flags[fIdx[i]] =false;}}}				
	public void clearFlags(int[] idxs){		for(int idx : idxs){flags[idx]=false;}	}			
		//called every time re-initialized
	public void initVisProg(){	drawCount = 0;		debugInfoString = "";		reInitInfoStr();}	
	public void initCamView(){	dz=camInitialDist;	ry=camInitRy;	rx=camInitRx - ry;	}
	public void reInitInfoStr(){		DebugInfoAra = new ArrayList<String>();		DebugInfoAra.add("");	}	
	public int addInfoStr(String str){return addInfoStr(DebugInfoAra.size(), str);}
	public int addInfoStr(int idx, String str){	
		int lstIdx = DebugInfoAra.size();
		if(idx >= lstIdx){		for(int i = lstIdx; i <= idx; ++i){	DebugInfoAra.add(i,"");	}}
		setInfoStr(idx,str);	return idx;
	}
	public void setInfoStr(int idx, String str){DebugInfoAra.set(idx,str);	}
	public void drawInfoStr(float sc){//draw text on main part of screen
		pushMatrix();		pushStyle();
		fill(0,0,0,100);
		translate((menuWidth),0);
		scale(sc,sc);
		for(int i = 0; i < DebugInfoAra.size(); ++i){		text((flags[debugMode]?(i<10?"0":"")+i+":     " : "") +"     "+DebugInfoAra.get(i)+"\n\n",0,(10+(12*i)));	}
		popStyle();	popMatrix();
	}		
	//vector and point functions to be compatible with earlier code from jarek's class or previous projects	
	//draw bounding box for 3d
	public void drawBoxBnds(){
		pushMatrix();	pushStyle();
		strokeWeight(3f);
		noFill();
		setColorValStroke(gui_TransGray);
		
		box(gridDimX,gridDimY,gridDimZ);
		popStyle();	popMatrix();
	}		
	//drawsInitial setup for each draw
	public void drawSetup(){			
		perspective(PI/3.0f, (1.0f*width)/(1.0f*height), .5f, camVals[2]*100.0f);
		dispWinFrames[curFocusWin].setCamera(camVals, rx,ry,dz);
		
//		if(flags[rideBoid]){ 
//			((myBoids3DWin) dispWinFrames[disp3DResIDX]).setBoidCam(rx,ry,dz);
//		} else {
//			camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
//			//if(this.flags[this.debugMode]){outStr2Scr("rx :  " + rx + " ry : " + ry + " dz : " + dz);}
//			// puts origin of all drawn objects at screen center and moves forward/away by dz
//			translate(camVals[0],camVals[1],(float)dz); 
//		    setCamOrient();
//		}

	    turnOnLights();
	}//drawSetup	
	//turn on lights for this sketch
	public void turnOnLights(){
	    lights(); 		
	}
	public void setCamOrient(){rotateX(rx);rotateY(ry); rotateX(PI/(2.0f));		}//sets the rx, ry, pi/2 orientation of the camera eye	
	public void unSetCamOrient(){rotateX(-PI/(2.0f)); rotateY(-ry);   rotateX(-rx); }//reverses the rx,ry,pi/2 orientation of the camera eye - paints on screen and is unaffected by camera movement
	public void drawAxes(double len, float stW, myPoint ctr, int alpha, boolean centered){//axes using current global orientation
		pushMatrix();pushStyle();
			strokeWeight(stW);
			stroke(255,0,0,alpha);
			if(centered){
				double off = len*.5f;
				line(ctr.x-off,ctr.y,ctr.z,ctr.x+off,ctr.y,ctr.z);stroke(0,255,0,alpha);line(ctr.x,ctr.y-off,ctr.z,ctr.x,ctr.y+off,ctr.z);stroke(0,0,255,alpha);line(ctr.x,ctr.y,ctr.z-off,ctr.x,ctr.y,ctr.z+off);} 
			else {		line(ctr.x,ctr.y,ctr.z,ctr.x+len,ctr.y,ctr.z);stroke(0,255,0,alpha);line(ctr.x,ctr.y,ctr.z,ctr.x,ctr.y+len,ctr.z);stroke(0,0,255,alpha);line(ctr.x,ctr.y,ctr.z,ctr.x,ctr.y,ctr.z+len);}
		popStyle();	popMatrix();	
	}//	drawAxes
	public void drawAxes(double len, float stW, myPoint ctr, myVector[] _axis, int alpha, boolean drawVerts){//RGB -> XYZ axes
		pushMatrix();pushStyle();
		if(drawVerts){
			show(ctr,3,gui_Black,gui_Black, false);
			for(int i=0;i<_axis.length;++i){show(myPoint._add(ctr, myVector._mult(_axis[i],len)),3,rgbClrs[i],rgbClrs[i], false);}
		}
		strokeWeight(stW);
		for(int i =0; i<3;++i){	setColorValStroke(rgbClrs[i]);	showVec(ctr,len, _axis[i]);	}
		popStyle();	popMatrix();	
	}//	drawAxes
	public void drawAxes(double len, float stW, myPoint ctr, myVector[] _axis, int[] clr, boolean drawVerts){//all axes same color
		pushMatrix();pushStyle();
			if(drawVerts){
				show(ctr,2,gui_Black,gui_Black, false);
				for(int i=0;i<_axis.length;++i){show(myPoint._add(ctr, myVector._mult(_axis[i],len)),2,rgbClrs[i],rgbClrs[i], false);}
			}
			strokeWeight(stW);stroke(clr[0],clr[1],clr[2],clr[3]);
			for(int i =0; i<3;++i){	showVec(ctr,len, _axis[i]);	}
		popStyle();	popMatrix();	
	}//	drawAxes
	
	public void drawText(String str, double x, double y, double z, int clr){
		int[] c = getClr(clr);
		pushMatrix();	pushStyle();
			fill(c[0],c[1],c[2],c[3]);
			unSetCamOrient();
			translate((float)x,(float)y,(float)z);
			text(str,0,0,0);		
		popStyle();	popMatrix();	
	}//drawText	
	//save screenshot
	public void savePic(){		save(screenShotPath + prjNmShrt + ((animCounter < 10) ? "000" : ((animCounter < 100) ? "00" : ((animCounter < 1000) ? "0" : ""))) + animCounter + ".jpg");		animCounter++;		}
	public void line(double x1, double y1, double z1, double x2, double y2, double z2){line((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2 );}
	public void line(myPoint p1, myPoint p2){line((float)p1.x,(float)p1.y,(float)p1.z,(float)p2.x,(float)p2.y,(float)p2.z);}
	
	public void drawOnScreenData(){
		if(flags[debugMode]){
			pushMatrix();pushStyle();			
			reInitInfoStr();
			addInfoStr(0,"mse loc on screen : " + new myPoint(mouseX, mouseY,0) + " mse loc in world :"+c.mseLoc +"  Eye loc in world :"+ c.eyeInWorld+ " camera rx :  " + rx + " ry : " + ry + " dz : " + dz);
			String[] res = ((mySideBarMenu)dispWinFrames[dispMenuIDX]).getDebugData();		//get debug data for each UI object
			int numToPrint = min(res.length,80);
			for(int s=0;s<numToPrint;++s) {	addInfoStr(res[s]);}				//add info to string to be displayed for debug
			drawInfoStr(1.0f); 	
			popStyle();	popMatrix();		
		}
		else if(showInfo){
			pushMatrix();pushStyle();			
			reInitInfoStr();	
			String[] res = consoleStrings.toArray(new String[0]);
			int dispNum = min(res.length, 80);
			for(int i=0;i<dispNum;++i){addInfoStr(res[i]);}
		    drawInfoStr(1.1f); 
			popStyle();	popMatrix();	
		}
	}
	//print out multiple-line text to screen
	public void ml_text(String str, float x, float y){
		String[] res = str.split("\\r?\\n");
		float disp = 0;
		for(int i =0; i<res.length; ++i){
			text(res[i],x, y+disp);		//add console string output to screen display- decays over time
			disp += 12;
		}
	}
	//print out a string ara with perLine # of strings per line
	public void outStr2ScrAra(String[] sAra, int perLine){
		for(int i=0;i<sAra.length; i+=perLine){
			String s = "";
			for(int j=0; j<perLine; ++j){	s+= sAra[i+j]+ "\t";}
			outStr2Scr(s,true);}
	}
	//print out string in display window
	public void outStr2Scr(String str){outStr2Scr(str,true);}
	//print informational string data to console, and to screen
	public void outStr2Scr(String str, boolean showDraw){
		if(trim(str) != ""){	System.out.println(str);}
		String[] res = str.split("\\r?\\n");
		if(showDraw){
			for(int i =0; i<res.length; ++i){
				consoleStrings.add(res[i]);		//add console string output to screen display- decays over time
			}
		}
	}
	//build a date with each component separated by token
	public String getDateTimeString(){return getDateTimeString(true, false,".");}
	public String getDateTimeString(boolean useYear, boolean toSecond, String token){
		String result = "";
		int val;
		if(useYear){val = now.get(Calendar.YEAR);		result += ""+val+token;}
		val = now.get(Calendar.MONTH)+1;				result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.DAY_OF_MONTH);			result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.HOUR_OF_DAY);					result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.MINUTE);					result += (val < 10 ? "0"+val : ""+val);
		if(toSecond){val = now.get(Calendar.SECOND);	result += token + (val < 10 ? "0"+val : ""+val);}
		return result;
	}
	//utilities
	
	//handle user-driven file load or save - returns a filename + filepath string
	public String FileSelected(File selection){
		if (null==selection){return null;}
		return selection.getAbsolutePath();		
	}//FileSelected
	
	//		//s-cut to print to console
	public void pr(String str){outStr2Scr(str);}
	
	public String getFName(String fNameAndPath){
		String[] strs = fNameAndPath.split("/");
		return strs[strs.length-1];
	}
	
	//load a file as text strings
	public String[] loadFileIntoStringAra(String fileName, String dispYesStr, String dispNoStr){
		String[] strs = null;
		try{
			strs = loadStrings(fileName);
			System.out.println(dispYesStr+"\tLength : " + strs.length);
		} catch (Exception e){System.out.println("!!"+dispNoStr);return null;}
		return strs;		
	}//loadFileIntoStrings
	
	//public void scribeHeaderRight(String s) {scribeHeaderRight(s, 20);} // writes black on screen top, right-aligned
	//public void scribeHeaderRight(String s, float y) {fill(0); text(s,width-6*s.length(),y); noFill();} // writes black on screen top, right-aligned
	public void displayHeader() { // Displays title and authors face on screen
	    float stVal = 17;
	    int idx = 1;	
	    translate(0,10,0);
	    fill(0); text("Shift-Click-Drag to change view.",width-190, stVal*idx++); noFill(); 
	    fill(0); text("Shift-RClick-Drag to zoom.",width-160, stVal*idx++); noFill();
	    fill(0); text("John Turner",width-75, stVal*idx++); noFill();	
	    }
	
	//project passed point onto box surface based on location - to help visualize the location in 3d
	public void drawProjOnBox(myPoint p){
		//myPoint[]  projOnPlanes = new myPoint[6];
		myPoint prjOnPlane;
		//public myPoint intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		pushMatrix();
		translate(-p.x,-p.y,-p.z);
		for(int i  = 0; i< 6; ++i){				
			prjOnPlane = bndChkInCntrdBox3D(intersectPl(p, boxNorms[i], boxWallPts[i][0],boxWallPts[i][1],boxWallPts[i][2]));				
			show(prjOnPlane,5,rgbClrs[i/2],rgbClrs[i/2], false);				
		}
		popMatrix();
	}//drawProjOnBox
	private static final double third = 1.0/3.0;
	public myVectorf getRandPosInSphere(double rad, myVectorf ctr){
		myVectorf pos = new myVectorf();
		do{
			double u = ThreadLocalRandom.current().nextDouble(0,1), r = rad * Math.pow(u, third),
					cosTheta = ThreadLocalRandom.current().nextDouble(-1,1), sinTheta =  Math.sin(Math.acos(cosTheta)),
					phi = ThreadLocalRandom.current().nextDouble(0,PConstants.TWO_PI);
			pos.set(sinTheta * Math.cos(phi), sinTheta * Math.sin(phi),cosTheta);
			pos._mult(r);
			pos._add(ctr);
		} while (pos.z < 0);
		return pos;
	}
	
	public myVectorf getRandPosOnSphere(double rad, myVectorf ctr){
		myVectorf pos = new myVectorf();
		//do{
			double 	cosTheta = ThreadLocalRandom.current().nextDouble(-1,1), sinTheta =  Math.sin(Math.acos(cosTheta)),
					phi = ThreadLocalRandom.current().nextDouble(0,PConstants.TWO_PI);
			pos.set(sinTheta * Math.cos(phi), sinTheta * Math.sin(phi),cosTheta);
			pos._mult(rad);
			pos._add(ctr);
		//} while (pos.z < 0);
		return pos;
	}
	//very fast mechanism for setting an array of doubles to a specific val - takes advantage of caching
	public void dAraFill(double[] ara, double val){
		int len = ara.length;
		if (len > 0){ara[0] = val; }
		for (int i = 1; i < len; i += i){  System.arraycopy(ara, 0, ara, i, ((len - i) < i) ? (len - i) : i);  }		
	}
	
	//convert a world location within the bounded cube region to be a 4-int color array
	public int[] getClrFromCubeLoc(float[] t){
		return new int[]{(int)(255*(t[0]-cubeBnds[0][0])/cubeBnds[1][0]),(int)(255*(t[1]-cubeBnds[0][1])/cubeBnds[1][1]),(int)(255*(t[2]-cubeBnds[0][2])/cubeBnds[1][2]),255};
	}
	
	//performs fisher-yates shuffle
	public String[] shuffleStrList(String[] _list, String type){
		String tmp = "";
		for(int i=(_list.length-1);i>0;--i){
			int j = (int)(ThreadLocalRandom.current().nextDouble(0,i));
			tmp = _list[i];
			_list[i] = _list[j];
			_list[j] = tmp;
		//	outStr2Scr("From i : " + i + " to j : " + j);
		}
		outStr2Scr("String list of Sphere " + type + " shuffled");
		return _list;
	}//shuffleStrList
	
	//random location within coords[0] and coords[1] extremal corners of a cube - bnds is to give a margin of possible random values
	public myVectorf getRandPosInCube(float[][] coords, float bnds){
		return new myVectorf(
				ThreadLocalRandom.current().nextDouble(coords[0][0]+bnds,(coords[0][0] + coords[1][0] - bnds)),
				ThreadLocalRandom.current().nextDouble(coords[0][1]+bnds,(coords[0][1] + coords[1][1] - bnds)),
				ThreadLocalRandom.current().nextDouble(coords[0][2]+bnds,(coords[0][2] + coords[1][2] - bnds)));}		
	public myPoint getScrLocOf3dWrldPt(myPoint pt){	return new myPoint(screenX((float)pt.x,(float)pt.y,(float)pt.z),screenY((float)pt.x,(float)pt.y,(float)pt.z),screenZ((float)pt.x,(float)pt.y,(float)pt.z));}
	
	public myPoint bndChkInBox2D(myPoint p){p.set(Math.max(0,Math.min(p.x,grid2D_X)),Math.max(0,Math.min(p.y,grid2D_Y)),0);return p;}
	public myPoint bndChkInBox3D(myPoint p){p.set(Math.max(0,Math.min(p.x,gridDimX)), Math.max(0,Math.min(p.y,gridDimY)),Math.max(0,Math.min(p.z,gridDimZ)));return p;}	
	public myPoint bndChkInCntrdBox3D(myPoint p){
		p.set(Math.max(-hGDimX,Math.min(p.x,hGDimX)), 
				Math.max(-hGDimY,Math.min(p.y,hGDimY)),
				Math.max(-hGDimZ,Math.min(p.z,hGDimZ)));return p;}	
	 
	public void translate(myPoint p){translate((float)p.x,(float)p.y,(float)p.z);}
	public void translate(myPointf p){translate(p.x,p.y,p.z);}
	public void translate(myVector p){translate((float)p.x,(float)p.y,(float)p.z);}
	public void translate(double x, double y, double z){translate((float)x,(float)y,(float)z);}
	public void translate(double x, double y){translate((float)x,(float)y);}
	public void rotate(float thet, myPoint axis){rotate(thet, (float)axis.x,(float)axis.y,(float)axis.z);}
	public void rotate(float thet, double x, double y, double z){rotate(thet, (float)x,(float)y,(float)z);}
	//************************************************************************
	//**** SPIRAL
	//************************************************************************
	//3d rotation - rotate P by angle a around point G and axis normal to plane IJ
	public myPoint R(myPoint P, double a, myVector I, myVector J, myPoint G) {
		double x= myVector._dot(new myVector(G,P),U(I)), y=myVector._dot(new myVector(G,P),U(J)); 
		double c=Math.cos(a), s=Math.sin(a); 
		double iXVal = x*c-x-y*s, jYVal= x*s+y*c-y;			
		return myPoint._add(P,iXVal,I,jYVal,J); }; 
		
	public cntlPt R(cntlPt P, double a, myVector I, myVector J, myPoint G) {
		double x= myVector._dot(new myVector(G,P),U(I)), y=myVector._dot(new myVector(G,P),U(J)); 
		double c=Math.cos(a), s=Math.sin(a); 
		double iXVal = x*c-x-y*s, jYVal= x*s+y*c-y;		
		return new cntlPt(this, P(P,iXVal,I,jYVal,J), P.r, P.w); };
		
	public myPoint PtOnSpiral(myPoint A, myPoint B, myPoint C, double t) {
		//center is coplanar to A and B, and coplanar to B and C, but not necessarily coplanar to A, B and C
		//so center will be coplanar to mp(A,B) and mp(B,C) - use mpCA midpoint to determine plane mpAB-mpBC plane?
		myPoint mAB = new myPoint(A,.5f, B);
		myPoint mBC = new myPoint(B,.5f, C);
		myPoint mCA = new myPoint(C,.5f, A);
		myVector mI = U(mCA,mAB);
		myVector mTmp = myVector._cross(mI,U(mCA,mBC));
		myVector mJ = U(mTmp._cross(mI));	//I and J are orthonormal
		double a =spiralAngle(A,B,B,C); 
		double s =spiralScale(A,B,B,C);
		
		//myPoint G = spiralCenter(a, s, A, B, mI, mJ); 
		myPoint G = spiralCenter(A, mAB, B, mBC); 
		return new myPoint(G, Math.pow(s,t), R(A,t*a,mI,mJ,G));
	  }
	public double spiralAngle(myPoint A, myPoint B, myPoint C, myPoint D) {return myVector._angleBetween(new myVector(A,B),new myVector(C,D));}
	public double spiralScale(myPoint A, myPoint B, myPoint C, myPoint D) {return myPoint._dist(C,D)/ myPoint._dist(A,B);}
	
	public myPoint R(myPoint Q, myPoint C, myPoint P, myPoint R) { // returns rotated version of Q by angle(CP,CR) parallel to plane (C,P,R)
		myVector I0=U(C,P), I1=U(C,R), V=new myVector(C,Q); 
		double c=myPoint._dist(I0,I1), s=Math.sqrt(1.-(c*c)); 
		if(Math.abs(s)<0.00001) return Q;
		myVector J0=V(1./s,I1,-c/s,I0);  
		myVector J1=V(-s,I0,c,J0);  
		double x=V._dot(I0), y=V._dot(J0);  
		return P(Q,x,M(I1,I0),y,M(J1,J0)); 
	} 	
	// spiral given 4 points, AB and CD are edges corresponding through rotation
	public myPoint spiralCenter(myPoint A, myPoint B, myPoint C, myPoint D) {         // new spiral center
		myVector AB=V(A,B), CD=V(C,D), AC=V(A,C);
		double m=CD.magn/AB.magn, n=CD.magn*AB.magn;		
		myVector rotAxis = U(AB._cross(CD));		//expect ab and ac to be coplanar - this is the axis to rotate around to find f
		
		myVector rAB = myVector._rotAroundAxis(AB, rotAxis, PConstants.HALF_PI);
		double c=AB._dot(CD)/n, 
				s=rAB._dot(CD)/n;
		double AB2 = AB._dot(AB), a=AB._dot(AC)/AB2, b=rAB._dot(AC)/AB2;
		double x=(a-m*( a*c+b*s)), y=(b-m*(-a*s+b*c));
		double d=1+m*(m-2*c);  if((c!=1)&&(m!=1)) { x/=d; y/=d; };
		return P(P(A,x,AB),y,rAB);
	  }
	
	
	public void cylinder(myPoint A, myPoint B, float r, int c1, int c2) {
		myPoint P = A;
		myVector V = V(A,B);
		myVector I = c.drawSNorm;//U(Normal(V));
		myVector J = U(N(I,V));
		float da = TWO_PI/36;
		beginShape(QUAD_STRIP);
			for(float a=0; a<=TWO_PI+da; a+=da) {fill(c1); gl_vertex(P(P,r*cos(a),I,r*sin(a),J,0,V)); fill(c2); gl_vertex(P(P,r*cos(a),I,r*sin(a),J,1,V));}
		endShape();
	}
	
	//point functions
	public myPoint P() {return new myPoint(); };                                                                          // point (x,y,z)
	public myPoint P(double x, double y, double z) {return new myPoint(x,y,z); };                                            // point (x,y,z)
	public myPoint P(myPoint A) {return new myPoint(A.x,A.y,A.z); };                                                           // copy of point P
	public myPoint P(myPoint A, double s, myPoint B) {return new myPoint(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };        // A+sAB
	public myPoint L(myPoint A, double s, myPoint B) {return new myPoint(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };        // A+sAB
	public myPoint P(myPoint A, myPoint B) {return P((A.x+B.x)/2.0,(A.y+B.y)/2.0,(A.z+B.z)/2.0); }                             // (A+B)/2
	public myPoint P(myPoint A, myPoint B, myPoint C) {return new myPoint((A.x+B.x+C.x)/3.0,(A.y+B.y+C.y)/3.0,(A.z+B.z+C.z)/3.0); };     // (A+B+C)/3
	public myPoint P(myPoint A, myPoint B, myPoint C, myPoint D) {return P(P(A,B),P(C,D)); };                                            // (A+B+C+D)/4
	public myPoint P(double s, myPoint A) {return new myPoint(s*A.x,s*A.y,s*A.z); };                                            // sA
	public myPoint A(myPoint A, myPoint B) {return new myPoint(A.x+B.x,A.y+B.y,A.z+B.z); };                                         // A+B
	public myPoint P(double a, myPoint A, double b, myPoint B) {return A(P(a,A),P(b,B));}                                        // aA+bB 
	public myPoint P(double a, myPoint A, double b, myPoint B, double c, myPoint C) {return A(P(a,A),P(b,B,c,C));}                     // aA+bB+cC 
	public myPoint P(double a, myPoint A, double b, myPoint B, double c, myPoint C, double d, myPoint D){return A(P(a,A,b,B),P(c,C,d,D));}   // aA+bB+cC+dD
	public myPoint P(myPoint P, myVector V) {return new myPoint(P.x + V.x, P.y + V.y, P.z + V.z); }                                 // P+V
	public myPoint P(myPoint P, double s, myVector V) {return new myPoint(P.x+s*V.x,P.y+s*V.y,P.z+s*V.z);}                           // P+sV
	public myPoint P(myPoint O, double x, myVector I, double y, myVector J) {return P(O.x+x*I.x+y*J.x,O.y+x*I.y+y*J.y,O.z+x*I.z+y*J.z);}  // O+xI+yJ
	public myPoint P(myPoint O, double x, myVector I, double y, myVector J, double z, myVector K) {return P(O.x+x*I.x+y*J.x+z*K.x,O.y+x*I.y+y*J.y+z*K.y,O.z+x*I.z+y*J.z+z*K.z);}  // O+xI+yJ+kZ
	void makePts(myPoint[] C) {for(int i=0; i<C.length; i++) C[i]=P();}
	
	//draw a circle - JT
	void circle(myPoint P, float r, myVector I, myVector J, int n) {myPoint[] pts = new myPoint[n];pts[0] = P(P,r,U(I));float a = (2*PI)/(1.0f*n);for(int i=1;i<n;++i){pts[i] = R(pts[i-1],a,J,I,P);}pushMatrix(); pushStyle();noFill(); show(pts);popStyle();popMatrix();}; // render sphere of radius r and center P
	
	void circle(myPoint p, float r){ellipse((float)p.x, (float)p.y, r, r);}
	void circle(float x, float y, float r1, float r2){ellipse(x,y, r1, r2);}
	
	void noteArc(float[] dims, int[] noteClr){
		noFill();
		setStroke(noteClr);
		strokeWeight(1.5f*dims[3]);
		arc(0,0, dims[2], dims[2], dims[0] - this.HALF_PI, dims[1] - this.HALF_PI);
	}
	//draw a ring segment from alphaSt in radians to alphaEnd in radians
	void noteArc(myPoint ctr, float alphaSt, float alphaEnd, float rad, float thickness, int[] noteClr){
		noFill();
		setStroke(noteClr);
		strokeWeight(thickness);
		arc((float)ctr.x, (float)ctr.y, rad, rad, alphaSt - this.HALF_PI, alphaEnd- this.HALF_PI);
	}
	
	
	void bezier(myPoint A, myPoint B, myPoint C, myPoint D) {bezier((float)A.x,(float)A.y,(float)A.z,(float)B.x,(float)B.y,(float)B.z,(float)C.x,(float)C.y,(float)C.z,(float)D.x,(float)D.y,(float)D.z);} // draws a cubic Bezier curve with control points A, B, C, D
	void bezier(myPoint [] C) {bezier(C[0],C[1],C[2],C[3]);} // draws a cubic Bezier curve with control points A, B, C, D
	myPoint bezierPoint(myPoint[] C, float t) {return P(bezierPoint((float)C[0].x,(float)C[1].x,(float)C[2].x,(float)C[3].x,(float)t),bezierPoint((float)C[0].y,(float)C[1].y,(float)C[2].y,(float)C[3].y,(float)t),bezierPoint((float)C[0].z,(float)C[1].z,(float)C[2].z,(float)C[3].z,(float)t)); }
	myVector bezierTangent(myPoint[] C, float t) {return V(bezierTangent((float)C[0].x,(float)C[1].x,(float)C[2].x,(float)C[3].x,(float)t),bezierTangent((float)C[0].y,(float)C[1].y,(float)C[2].y,(float)C[3].y,(float)t),bezierTangent((float)C[0].z,(float)C[1].z,(float)C[2].z,(float)C[3].z,(float)t)); }
	
	
	public myPoint Mouse() {return new myPoint(mouseX, mouseY,0);}                                          			// current mouse location
	public myVector MouseDrag() {return new myVector(mouseX-pmouseX,mouseY-pmouseY,0);};                     			// vector representing recent mouse displacement
	
	//public int color(myPoint p){return color((int)p.x,(int)p.z,(int)p.y);}	//needs to be x,z,y for some reason - to match orientation of color frames in z-up 3d geometry
	public int color(myPoint p){return color((int)p.x,(int)p.y,(int)p.z);}	
	
	// =====  vector functions
	public myVector V() {return new myVector(); };                                                                          // make vector (x,y,z)
	public myVector V(double x, double y, double z) {return new myVector(x,y,z); };                                            // make vector (x,y,z)
	public myVector V(myVector V) {return new myVector(V.x,V.y,V.z); };                                                          // make copy of vector V
	public myVector A(myVector A, myVector B) {return new myVector(A.x+B.x,A.y+B.y,A.z+B.z); };                                       // A+B
	public myVector A(myVector U, float s, myVector V) {return V(U.x+s*V.x,U.y+s*V.y,U.z+s*V.z);};                               // U+sV
	public myVector M(myVector U, myVector V) {return V(U.x-V.x,U.y-V.y,U.z-V.z);};                                              // U-V
	public myVector M(myVector V) {return V(-V.x,-V.y,-V.z);};                                                              // -V
	public myVector V(myVector A, myVector B) {return new myVector((A.x+B.x)/2.0,(A.y+B.y)/2.0,(A.z+B.z)/2.0); }                      // (A+B)/2
	public myVector V(myVector A, float s, myVector B) {return new myVector(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };      // (1-s)A+sB
	public myVector V(myVector A, myVector B, myVector C) {return new myVector((A.x+B.x+C.x)/3.0,(A.y+B.y+C.y)/3.0,(A.z+B.z+C.z)/3.0); };  // (A+B+C)/3
	public myVector V(myVector A, myVector B, myVector C, myVector D) {return V(V(A,B),V(C,D)); };                                         // (A+B+C+D)/4
	public myVector V(double s, myVector A) {return new myVector(s*A.x,s*A.y,s*A.z); };                                           // sA
	public myVector V(double a, myVector A, double b, myVector B) {return A(V(a,A),V(b,B));}                                       // aA+bB 
	public myVector V(double a, myVector A, double b, myVector B, double c, myVector C) {return A(V(a,A,b,B),V(c,C));}                   // aA+bB+cC
	public myVector V(myPoint P, myPoint Q) {return new myVector(P,Q);};                                          // PQ
	public myVector N(myVector U, myVector V) {return V( U.y*V.z-U.z*V.y, U.z*V.x-U.x*V.z, U.x*V.y-U.y*V.x); };                  // UxV cross product (normal to both)
	public myVector N(myPoint A, myPoint B, myPoint C) {return N(V(A,B),V(A,C)); };                                                   // normal to triangle (A,B,C), not normalized (proportional to area)
	public myVector B(myVector U, myVector V) {return U(N(N(U,V),U)); }        
	
	
	public double d(myVector U, myVector V) {return U.x*V.x+U.y*V.y+U.z*V.z; };                                            //U*V dot product
	public double dot(myVector U, myVector V) {return U.x*V.x+U.y*V.y+U.z*V.z; };                                            //U*V dot product
	public double det2(myVector U, myVector V) {return -U.y*V.x+U.x*V.y; };                                       		// U|V det product
	public double det3(myVector U, myVector V) {double dist = d(U,V); return Math.sqrt(d(U,U)*d(V,V) - (dist*dist)); };                                // U|V det product
	public double m(myVector U, myVector V, myVector W) {return d(U,N(V,W)); };                                                 // (UxV)*W  mixed product, determinant - measures 6x the volume of the parallelapiped formed by myVectortors
	public double m(myPoint E, myPoint A, myPoint B, myPoint C) {return m(V(E,A),V(E,B),V(E,C));}                                    // det (EA EB EC) is >0 when E sees (A,B,C) clockwise
	public double n2(myVector V) {return (V.x*V.x)+(V.y*V.y)+(V.z*V.z);};                                                   // V*V    norm squared
	public double n(myVector V) {return  Math.sqrt(n2(V));};                                                                // ||V||  norm
	public double d(myPoint P, myPoint Q) {return  myPoint._dist(P, Q); };                            // ||AB|| distance
	public double area(myPoint A, myPoint B, myPoint C) {return n(N(A,B,C))/2; };                                               // area of triangle 
	public double volume(myPoint A, myPoint B, myPoint C, myPoint D) {return m(V(A,B),V(A,C),V(A,D))/6; };                           // volume of tet 
	public boolean parallel (myVector U, myVector V) {return n(N(U,V))<n(U)*n(V)*0.00001; }                              // true if U and V are almost parallel
	public double angle(myPoint A, myPoint B, myPoint C){return angle(V(A,B),V(A,C));}												//angle between AB and AC
	public double angle(myPoint A, myPoint B, myPoint C, myPoint D){return angle(U(A,B),U(C,D));}							//angle between AB and CD
	public double angle(myVector U, myVector V){double angle = Math.atan2(n(N(U,V)),d(U,V)),sign = m(U,V,V(0,0,1));if(sign<0){    angle=-angle;}	return angle;}
	public boolean cw(myVector U, myVector V, myVector W) {return m(U,V,W)>0; };                                               // (UxV)*W>0  U,V,W are clockwise
	public boolean cw(myPoint A, myPoint B, myPoint C, myPoint D) {return volume(A,B,C,D)>0; };                                     // tet is oriented so that A sees B, C, D clockwise 
	public boolean projectsBetween(myPoint P, myPoint A, myPoint B) {return dot(V(A,P),V(A,B))>0 && dot(V(B,P),V(B,A))>0 ; };
	public double distToLine(myPoint P, myPoint A, myPoint B) {double res = det3(U(A,B),V(A,P)); return Double.isNaN(res) ? 0 : res; };		//MAY RETURN NAN IF point P is on line
	public myPoint projectionOnLine(myPoint P, myPoint A, myPoint B) {return P(A,dot(V(A,B),V(A,P))/dot(V(A,B),V(A,B)),V(A,B));}
	public boolean isSame(myPoint A, myPoint B) {return (A.x==B.x)&&(A.y==B.y)&&(A.z==B.z) ;}                                         // A==B
	public boolean isSame(myPoint A, myPoint B, double e) {return ((Math.abs(A.x-B.x)<e)&&(Math.abs(A.y-B.y)<e)&&(Math.abs(A.z-B.z)<e));}                   // ||A-B||<e
	
	public myVector W(double s,myVector V) {return V(s*V.x,s*V.y,s*V.z);}                                                      // sV
	
	public myVector U(myVector v){myVector u = new myVector(v); return u._normalize(); }
	public myVector U(myVector v, float d, myVector u){myVector r = new myVector(v,d,u); return r._normalize(); }
	public myVector Upt(myPoint v){myVector u = new myVector(v); return u._normalize(); }
	public myVector U(myPoint a, myPoint b){myVector u = new myVector(a,b); return u._normalize(); }
	public myVector U(double x, double y, double z) {myVector u = new myVector(x,y,z); return u._normalize();}
	
	public myVector normToPlane(myPoint A, myPoint B, myPoint C) {return myVector._cross(new myVector(A,B),new myVector(A,C)); };   // normal to triangle (A,B,C), not normalized (proportional to area)
	
	public void gl_normal(myVector V) {normal((float)V.x,(float)V.y,(float)V.z);}                                          // changes normal for smooth shading
	public void gl_vertex(myPoint P) {vertex((float)P.x,(float)P.y,(float)P.z);}                                           // vertex for shading or drawing
	public void gl_normal(myVectorf V) {normal(V.x,V.y,V.z);}                                          // changes normal for smooth shading
	public void gl_vertex(myPointf P) {vertex(P.x,P.y,P.z);}                                           // vertex for shading or drawing
	///show functions
	public void showVec( myPoint ctr, double len, myVector v){line(ctr.x,ctr.y,ctr.z,ctr.x+(v.x)*len,ctr.y+(v.y)*len,ctr.z+(v.z)*len);}
	public void show(myPoint P, double r,int fclr, int sclr, boolean flat) {//TODO make flat circles for points if flat
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		if(!flat){
			translate((float)P.x,(float)P.y,(float)P.z); 
			sphereDetail(5);
			sphere((float)r);
		} else {
			translate((float)P.x,(float)P.y,0); 
			this.circle(0,0,(float)r,(float)r);				
		}
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public void show(myPoint P, double rad, int fclr, int sclr, int tclr, String txt) {
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		sphereDetail(5);
		translate((float)P.x,(float)P.y,(float)P.z); 
		setColorValFill(tclr);setColorValStroke(tclr);
		showOffsetText(1.2f * (float)rad,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public void show(myPoint P, double r, int fclr, int sclr) {
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		sphereDetail(5);
		translate((float)P.x,(float)P.y,(float)P.z); 
		sphere((float)r); 
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public void show(myPoint P, double r){show(P,r, gui_Black, gui_Black, false);}
	public void show(myPoint P, String s) {text(s, (float)P.x, (float)P.y, (float)P.z); } // prints string s in 3D at P
	public void show(myPoint P, String s, myVector D) {text(s, (float)(P.x+D.x), (float)(P.y+D.y), (float)(P.z+D.z));  } // prints string s in 3D at P+D
	public void show(myPoint P, double r, String s, myVector D){show(P,r, gui_Black, gui_Black, false);pushStyle();setColorValFill(gui_Black);show(P,s,D);popStyle();}
	public void show(myPoint P, double r, String s, myVector D, int clr, boolean flat){show(P,r, clr, clr, flat);pushStyle();setColorValFill(clr);show(P,s,D);popStyle();}
	public void show(myPoint[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	public void show(myPoint[] ara, myVector norm) {beginShape();gl_normal(norm); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	
	public void showVec( myPointf ctr, float len, myVectorf v){line(ctr.x,ctr.y,ctr.z,ctr.x+(v.x)*len,ctr.y+(v.y)*len,ctr.z+(v.z)*len);}
	
	public void showOffsetText(float d, int tclr, String txt){
		setColorValFill(tclr);setColorValStroke(tclr);
		text(txt, d, d,d); 
	}
	
	public void show(myPointf P, float r,int fclr, int sclr, boolean flat) {//TODO make flat circles for points if flat
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		if(!flat){
			translate(P.x,P.y,P.z); 
			sphereDetail(5);
			sphere(r);
		} else {
			translate(P.x,P.y,0); 
			circle(0,0,r,r);				
		}
		popStyle(); popMatrix();
	} // render sphere of radius r and center P)		

//		public void showx(myPointf P, float rad, int fclr, int sclr, int tclr, String txt) {
//			pushMatrix(); pushStyle(); 
//			if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
//			sphereDetail(5);
//			translate(P.x,P.y,P.z);
//			sphere(rad); 
//			showOffsetText(1.2f * rad,tclr, txt);
//			popStyle(); popMatrix();} // render sphere of radius r and center P)

//		public void show(myPointf P, float rad, int[] fclr, int[] sclr, int tclr, String txt) {
//			pushMatrix(); pushStyle(); 
//			if((fclr!= null) && (sclr!= null)){setFill(fclr,255); setStroke(sclr,255);}
//			sphereDetail(5);
//			translate(P.x,P.y,P.z);
//			sphere(rad); 
//			showOffsetText(1.2f * rad,tclr, txt);
//			popStyle(); popMatrix();} // render sphere of radius r and center P)

	//inRect means draw inside rectangle
	public void show(myPointf P, float rad, int det, int[] fclr, int[] sclr, int tclr, String txt, boolean useBKGBox) {
		pushMatrix(); pushStyle(); 
		translate(P.x,P.y,P.z); 
		if(useBKGBox){
			fill(255,255,255,150);
			stroke(0,0,0,255);
			rect(0,6.0f,txt.length()*7.8f,-15);
			tclr = gui_Black;
		} 
		setFill(fclr,255); setStroke(sclr,255);			
		sphereDetail(det);
		sphere(rad); 
		showOffsetText(1.2f * rad,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	//show sphere of certain radius
	public void show(myPointf P, float rad, int det, int[] fclr, int[] sclr) {
		pushMatrix(); pushStyle(); 
		if((fclr!= null) && (sclr!= null)){setFill(fclr,255); setStroke(sclr,255);}
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	public void show(myPointf P, float rad, int det, int fclr, int sclr) {//only call with set fclr and sclr
		pushMatrix(); pushStyle(); 
		setColorValFill(fclr,255); 
		setColorValStroke(sclr,255);
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public void show(myPointf P, float rad, int det){			
		pushMatrix(); pushStyle(); 
		fill(0,0,0,255); 
		stroke(0,0,0,255);
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		popStyle(); popMatrix();
	}
	
	public void show(myPointf P, String s) {text(s, P.x, P.y, P.z); } // prints string s in 3D at P
	public void show(myPointf P, String s, myVectorf D) {text(s, (P.x+D.x), (P.y+D.y),(P.z+D.z));  } // prints string s in 3D at P+D
	public void show(myPointf P, float r, String s, myVectorf D){show(P,r, gui_Black, gui_Black, false);pushStyle();setColorValFill(gui_Black);show(P,s,D);popStyle();}
	public void show(myPointf P, float r, String s, myVectorf D, int clr, boolean flat){show(P,r, clr, clr, flat);pushStyle();setColorValFill(clr);show(P,s,D);popStyle();}
	public void show(myPointf[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	public void show(myPointf[] ara, myVectorf norm) {beginShape();gl_normal(norm); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	
	public void showNoClose(myPoint[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape();};                     
	public void showNoClose(myPointf[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape();};                     
	///end show functions
	
	public void curveVertex(myPoint P) {curveVertex((float)P.x,(float)P.y);};                                           // curveVertex for shading or drawing
	public void curve(myPoint[] ara) {if(ara.length == 0){return;}beginShape(); curveVertex(ara[0]);for(int i=0;i<ara.length;++i){curveVertex(ara[i]);} curveVertex(ara[ara.length-1]);endShape();};                      // volume of tet 	
	
	public boolean intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C, myPoint X) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		myVector EA=new myVector(E,A), AB=new myVector(A,B), AC=new myVector(A,C); 		double t = (float)(myVector._mixProd(EA,AC,AB) / myVector._mixProd(T,AC,AB));		X.set(myPoint._add(E,t,T));		return true;
	}	
	public myPoint intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		myVector EA=new myVector(E,A), AB=new myVector(A,B), AC=new myVector(A,C); 		
		double t = (float)(myVector._mixProd(EA,AC,AB) / myVector._mixProd(T,AC,AB));		
		return (myPoint._add(E,t,T));		
	}	
	// if ray from E along V intersects sphere at C with radius r, return t when intersection occurs
	public double intersectPt(myPoint E, myVector V, myPoint C, double r) { 
		myVector Vce = V(C,E);
		double CEdCE = Vce._dot(Vce), VdV = V._dot(V), VdVce = V._dot(Vce), b = 2 * VdVce, c = CEdCE - (r*r),
				radical = (b*b) - 4 *(VdV) * c;
		if(radical < 0) return -1;
		double t1 = (b + Math.sqrt(radical))/(2*VdV), t2 = (b - Math.sqrt(radical))/(2*VdV);			
		return ((t1 > 0) && (t2 > 0) ? Math.min(t1, t2) : ((t1 < 0 ) ? ((t2 < 0 ) ? -1 : t2) : t1) );
		
	}	
	
	public void rect(float[] a){rect(a[0],a[1],a[2],a[3]);}				//rectangle from array of floats : x, y, w, h
	

/////////////////////		
///color utils
/////////////////////
	//		public final int  // set more colors using Menu >  Tools > Color Selector
	//		  black=0xff000000, 
	//		  white=0xffFFFFFF,
	//		  red=0xffFF0000, 
	//		  green=0xff00FF00, 
	//		  blue=0xff0000FF, 
	//		  yellow=0xffFFFF00, 
	//		  cyan=0xff00FFFF, 
	//		  magenta=0xffFF00FF,
	//		  grey=0xff818181, 
	//		  orange=0xffFFA600, 
	//		  brown=0xffB46005, 
	//		  metal=0xffB5CCDE, 
	//		  dgreen=0xff157901;
	//set color based on passed point r= x, g = z, b=y
	public void fillAndShowLineByRBGPt(myPoint p, float x,  float y, float w, float h){
		fill((int)p.x,(int)p.y,(int)p.z);
		stroke((int)p.x,(int)p.y,(int)p.z);
		rect(x,y,w,h);
		//show(p,r,-1);
	}
	
	public myPoint WrldToScreen(myPoint wPt){return new myPoint(screenX((float)wPt.x,(float)wPt.y,(float)wPt.z),screenY((float)wPt.x,(float)wPt.y,(float)wPt.z),screenZ((float)wPt.x,(float)wPt.y,(float)wPt.z));}
	
	public int[][] triColors = new int[][] {
		{gui_DarkMagenta,gui_DarkBlue,gui_DarkGreen,gui_DarkCyan}, 
		{gui_LightMagenta,gui_LightBlue,gui_LightGreen,gui_TransCyan}};
		
	public void setFill(int[] clr){setFill(clr,clr[3]);}
	public void setStroke(int[] clr){setStroke(clr,clr[3]);}		
	public void setFill(int[] clr, int alpha){fill(clr[0],clr[1],clr[2], alpha);}
	public void setStroke(int[] clr, int alpha){stroke(clr[0],clr[1],clr[2], alpha);}
	public void setColorValFill(int colorVal){ setColorValFill(colorVal,255);}
	public void setColorValFill(int colorVal, int alpha){
		switch (colorVal){
			case gui_rnd				: { fill(random(255),random(255),random(255),alpha);break;}
	    	case gui_White  			: { fill(255,255,255,alpha);break; }
	    	case gui_Gray   			: { fill(120,120,120,alpha); break;}
	    	case gui_Yellow 			: { fill(255,255,0,alpha);break; }
	    	case gui_Cyan   			: { fill(0,255,255,alpha);  break; }
	    	case gui_Magenta			: { fill(255,0,255,alpha);break; }
	    	case gui_Red    			: { fill(255,0,0,alpha); break; }
	    	case gui_Blue				: { fill(0,0,255,alpha); break; }
	    	case gui_Green				: { fill(0,255,0,alpha);  break; } 
	    	case gui_DarkGray   		: { fill(80,80,80,alpha); break;}
	    	case gui_DarkRed    		: { fill(120,0,0,alpha);break;}
	    	case gui_DarkBlue   		: { fill(0,0,120,alpha); break;}
	    	case gui_DarkGreen  		: { fill(0,120,0,alpha); break;}
	    	case gui_DarkYellow 		: { fill(120,120,0,alpha); break;}
	    	case gui_DarkMagenta		: { fill(120,0,120,alpha); break;}
	    	case gui_DarkCyan   		: { fill(0,120,120,alpha); break;}	   
	    	case gui_LightGray   		: { fill(200,200,200,alpha); break;}
	    	case gui_LightRed    		: { fill(255,110,110,alpha); break;}
	    	case gui_LightBlue   		: { fill(110,110,255,alpha); break;}
	    	case gui_LightGreen  		: { fill(110,255,110,alpha); break;}
	    	case gui_LightYellow 		: { fill(255,255,110,alpha); break;}
	    	case gui_LightMagenta		: { fill(255,110,255,alpha); break;}
	    	case gui_LightCyan   		: { fill(110,255,255,alpha); break;}    	
	    	case gui_Black			 	: { fill(0,0,0,alpha);break;}//
	    	case gui_TransBlack  	 	: { fill(0x00010100);  break;}//	have to use hex so that alpha val is not lost    	
	    	case gui_FaintGray 		 	: { fill(77,77,77,alpha/3); break;}
	    	case gui_FaintRed 	 	 	: { fill(110,0,0,alpha/2);  break;}
	    	case gui_FaintBlue 	 	 	: { fill(0,0,110,alpha/2);  break;}
	    	case gui_FaintGreen 	 	: { fill(0,110,0,alpha/2);  break;}
	    	case gui_FaintYellow 	 	: { fill(110,110,0,alpha/2); break;}
	    	case gui_FaintCyan  	 	: { fill(0,110,110,alpha/2); break;}
	    	case gui_FaintMagenta  	 	: { fill(110,0,110,alpha/2); break;}
	    	case gui_TransGray 	 	 	: { fill(120,120,120,alpha/8); break;}//
	    	case gui_TransRed 	 	 	: { fill(255,0,0,alpha/2);  break;}
	    	case gui_TransBlue 	 	 	: { fill(0,0,255,alpha/2);  break;}
	    	case gui_TransGreen 	 	: { fill(0,255,0,alpha/2);  break;}
	    	case gui_TransYellow 	 	: { fill(255,255,0,alpha/2);break;}
	    	case gui_TransCyan  	 	: { fill(0,255,255,alpha/2);break;}
	    	case gui_TransMagenta  	 	: { fill(255,0,255,alpha/2);break;}
	    	case gui_OffWhite			: { fill(248,248,255,alpha);break; }
	    	default         			: { fill(255,255,255,alpha);break;}  	    	
		}//switch	
	}//setcolorValFill
	public void setColorValStroke(int colorVal){ setColorValStroke(colorVal, 255);}
	public void setColorValStroke(int colorVal, int alpha){
		switch (colorVal){
	    	case gui_White  	 	    : { stroke(255,255,255,alpha); break; }
	    	case gui_Gray   	 	    : { stroke(120,120,120,alpha); break;}
	    	case gui_Yellow      	    : { stroke(255,255,0,alpha); break; }
	    	case gui_Cyan   	 	    : { stroke(0,255,255,alpha); break; }
	    	case gui_Magenta	 	    : { stroke(255,0,255,alpha);  break; }
	    	case gui_Red    	 	    : { stroke(255,120,120,alpha); break; }
	    	case gui_Blue		 	    : { stroke(120,120,255,alpha); break; }
	    	case gui_Green		 	    : { stroke(120,255,120,alpha); break; }
	    	case gui_DarkGray    	    : { stroke(80,80,80,alpha); break; }
	    	case gui_DarkRed     	    : { stroke(120,0,0,alpha); break; }
	    	case gui_DarkBlue    	    : { stroke(0,0,120,alpha); break; }
	    	case gui_DarkGreen   	    : { stroke(0,120,0,alpha); break; }
	    	case gui_DarkYellow  	    : { stroke(120,120,0,alpha); break; }
	    	case gui_DarkMagenta 	    : { stroke(120,0,120,alpha); break; }
	    	case gui_DarkCyan    	    : { stroke(0,120,120,alpha); break; }	   
	    	case gui_LightGray   	    : { stroke(200,200,200,alpha); break;}
	    	case gui_LightRed    	    : { stroke(255,110,110,alpha); break;}
	    	case gui_LightBlue   	    : { stroke(110,110,255,alpha); break;}
	    	case gui_LightGreen  	    : { stroke(110,255,110,alpha); break;}
	    	case gui_LightYellow 	    : { stroke(255,255,110,alpha); break;}
	    	case gui_LightMagenta	    : { stroke(255,110,255,alpha); break;}
	    	case gui_LightCyan   		: { stroke(110,255,255,alpha); break;}		   
	    	case gui_Black				: { stroke(0,0,0,alpha); break;}
	    	case gui_TransBlack  		: { stroke(1,1,1,1); break;}	    	
	    	case gui_FaintGray 			: { stroke(120,120,120,250); break;}
	    	case gui_FaintRed 	 		: { stroke(110,0,0,alpha); break;}
	    	case gui_FaintBlue 	 		: { stroke(0,0,110,alpha); break;}
	    	case gui_FaintGreen 		: { stroke(0,110,0,alpha); break;}
	    	case gui_FaintYellow 		: { stroke(110,110,0,alpha); break;}
	    	case gui_FaintCyan  		: { stroke(0,110,110,alpha); break;}
	    	case gui_FaintMagenta  		: { stroke(110,0,110,alpha); break;}
	    	case gui_TransGray 	 		: { stroke(150,150,150,alpha/4); break;}
	    	case gui_TransRed 	 		: { stroke(255,0,0,alpha/2); break;}
	    	case gui_TransBlue 	 		: { stroke(0,0,255,alpha/2); break;}
	    	case gui_TransGreen 		: { stroke(0,255,0,alpha/2); break;}
	    	case gui_TransYellow 		: { stroke(255,255,0,alpha/2); break;}
	    	case gui_TransCyan  		: { stroke(0,255,255,alpha/2); break;}
	    	case gui_TransMagenta  		: { stroke(255,0,255,alpha/2); break;}
	    	case gui_OffWhite			: { stroke(248,248,255,alpha);break; }
	    	default         			: { stroke(55,55,255,alpha); break; }
		}//switch	
	}//setcolorValStroke	
	
	public void setColorValFillAmb(int colorVal){ setColorValFillAmb(colorVal,255);}
	public void setColorValFillAmb(int colorVal, int alpha){
		switch (colorVal){
			case gui_rnd				: { fill(random(255),random(255),random(255),alpha); ambient(120,120,120);break;}
	    	case gui_White  			: { fill(255,255,255,alpha); ambient(255,255,255); break; }
	    	case gui_Gray   			: { fill(120,120,120,alpha); ambient(120,120,120); break;}
	    	case gui_Yellow 			: { fill(255,255,0,alpha); ambient(255,255,0); break; }
	    	case gui_Cyan   			: { fill(0,255,255,alpha); ambient(0,255,alpha); break; }
	    	case gui_Magenta			: { fill(255,0,255,alpha); ambient(255,0,alpha); break; }
	    	case gui_Red    			: { fill(255,0,0,alpha); ambient(255,0,0); break; }
	    	case gui_Blue				: { fill(0,0,255,alpha); ambient(0,0,alpha); break; }
	    	case gui_Green				: { fill(0,255,0,alpha); ambient(0,255,0); break; } 
	    	case gui_DarkGray   		: { fill(80,80,80,alpha); ambient(80,80,80); break;}
	    	case gui_DarkRed    		: { fill(120,0,0,alpha); ambient(120,0,0); break;}
	    	case gui_DarkBlue   		: { fill(0,0,120,alpha); ambient(0,0,120); break;}
	    	case gui_DarkGreen  		: { fill(0,120,0,alpha); ambient(0,120,0); break;}
	    	case gui_DarkYellow 		: { fill(120,120,0,alpha); ambient(120,120,0); break;}
	    	case gui_DarkMagenta		: { fill(120,0,120,alpha); ambient(120,0,120); break;}
	    	case gui_DarkCyan   		: { fill(0,120,120,alpha); ambient(0,120,120); break;}		   
	    	case gui_LightGray   		: { fill(200,200,200,alpha); ambient(200,200,200); break;}
	    	case gui_LightRed    		: { fill(255,110,110,alpha); ambient(255,110,110); break;}
	    	case gui_LightBlue   		: { fill(110,110,255,alpha); ambient(110,110,alpha); break;}
	    	case gui_LightGreen  		: { fill(110,255,110,alpha); ambient(110,255,110); break;}
	    	case gui_LightYellow 		: { fill(255,255,110,alpha); ambient(255,255,110); break;}
	    	case gui_LightMagenta		: { fill(255,110,255,alpha); ambient(255,110,alpha); break;}
	    	case gui_LightCyan   		: { fill(110,255,255,alpha); ambient(110,255,alpha); break;}	    	
	    	case gui_Black			 	: { fill(0,0,0,alpha); ambient(0,0,0); break;}//
	    	case gui_TransBlack  	 	: { fill(0x00010100); ambient(0,0,0); break;}//	have to use hex so that alpha val is not lost    	
	    	case gui_FaintGray 		 	: { fill(77,77,77,alpha/3); ambient(77,77,77); break;}//
	    	case gui_FaintRed 	 	 	: { fill(110,0,0,alpha/2); ambient(110,0,0); break;}//
	    	case gui_FaintBlue 	 	 	: { fill(0,0,110,alpha/2); ambient(0,0,110); break;}//
	    	case gui_FaintGreen 	 	: { fill(0,110,0,alpha/2); ambient(0,110,0); break;}//
	    	case gui_FaintYellow 	 	: { fill(110,110,0,alpha/2); ambient(110,110,0); break;}//
	    	case gui_FaintCyan  	 	: { fill(0,110,110,alpha/2); ambient(0,110,110); break;}//
	    	case gui_FaintMagenta  	 	: { fill(110,0,110,alpha/2); ambient(110,0,110); break;}//
	    	case gui_TransGray 	 	 	: { fill(120,120,120,alpha/8); ambient(120,120,120); break;}//
	    	case gui_TransRed 	 	 	: { fill(255,0,0,alpha/2); ambient(255,0,0); break;}//
	    	case gui_TransBlue 	 	 	: { fill(0,0,255,alpha/2); ambient(0,0,alpha); break;}//
	    	case gui_TransGreen 	 	: { fill(0,255,0,alpha/2); ambient(0,255,0); break;}//
	    	case gui_TransYellow 	 	: { fill(255,255,0,alpha/2); ambient(255,255,0); break;}//
	    	case gui_TransCyan  	 	: { fill(0,255,255,alpha/2); ambient(0,255,alpha); break;}//
	    	case gui_TransMagenta  	 	: { fill(255,0,255,alpha/2); ambient(255,0,alpha); break;}//   	
	    	case gui_OffWhite			: { fill(248,248,255,alpha);ambient(248,248,255); break; }
	    	default         			: { fill(255,255,255,alpha); ambient(255,255,alpha); break; }	    
	    	
		}//switch	
	}//setcolorValFill

	//returns one of 30 predefined colors as an array (to support alpha)
	public int[] getClr(int colorVal){		return getClr(colorVal, 255);	}//getClr
	public int[] getClr(int colorVal, int alpha){
		switch (colorVal){
			case gui_Gray   		         : { return new int[] {120,120,120,alpha}; }
			case gui_White  		         : { return new int[] {255,255,255,alpha}; }
			case gui_Yellow 		         : { return new int[] {255,255,0,alpha}; }
			case gui_Cyan   		         : { return new int[] {0,255,255,alpha};} 
			case gui_Magenta		         : { return new int[] {255,0,255,alpha};}  
			case gui_Red    		         : { return new int[] {255,0,0,alpha};} 
			case gui_Blue			         : { return new int[] {0,0,255,alpha};}
			case gui_Green			         : { return new int[] {0,255,0,alpha};}  
			case gui_DarkGray   	         : { return new int[] {80,80,80,alpha};}
			case gui_DarkRed    	         : { return new int[] {120,0,0,alpha};}
			case gui_DarkBlue  	 	         : { return new int[] {0,0,120,alpha};}
			case gui_DarkGreen  	         : { return new int[] {0,120,0,alpha};}
			case gui_DarkYellow 	         : { return new int[] {120,120,0,alpha};}
			case gui_DarkMagenta	         : { return new int[] {120,0,120,alpha};}
			case gui_DarkCyan   	         : { return new int[] {0,120,120,alpha};}	   
			case gui_LightGray   	         : { return new int[] {200,200,200,alpha};}
			case gui_LightRed    	         : { return new int[] {255,110,110,alpha};}
			case gui_LightBlue   	         : { return new int[] {110,110,255,alpha};}
			case gui_LightGreen  	         : { return new int[] {110,255,110,alpha};}
			case gui_LightYellow 	         : { return new int[] {255,255,110,alpha};}
			case gui_LightMagenta	         : { return new int[] {255,110,255,alpha};}
			case gui_LightCyan   	         : { return new int[] {110,255,255,alpha};}
			case gui_Black			         : { return new int[] {0,0,0,alpha};}
			case gui_FaintGray 		         : { return new int[] {110,110,110,alpha};}
			case gui_FaintRed 	 	         : { return new int[] {110,0,0,alpha};}
			case gui_FaintBlue 	 	         : { return new int[] {0,0,110,alpha};}
			case gui_FaintGreen 	         : { return new int[] {0,110,0,alpha};}
			case gui_FaintYellow 	         : { return new int[] {110,110,0,alpha};}
			case gui_FaintCyan  	         : { return new int[] {0,110,110,alpha};}
			case gui_FaintMagenta  	         : { return new int[] {110,0,110,alpha};}    	
			case gui_TransBlack  	         : { return new int[] {1,1,1,alpha/2};}  	
			case gui_TransGray  	         : { return new int[] {110,110,110,alpha/2};}
			case gui_TransLtGray  	         : { return new int[] {180,180,180,alpha/2};}
			case gui_TransRed  	         	 : { return new int[] {110,0,0,alpha/2};}
			case gui_TransBlue  	         : { return new int[] {0,0,110,alpha/2};}
			case gui_TransGreen  	         : { return new int[] {0,110,0,alpha/2};}
			case gui_TransYellow  	         : { return new int[] {110,110,0,alpha/2};}
			case gui_TransCyan  	         : { return new int[] {0,110,110,alpha/2};}
			case gui_TransMagenta  	         : { return new int[] {110,0,110,alpha/2};}	
			case gui_TransWhite  	         : { return new int[] {220,220,220,alpha/2};}	
			case gui_OffWhite				 : { return new int[] {255,255,235,alpha};}
			default         		         : { return new int[] {255,255,255,alpha};}    
		}//switch
	}//getClr
	
	public int getRndClrInt(){return (int)random(0,23);}		//return a random color flag value from below
	public int[] getRndClr(int alpha){return new int[]{(int)random(0,255),(int)random(0,255),(int)random(0,255),alpha};	}
	public int[] getRndClr(){return getRndClr(255);	}		
	public Integer[] getClrMorph(int a, int b, double t){return getClrMorph(getClr(a), getClr(b), t);}    
	public Integer[] getClrMorph(int[] a, int[] b, double t){
		if(t==0){return new Integer[]{a[0],a[1],a[2],a[3]};} else if(t==1){return new Integer[]{b[0],b[1],b[2],b[3]};}
		return new Integer[]{(int)(((1.0f-t)*a[0])+t*b[0]),(int)(((1.0f-t)*a[1])+t*b[1]),(int)(((1.0f-t)*a[2])+t*b[2]),(int)(((1.0f-t)*a[3])+t*b[3])};
	}

	//used to generate random color
	public static final int gui_rnd = -1;
	//color indexes
	public static final int gui_Black 	= 0;
	public static final int gui_White 	= 1;	
	public static final int gui_Gray 	= 2;
	
	public static final int gui_Red 	= 3;
	public static final int gui_Blue 	= 4;
	public static final int gui_Green 	= 5;
	public static final int gui_Yellow 	= 6;
	public static final int gui_Cyan 	= 7;
	public static final int gui_Magenta = 8;
	
	public static final int gui_LightRed = 9;
	public static final int gui_LightBlue = 10;
	public static final int gui_LightGreen = 11;
	public static final int gui_LightYellow = 12;
	public static final int gui_LightCyan = 13;
	public static final int gui_LightMagenta = 14;
	public static final int gui_LightGray = 15;

	public static final int gui_DarkCyan = 16;
	public static final int gui_DarkYellow = 17;
	public static final int gui_DarkGreen = 18;
	public static final int gui_DarkBlue = 19;
	public static final int gui_DarkRed = 20;
	public static final int gui_DarkGray = 21;
	public static final int gui_DarkMagenta = 22;
	
	public static final int gui_FaintGray = 23;
	public static final int gui_FaintRed = 24;
	public static final int gui_FaintBlue = 25;
	public static final int gui_FaintGreen = 26;
	public static final int gui_FaintYellow = 27;
	public static final int gui_FaintCyan = 28;
	public static final int gui_FaintMagenta = 29;
	
	public static final int gui_TransBlack = 30;
	public static final int gui_TransGray = 31;
	public static final int gui_TransMagenta = 32;	
	public static final int gui_TransLtGray = 33;
	public static final int gui_TransRed = 34;
	public static final int gui_TransBlue = 35;
	public static final int gui_TransGreen = 36;
	public static final int gui_TransYellow = 37;
	public static final int gui_TransCyan = 38;	
	public static final int gui_TransWhite = 39;	
	public static final int gui_OffWhite = 40;
}

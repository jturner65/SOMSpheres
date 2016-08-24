package SOM_Sphere_PKG;

public class mySOMAnimResWin extends myDispWindow {
	
	public SOMMapData SOMSpheres_Data;						//map built from the spheres data
	//ui vars
	public final static int
		gIDX_NumSpheres 	= 0,
		gIDX_NumSamples 	= 1,
		gIDX_MinRadius		= 2,
		gIDX_MaxRadius		= 3,
		gIDX_SelDispSphere	= 4;			//ID of a sphere to be selected and highlighted

	public final int numGUIObjs = 5;											//# of gui objects for ui
	
	//to handle real-time update of locations of spheres
	public myVector curMseLookVec;  //pa.c.getMse2DtoMse3DinWorld()
	public myPoint curMseLoc3D;		//pa.c.getMseLoc(pa.sceneCtrVals[pa.sceneIDX])

	//private child-class flags
	public static final int 
			debugAnimIDX = 0,						//debug
			sphereDataLoadedIDX = 1,				//all SOM spheres have been loaded
			showSamplePntsIDX 	= 2,				//show spheres by polys or by sampled surface points
			saveSphereDataIDX 	= 3,				//save sphere locations as training data on next draw cycle
			currSphrDatSavedIDX = 4,				//current sphere data has been saved
			useSphrLocAsClrIDX	= 5,				//should use sphere's location as both its and its samples' color
			useSmplLocAsClrIDX  = 6,				//use all locations of samples as their colors, instead of sphere ctr's location
			showSphereIdIDX		= 7,				//display the sphere's ID as a text tag
			showSelSphereIDX	= 8,				//highlight the sphere with the selected idx
			useSmplsForTrainIDX = 9;				//use surface samples, or sphere centers, for training data
	
	public static final int numPrivFlags = 10;
	
	//represented random spheres
	public mySOMSphere[] spheres;
	public int numSpheres = 200, numSmplPoints = 200, curSelSphereIDX = 0;
	public float minSphRad = 5, maxSphRad = 20;
	
	public dataPoint[] sphereTrainData, sphereTestData, 
					sphereCtrData, sphereSmplData;
	
	public mySOMAnimResWin(SOM_SphereMain _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed,String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;
		//initUIClickCoords(rectDim[0] + .1 * rectDim[2],stY,rectDim[0] + rectDim[2],stY + yOff);
		trajFillClrCnst = SOM_SphereMain.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = SOM_SphereMain.gui_Cyan;
		super.initThisWin(_canDrawTraj, true);
	}
	
	//save data in appropriate formats for spheres and sphere sample points, to appropriately named files
	private void saveSphereInfo(){
		pa.th_exec.execute(new sphereWriter(pa, this));			
	}
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of privModFlgIdxs
				"Turn off sphere debugging", "Show Spheres","Hide Sphere Labels", "Using Sphere Loc As Color", "Using Sample Loc As Color", "Hide Selected Sphere", 
				"Use Sphere Samples For Train", "Saving Sphere Train/Test Data"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Turn on sphere debugging", "Show Sample Pts","Show Sphere Labels","Using Random Color for Sphere", "Using Sphere Color as Sample Color", "Highlight Selected Sphere", 
				"Use Sphere Centers For Train", "Save Sphere Train/Test Data"
		};
		privModFlgIdxs = new int[]{debugAnimIDX, showSamplePntsIDX,showSphereIdIDX, useSphrLocAsClrIDX, useSmplLocAsClrIDX, showSelSphereIDX,useSmplsForTrainIDX, saveSphereDataIDX};
		numClickBools = privModFlgIdxs.length;	
		initPrivBtnRects(0,numClickBools);
	}//initAllPrivBtns
	
	@Override
	protected void initMe() {	
		initUIBox();				//set up ui click region to be in sidebar menu below menu's entries		
		dispFlags[trajDecays] = true;								//this window responds to travelling reticle/playing
		curTrajAraIDX = 0;		
		initPrivFlags(numPrivFlags);		
		//pa.th_exec.execute(new sphereLoader(pa, this));		//fire and forget load of spheres - INITIAL MOVED TO MAIN SETUP
	}

	
	public void initAllSpheres(){
		pa.th_exec.execute(new sphereLoader(pa, this));		//fire and forget load of spheres	
	}
	
	@Override
	//set flag values and execute special functionality for this sequencer
	public void setPrivFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch(idx){
			case debugAnimIDX 			: {	break;}				
			case sphereDataLoadedIDX 	: {	break;}		//sphere data has been loaded				
			case showSamplePntsIDX 		: {	break;}		//show sphere as sample points or as sphere
			case saveSphereDataIDX 		: { break;}		//save all sphere centers, colors and IDs as training data/classes, and sample point locs, IDs and clrs as validation data
			case currSphrDatSavedIDX 	: 	{if(val){pa.outStr2Scr("Current Sphere data saved"); } break;}
			case showSphereIdIDX  		: { break;}//show labels for spheres
			case useSphrLocAsClrIDX 	: { break;}		//color of spheres is location or is random
			case useSmplLocAsClrIDX 	: { break;}		//color of samples is location or current sphere's color (either its location or random color)
			case showSelSphereIDX 		: { break;}
			case useSmplsForTrainIDX	: {break;}		//use surface samples for train and centers for test, or vice versa
		}		
	}//setPrivFlags		
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		//pa.outStr2Scr("setupGUIObjsAras in :"+ name);
		guiMinMaxModVals = new double [][]{
			{10,1000,10},										//# of spheres
			{10,1000,10},										//# of per-sphere samples
			{1,50,1},											//min radius of spheres
			{10,100,1},											//max radius of spheres
			{0,numSpheres-1,1}											//which sphere to select to highlight
		};														//min max mod values for each modifiable UI comp	
		
		guiStVals = new double[]{
			numSpheres,
			numSmplPoints,
			minSphRad,
			maxSphRad,
			curSelSphereIDX
		};								//starting value
		guiObjNames = new String[]{
			"# of spheres",
			"# of samples per sphere",
			"Min sphere radius",
			"Max sphere radius",
			"ID of sphere to select"
		};							//name/label of component		
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
		guiBoolVals = new boolean [][]{
			{true, false, true},
			{true, false, true},
			{false, false, true},			
			{false, false, true},
			{true, false, true}
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
	}
	@Override
	protected void setUIWinVals(int UIidx) {
		float val = (float)guiObjs[UIidx].getVal();
		int ival = (int)val;

		switch(UIidx){		
		case gIDX_NumSpheres : {
			if(ival != numSpheres){numSpheres = ival;guiObjs[gIDX_SelDispSphere].setNewMax(ival-1);setPrivFlags(sphereDataLoadedIDX,false);initAllSpheres();}
			break;}
		case gIDX_NumSamples : {
			if(ival != numSmplPoints){numSmplPoints = ival;setPrivFlags(sphereDataLoadedIDX,false);initAllSpheres();}
			break;}
		case gIDX_MinRadius : {
			if(val != minSphRad){
				minSphRad = val;
				if(minSphRad >= maxSphRad) { maxSphRad = minSphRad + 1;setWinToUIVals(gIDX_MaxRadius, maxSphRad);   }
				setPrivFlags(sphereDataLoadedIDX,false);initAllSpheres();}
			break;}
		case gIDX_MaxRadius	: {
			if(val != maxSphRad){
				maxSphRad = val;
				if(minSphRad >= maxSphRad)  { minSphRad = maxSphRad - 1;setWinToUIVals(gIDX_MinRadius, minSphRad);   }				
				setPrivFlags(sphereDataLoadedIDX,false);initAllSpheres();}
			break;}
		case gIDX_SelDispSphere :{
			if(ival != curSelSphereIDX){curSelSphereIDX = pa.min(ival, numSpheres-1);}//don't select a sphere Higher than the # of spheres
			break;}
		
		default : {break;}
		}
	}

	//if any ui values have a string behind them for display
	@Override
	protected String getUIListValStr(int UIidx, int validx) {
		switch(UIidx){
			default : {break;}
		}
		return "";
	}

	@Override
	public void initDrwnTrajIndiv(){}
	
	public void setLights(){
		pa.ambientLight(102, 102, 102);
		pa.lightSpecular(204, 204, 204);
		pa.directionalLight(111, 111, 111, 0, 1, -1);	
	}
	
	//overrides function in base class mseClkDisp
	@Override
	public void drawTraj3D(float animTimeMod,myPoint trans){
		
	}//drawTraj3D
	@Override
	protected void drawMe(float animTimeMod) {
		curMseLookVec = pa.c.getMse2DtoMse3DinWorld(pa.sceneCtrVals[pa.sceneIDX]);			//need to be here
		curMseLoc3D = pa.c.getMseLoc(pa.sceneCtrVals[pa.sceneIDX]);
		//pa.outStr2Scr("Current mouse loc in 3D : " + curMseLoc3D.toStrBrf() + "| scenectrvals : " + pa.sceneCtrVals[pa.sceneIDX].toStrBrf() +"| current look-at vector from mouse point : " + curMseLookVec.toStrBrf());
		pa.pushMatrix();pa.pushStyle();//nested shenannigans to get rid of if checks in each individual draw
		if(getPrivFlags(sphereDataLoadedIDX)){ 	
			if(getPrivFlags(useSphrLocAsClrIDX)){
				if(getPrivFlags(showSamplePntsIDX)){  //useSmplLocAsClrIDX
					if(getPrivFlags(useSmplLocAsClrIDX)){	for(mySOMSphere s : spheres){s.drawMeSmplsClrSmplLoc();}} 
					else{									for(mySOMSphere s : spheres){s.drawMeSmplsClrLoc();}}
				} else {					for(mySOMSphere s : spheres){s.drawMeClrLoc();}}
			} else {
				if(getPrivFlags(showSamplePntsIDX)){
					if(getPrivFlags(useSmplLocAsClrIDX)){	for(mySOMSphere s : spheres){s.drawMeSmplsClrSmplLoc();}} 
					else{									for(mySOMSphere s : spheres){s.drawMeSmplsClrRnd();}}
				} else {					for(mySOMSphere s : spheres){s.drawMeClrRnd();}}
			}
			//drawMeLabel()
			if(getPrivFlags(showSphereIdIDX)){for(mySOMSphere s : spheres){s.drawMeLabel();}	}
			if(getPrivFlags(showSelSphereIDX)){spheres[curSelSphereIDX].drawMeSelected(animTimeMod);     }
	}
		pa.popStyle();pa.popMatrix();
		if(getPrivFlags(saveSphereDataIDX)){saveSphereInfo();	setPrivFlags(saveSphereDataIDX, false);	}
	}
	
	@Override
	protected void playMe() {	}//only called 1 time
	@Override
	protected void stopMe() {	}	
	
	//debug function
	public void dbgFunc0(){	
	}	
	public void dbgFunc1(){	
	}	
	public void dbgFunc2(){	
	}	
	public void dbgFunc3(){	
	}	
	public void dbgFunc4(){	
	}	
	@Override
	public void clickDebug(int btnNum){
		pa.outStr2Scr("click debug in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	dbgFunc0();	break;}
			case 1 : {	dbgFunc1();	break;}
			case 2 : {	dbgFunc2();	break;}
			case 3 : {	dbgFunc3();	break;}
			default : {break;}
		}		
	}
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){	}
	@Override
	protected myPoint getMouseLoc3D(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){
		return false;
	}
	//alt key pressed handles trajectory
	//cntl key pressed handles unfocus of spherey
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {
		boolean res = checkUIButtons(mouseX, mouseY);
		if(res) {return res;}
//		//pa.outStr2Scr("sphere ui click in world : " + mseClckInWorld.toStrBrf());
//		if((!privFlags[sphereSelIDX]) && (curSelSphere!="")){			//set flags to fix sphere
//			res = true;
//			setPrivFlags(sphereSelIDX,true);			
//		} else if((privFlags[sphereSelIDX]) && (curSelSphere!="")){
//			if(pa.flags[pa.cntlKeyPressed]){			//cntl+click to deselect a sphere		
//				setPrivFlags(sphereSelIDX,false);
//				curSelSphere = ""; 
//				res = true;
//			} else {									//pass click through to selected sphere
//				res = sphereCntls.get(curSelSphere).hndlMouseClickIndiv(mouseX, mouseY, mseClckInWorld,curMseLookVec);				
//			}
//		}
		return res;
	}//hndlMouseClickIndiv

	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld) {
		boolean res = false;
		//pa.outStr2Scr("hndlMouseDragIndiv sphere ui drag in world mouseClickIn3D : " + mouseClickIn3D.toStrBrf() + " mseDragInWorld : " + mseDragInWorld.toStrBrf());
//		if((privFlags[sphereSelIDX]) && (curSelSphere!="")){//pass drag through to selected sphere
//			//pa.outStr2Scr("sphere ui drag in world mouseClickIn3D : " + mouseClickIn3D.toStrBrf() + " mseDragInWorld : " + mseDragInWorld.toStrBrf());
//			res = sphereCntls.get(curSelSphere).hndlMouseDragIndiv(mouseX, mouseY, pmouseX, pmouseY, mouseClickIn3D,curMseLookVec, mseDragInWorld);
//		}
		return res;
	}
	
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc) {}	

	@Override
	protected void hndlMouseRelIndiv() {}
	@Override
	protected void endShiftKeyI() {}
	@Override
	protected void endAltKeyI() {}
	@Override
	protected void endCntlKeyI() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}
	//resize drawn all trajectories
	@Override
	protected void resizeMe(float scale) {}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {}
}

package SOM_Sphere_PKG;


//window that accepts trajectory editing
public class mySOMMapUIWin extends myDispWindow {
	
	public SOMMapData SOM_Data;
	
	public static final int 
		buildSOMExe 			= 0,			//command to initiate SOM-building
		resetMapDefsIDX			= 1,			//reset default UI values for map
		mapDataLoadedIDX		= 2,			//whether map has been loaded or not	
		mapLoadFtrBMUsIDX 		= 3,			//whether or not to load the best matching units for each feature - this is a large construct so load only if necessary
		mapUseSclFtrDistIDX 	= 4,			//whether or not to use the scaled (0-1) ftrs or the unscaled features for distance measures
		mapUseChiSqDistIDX		= 5,			//whether or not to use chi-squared (weighted) distance for features
		mapSetSmFtrZeroIDX		= 6,			//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
		//display/interaction
		mapDrawTrainDatIDX		= 7,			//draw training examples
		mapDrawTrDatLblIDX		= 8,			//draw labels for training samples
		mapDrawMapNodesIDX		= 9,			//draw map nodes
		mapDrawAllMapNodesIDX	= 10,			//draw all map nodes, even empty
		mapShowLocClrIDX 		= 11;			//show img built of map with each pxl clr built from the 1st 3 features of the interpolated point at that pxl between the map nodes
	
	public static final int numPrivFlags = 12;
	
	//SOM map list options
	public String[] 
		uiMapShapeList = new String[] {"rectangular","hexagonal"},
		uiMapBndsList = new String[] {"planar","toroid"},
		uiMapKTypList = new String[] {"Dense CPU", "Dense GPU", "Sparse CPU"},
		uiMapNHoodList = new String[] {"gaussian","bubble"},
		uiMapRadClList = new String[] {"linear","exponential"},
		uiMapLrnClList = new String[] {"linear","exponential"};
			
	//	//GUI Objects	
	public final static int 
		uiMapRowsIDX 		= 0,            //map rows
		uiMapColsIDX		= 1,			//map cols
		uiMapEpochsIDX		= 2,			//# of training epochs
		uiMapShapeIDX		= 3,			//hexagonal or rectangular
		uiMapBndsIDX		= 4,			//planar or torroidal bounds
		uiMapKTypIDX		= 5,			//0 : dense cpu, 1 : dense gpu, 2 : sparse cpu.  dense needs appropriate lrn file format
		uiMapNHdFuncIDX		= 6,			//neighborhood : 0 : gaussian, 1 : bubble
		uiMapRadCoolIDX		= 7,			//radius cooling 0 : linear, 1 : exponential
		uiMapLrnCoolIDX		= 8,			//learning rate cooling 0 : linear 1 : exponential
		uiMapLrnStIDX		= 9,			//start learning rate
		uiMapLrnEndIDX		= 10,			//end learning rate
		uiMapRadStIDX		= 11,			//start radius
		uiMapRadEndIDX		= 12;			//end radius
		
	public final int numGUIObjs = 13;	
	
	private double[] uiVals;				//raw values from ui components
	
	public mySOMMapUIWin(SOM_SphereMain _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;		
		trajFillClrCnst = SOM_SphereMain.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = SOM_SphereMain.gui_Cyan;
		super.initThisWin(_canDrawTraj, false);
	}
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of flags
				"Building SOM", "Resetting Def Vals", "Loading Feature BMUs",
				"Using Scaled Ftrs For Dist Calc","Using ChiSq for Ftr Distance",
				"Unshared Ftrs are 0",	"Hide Train Data",
				"Hide Train Lbls",	"Hide Pop Map Nodes",	
				"Hide Map Nodes", "Showing Ftr Clr"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Build New Map ","Reset Def Vals","Not Loading Feature BMUs",
				"Using Unscaled Ftrs For Dist Calc","Not Using ChiSq Distance",
				"Ignoring Unshared Ftrs","Show Train Data",
				"Show Train Lbls",	"Show Pop Map Nodes",
				"Show Map Nodes", "Not Showing Ftr Clr"
		};
		privModFlgIdxs = new int[]{buildSOMExe, resetMapDefsIDX, mapLoadFtrBMUsIDX,mapUseSclFtrDistIDX,
				mapUseChiSqDistIDX,mapSetSmFtrZeroIDX,mapDrawTrainDatIDX,mapDrawTrDatLblIDX,mapDrawMapNodesIDX,mapDrawAllMapNodesIDX,mapShowLocClrIDX};
		numClickBools = privModFlgIdxs.length;	
		//maybe have call for 		initPrivBtnRects(0);	
		initPrivBtnRects(0,numClickBools);
	}//initAllPrivBtns

	protected void initMe() {
		initUIBox();				//set up ui click region to be in sidebar menu below menu's entries	
		float offset = 20;
		float width = rectDim[3]-(2*offset),//actually height, but want it square, and space is wider than high, so we use height as constraint - ends up being 834.8 x 834.8 with default screen dims
		xStart = rectDim[0] + .5f*(rectDim[2] - width);
		
		dispFlags[canDrawTraj] = true;			//to edit instrument qualities need to use drawn trajectories		
		//init specific sim flags
		initPrivFlags(numPrivFlags);			
		setPrivFlags(mapLoadFtrBMUsIDX,true);
		setPrivFlags(mapDrawTrainDatIDX,true);
		setPrivFlags(mapDrawMapNodesIDX,true);
		setPrivFlags(mapUseChiSqDistIDX,true);
		SOM_Data = new SOMMapData(pa, this, new float[]{xStart, rectDim[1] + offset, width, width});
	}//initMe
	
	@Override
	public void setPrivFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case buildSOMExe 			: {break;}			//placeholder	
			case resetMapDefsIDX		: {if(val){resetUIVals(); setPrivFlags(resetMapDefsIDX,false);}}
			case mapDataLoadedIDX 		: {break;}			//placeholder				
			case mapLoadFtrBMUsIDX 		: {//whether or not to load the best matching units for each feature - this is a large construct so load only if necessary
				break;}							
			case mapUseSclFtrDistIDX 	: {//whether or not to use the scaled (0-1) ftrs or the unscaled features for distance measures 
				//turn off chi sq flag if this is set
				if(val){setPrivFlags(mapUseChiSqDistIDX, false);}
				break;}							
			case mapUseChiSqDistIDX		: {//whether or not to use chi-squared (weighted) distance for features
				//turn off scaled ftrs if this is set
				if(val){setPrivFlags(mapUseSclFtrDistIDX, false);}
				break;}							
			case mapSetSmFtrZeroIDX		: {//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
				break;}							
			case mapDrawTrainDatIDX		: {//draw training examples
				break;}							
			case mapDrawTrDatLblIDX 	: {//draw labels for training samples                                                       
				break;}
			case mapDrawMapNodesIDX		: {//draw map nodes
				break;}							
			case mapDrawAllMapNodesIDX	: {//draw all map nodes, even empty
				break;}							
			case mapShowLocClrIDX		: {//draw all map nodes, even empty
				break;}							
		}
	}//setFlag	
	
	//first verify that new .lrn file exists, then
	//build new somoclu map using UI-entered values, then load resultant data
	protected void buildNewSOMMap(){
		pa.outStr2Scr("SOM_data.buildNewMap called");
		//verify sphere train/test data exists, otherwise save it
		if(!pa.curSphereDatSaved()){
			pa.outStr2Scr("Current Sphere Data not saved into test/lrn file, can't build SOM");
			setPrivFlags(buildSOMExe, false);
			return;
		}
		String lrnFileName = pa.getSphereLRNFileName(), 		//training data .lrn file
				testFileName = pa.getSphereTestFileName(),		//testing data .csv file
				minsFileName = pa.getSphereMinsFileName(),		//mins data percol .csv file
				diffsFileName = pa.getSphereDiffsFileName(),	//diffs data percol .csv file
				
				outFilePrfx = pa.getSphereOutFileBase() + "_x"+(int)this.guiObjs[uiMapColsIDX].getVal()+"_y"+(int)this.guiObjs[uiMapRowsIDX].getVal()+"_k"+(int)this.guiObjs[uiMapKTypIDX].getVal();
		//build new map and execute
		//somocluDat(SOM_SphereMain _p, int[] _mapInts, float[] _mapFloats, String[] _mapStrings, String _tdFN, String _outPfx)
		int[] intAra = new int[]{
				(int)this.guiObjs[uiMapColsIDX].getVal(), 
				(int)this.guiObjs[uiMapRowsIDX].getVal(),
				(int)this.guiObjs[uiMapEpochsIDX].getVal(),
				(int)this.guiObjs[uiMapKTypIDX].getVal(),
				(int)this.guiObjs[uiMapRadStIDX].getVal(),
				(int)this.guiObjs[uiMapRadEndIDX].getVal()};
		for(int i=0; i<intAra.length;++i){
			pa.outStr2Scr("intAra["+i+"] = "+intAra[i]);
		}
		//structure holding somoclu specific cmd line args and file names and such
		somocluDat SOMExecDat = new somocluDat(pa, 
				intAra,
				new float[]{
					(float)this.guiObjs[uiMapLrnStIDX].getVal(),
					(float)this.guiObjs[uiMapLrnEndIDX].getVal()
				}, 
				new String[]{
					getUIListValStr(uiMapShapeIDX, (int)this.guiObjs[uiMapShapeIDX].getVal()),	
					getUIListValStr(uiMapBndsIDX, (int)this.guiObjs[uiMapBndsIDX].getVal()),	
					getUIListValStr(uiMapRadCoolIDX, (int)this.guiObjs[uiMapRadCoolIDX].getVal()),	
					getUIListValStr(uiMapNHdFuncIDX, (int)this.guiObjs[uiMapNHdFuncIDX].getVal()),	
					getUIListValStr(uiMapLrnCoolIDX, (int)this.guiObjs[uiMapLrnCoolIDX].getVal())	
				}, lrnFileName,  outFilePrfx);
		pa.outStr2Scr("Som map descriptor : " + SOMExecDat + " exec str : ");
		pa.outStr2ScrAra(SOMExecDat.execString());
		//launch in a thread?
		SOM_Data.buildNewMap(SOMExecDat);
		//now load new map data and configure SOMMapData obj to hold all appropriate data
		//TODO need to specify class file name		
		SOM_Data.loadData("Not Used", diffsFileName, minsFileName, lrnFileName, outFilePrfx, outFilePrfx + "_outCSV", false);
		
		pa.outStr2Scr("SOM_data.buildNewMap complete");
		setPrivFlags(buildSOMExe, false);
	}//buildNewSOMMap	
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		//pa.outStr2Scr("setupGUIObjsAras in :"+ name);
//		if(numInstrs < 0){numInstrs = 0;}
		guiMinMaxModVals = new double [][]{  
			{1.0, 300.0, 10},			//uiMapRowsIDX 	 		
			{1.0, 300.0, 10},			//uiMapColsIDX	 		
			{1.0, 200.0, 10},			//uiMapEpochsIDX		
			{0.0, 1.0, 1},				//uiMapShapeIDX	 		
			{0.0, 1.0, 1},				//uiMapBndsIDX	 		
			{0.0, 2.0, .05},			//uiMapKTypIDX	 		
			{0.0, 1.0, 1},				//uiMapNHdFuncIDX		
			{0.0, 1.0, 1},				//uiMapRadCoolIDX		
			{0.0, 1.0, 1},				//uiMapLrnCoolIDX		
			{0.001, 1.0, 0.001},		//uiMapLrnStIDX	 		
			{0.001, 1.0, 0.001},		//uiMapLrnEndIDX		
			{2.0, 300.0, 1.0},			//uiMapRadStIDX	 	# nodes	
			{1.0, 10.0, 1.0}			//uiMapRadEndIDX		# nodes	
	
		};					
		guiStVals = new double[]{					
			40,		//uiMapRowsIDX 	 	
			40,		//uiMapColsIDX	 	
			10,		//uiMapEpochsIDX	
			0,		//uiMapShapeIDX	 	
			1,		//uiMapBndsIDX	 	
			0,		//uiMapKTypIDX	 	
			0,		//uiMapNHdFuncIDX	
			0,		//uiMapRadCoolIDX	
			0,		//uiMapLrnCoolIDX	
			0.1,	//uiMapLrnStIDX	 	
			0.01,	//uiMapLrnEndIDX	
			20.0,		//uiMapRadStIDX	 	
			1.0		//uiMapRadEndIDX
		};								//starting value
		uiVals = new double[numGUIObjs];//raw values
		System.arraycopy(guiStVals, 0, uiVals, 0, numGUIObjs);
		guiObjNames = new String[]{
				"# Map Rows",  			//uiMapRowsIDX 	 
				"# Map Columns",  		//uiMapColsIDX	 
				"# Training Epochs",  	//uiMapEpochsIDX
				"Map Node Shape",  		//uiMapShapeIDX	 
				"Map Boundaries",  		//uiMapBndsIDX	 
				"Dense/Sparse (C/G)PU", //uiMapKTypIDX	 
				"Neighborhood Func",  	//uiMapNHdFuncIDX
				"Radius Cooling", 		//uiMapRadCoolIDX
				"Learn rate Cooling",   //uiMapLrnCoolIDX
				"Start Learn Rate",  	//uiMapLrnStIDX	 
				"End Learn Rate",  		//uiMapLrnEndIDX
				"Start Cool Radius",  	//uiMapRadStIDX	 
				"End Cool Radius" 		//uiMapRadEndIDX				
		};			//name/label of component	
					
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows, 3 is object allows for lclick-up/rclick-down mod
		guiBoolVals = new boolean [][]{
			{true, false, true},      						//uiMapRowsIDX 	 	
			{true, false, true},      						//uiMapColsIDX	 	
			{true, false, true},      						//uiMapEpochsIDX	 	
			{true, true, true},      						//uiMapShapeIDX	 	
			{true, true, true},      						//uiMapBndsIDX	 	
			{true, true, true},      						//uiMapKTypIDX	 	
			{true, true, true},      						//uiMapNHdFuncIDX	
			{true, true, true},      						//uiMapRadCoolIDX	
			{true, true, true},      						//uiMapLrnCoolIDX	
			{false, false, true},      						//uiMapLrnStIDX	 	
			{false, false, true},      						//uiMapLrnEndIDX	 	
			{true, false, true},      						//uiMapRadStIDX	 	
			{true, false, true},      						//uiMapRadEndIDX	 	
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
	}
	public void resetUIVals(){
		for(int i=0; i<guiStVals.length;++i){	
			guiObjs[i].setVal(guiStVals[i]);
			//pa.outStr2Scr("i:"+i+" st obj val: " + guiStVals[i]);
		}
	}
	//if any ui values have a string behind them for display
	@Override
	public String getUIListValStr(int UIidx, int validx) {		
		//pa.outStr2Scr("UIidx : " + UIidx + "  Val : " + validx );
		switch(UIidx){//pa.score.staffs.size()
			case uiMapShapeIDX		: {return uiMapShapeList[validx % uiMapShapeList.length]; }
			case uiMapBndsIDX		: {return uiMapBndsList[validx % uiMapBndsList.length]; }
			case uiMapKTypIDX		: {return uiMapKTypList[validx % uiMapKTypList.length]; }
			case uiMapNHdFuncIDX	: {return uiMapNHoodList[validx % uiMapNHoodList.length]; }
			case uiMapRadCoolIDX	: {return uiMapRadClList[validx % uiMapRadClList.length]; }
			case uiMapLrnCoolIDX	: {return uiMapLrnClList[validx % uiMapLrnClList.length]; }	
		}
		return "";
	}
	@Override
	protected void setUIWinVals(int UIidx) {
		double val = guiObjs[UIidx].getVal();
		if(uiVals[UIidx] != val){uiVals[UIidx] = val;} else {return;}//set values in raw array and only proceed if values have changed
		//int intVal = (int)val;
		switch(UIidx){
			case uiMapRowsIDX 	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(val, guiObjs[uiMapColsIDX].getVal()));break;}
			case uiMapColsIDX	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(guiObjs[uiMapRowsIDX].getVal(), val));break;}
			case uiMapEpochsIDX	    : {break;}
			case uiMapShapeIDX	    : {break;}
			case uiMapBndsIDX	    : {break;}
			case uiMapKTypIDX	    : {break;}
			case uiMapNHdFuncIDX	: {break;}
			case uiMapRadCoolIDX	: {break;}
			case uiMapLrnCoolIDX	: {break;}
			case uiMapLrnStIDX	    : {
				if(val <= guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);}				
				break;}
			case uiMapLrnEndIDX	    : {
				if(val >= guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]);}				
				break;}
			case uiMapRadStIDX	    : {
				if(val <= guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);}
				break;}
			case uiMapRadEndIDX	    : {
				if(val >= guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]);}
				break;}
		}
	}
	
	@Override
	public void initDrwnTrajIndiv(){}
	@Override
	protected void playMe() {}	
	@Override
	protected void stopMe() {}	
	@Override
	protected void drawMe(float animTimeMod) {
		setPrivFlags(mapDataLoadedIDX,SOM_Data.isMapDrawable());
		if(getPrivFlags(mapDataLoadedIDX)){ SOM_Data.drawMap();}	
		if(getPrivFlags(buildSOMExe)){buildNewSOMMap();}
	}
	
	@Override
	public void clickDebug(int btnNum){
		pa.outStr2Scr("click debug in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {
				break;
			}
			case 1 : {
				break;
			}
			case 2 : {
				break;
			}
			case 3 : {
				break;
			}
			default : {break;}
		}		
	}	
	//handle mouseover 
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){
		boolean res = false;
		if(getPrivFlags(mapDataLoadedIDX)){ res = SOM_Data.chkMouseOvr(mouseX, mouseY);	}
		return res;
	}	
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {
		boolean mod = false;
		
		
		if(mod) {return mod;}
		else {return checkUIButtons(mouseX, mouseY);}
	}
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {
		boolean mod = false;
		
		return mod;
	}
	@Override
	protected void hndlMouseRelIndiv() {	}	
	@Override
	protected myPoint getMouseLoc3D(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}	
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc){}//not a snap-to window
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){		}
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
	protected void resizeMe(float scale) {
		
		//any resizing done
	}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {}
	@Override
	public String toString(){
		String res = super.toString();
		return res;
	}

}//myTrajEditWin

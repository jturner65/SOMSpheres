package SOM_Sphere_PKG;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import processing.core.PApplet;
import processing.core.PImage;

//this class holds the data describing a SOM and the data used to both build and query the som

public class SOMMapData {
	public SOM_SphereMain p;
	public mySOMMapUIWin win;				//owning window
	
	public SOMDatFileConfig fnames;			//struct maintaining file names for all files in som, along with 
	
	//description of somoclu exe params
	public somocluDat SOMExeDat;
	
	public TreeMap<Float,ArrayList<Tuple<Integer,Integer>>> MapSqMagToFtrs;		//precalculate the squared sum of all ftrs to facilitate lookup for locations of points on map
	
	//all nodes of som map, keyed by node location
	public TreeMap<Tuple<Integer,Integer>, SOMmapNode> MapNodes;
	//keyed by field used in lrn file (float rep of individual record, 
	public TreeMap<String, dataClass> TrainDataLabels;	
	
	public dataPoint[] trainData, inputData, testData;
	
	public float[] map_ftrsMean, map_ftrsVar, td_ftrsMean, td_ftrsVar, in_ftrsMean, in_ftrsVar; //per feature training and input data means and variances
	
	public HashSet<SOMmapNode> nodesWithEx, nodesWithNoEx;	//map nodes that have examples - for display only
	
	//features used to train map - these constructs are intended to hold the sorted list of weight ratios for each feature on all map nodes.  this can be very big (as big as the weights structure) so only load if necessary
	public SOMFeature[] featuresBMUs;
	public dataDesc dataHdr;			//describes data, set in weights read used in csv save file
	public int numFtrs, numTrainData, numInputData, numTestData;
	
	public Float[] diffsVals, minsVals;	//values to return scaled values to actual data points - multiply wts by diffsVals, add minsVals
	private int mapX =0, mapY =0;
	
	//public boolean saveMapImg;			//whether we should save the img of the map
	private PImage mapLocClrImg, mapRndClrImg;
	
	//public String somResBaseFName, trainDataName, diffsFileName, minsFileName;//files holding diffs and mins (.csv extension)
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 				= 0,
			dataLoadedIDX			= 1,			//data is cleanly loaded
			loaderRtnIDX			= 2,			//dataloader has finished - wait on this to draw map
			saveLocClrImgIDX 		= 3;			//whether or not to save the location-as-color image rep of the SOM	
	public static final int numFlags = 4;	
	
	public static String[] SOMResExtAra = new String[]{".wts",".fwts",".bm",".umx"};
	public static final int
		wtsIDX = 0,
		fwtsIDX = 1,
		bmuIDX = 2,
		umtxIDX = 3;	
	//headers for the csv files that are written by this object
//	private String TrainMCClassCSVHdr = "IDX,lrnFileKey,class_and_description";//,
				//	MCclassCsvHeader = "Subject_Clip,Subject_Classification, Motion_Classes";
	
	//draw/interaction variables
	private int[] dpFillClr, dpStkClr;
	private float[] mapDims;
	
	public dataPoint mseOvrData;//label of mouse-over location in map
	
	private myPoint ULCrnr;	//upper left corner of map square - use to orient any drawn trajectories
	
	public SOMMapData(SOM_SphereMain _p, mySOMMapUIWin _win, float[] _dims) {
		p=_p; win=_win;
		initFlags();
		dpFillClr = p.getClr(SOM_SphereMain.gui_White);
		dpStkClr = p.getClr(SOM_SphereMain.gui_Blue);	
		mapDims = _dims;	//rect mode corner
		mapLocClrImg = p.createImage((int)mapDims[2], (int)mapDims[3], p.RGB);
		mapRndClrImg = p.createImage((int)mapDims[2], (int)mapDims[3], p.RGB);
		ULCrnr = new myPoint(mapDims[0],mapDims[1],0);
		mseOvrData = null;
	}//ctor
	
	public void runMap(String[] cmdExecStr) throws IOException{
		boolean showDebug = getFlag(debugIDX);
		//http://stackoverflow.com/questions/10723346/why-should-avoid-using-runtime-exec-in-java		
		String wkDirStr = cmdExecStr[0], cmdStr = cmdExecStr[1], argsStr = "";
		String[] execStr = new String[cmdExecStr.length - 1];
		execStr[0] = wkDirStr + cmdStr;
		for(int i =2; i<cmdExecStr.length;++i){execStr[i-1] = cmdExecStr[i]; argsStr +=cmdExecStr[i]+" | ";}
		if(showDebug){p.outStr2Scr("wkDir : "+ wkDirStr + "\ncmdStr : " + cmdStr + "\nargs : "+argsStr);}
		ProcessBuilder pb = new ProcessBuilder(execStr);//.inheritIO();
		
		File wkDir = new File(wkDirStr); 
		pb.directory(wkDir);
		Process process=pb.start();
		if(showDebug){for(String s : pb.command()){p.outStr2Scr("cmd : " + s);}p.outStr2Scr(pb.directory().toString());}		
		BufferedReader rdr = new BufferedReader(new InputStreamReader(process.getInputStream())); 
		//put output into a string
		p.outStr2Scr("begin getting output");
		StringBuilder strbld = new StringBuilder();
		String s = null;
		while ((s = rdr.readLine()) != null){
			p.outStr2Scr(s);
			strbld.append(s);			
			strbld.append(System.getProperty("line.separator"));
		}
		String result = strbld.toString();//result of running map TODO save to log?
		p.outStr2Scr("runMap Finished");
	}
	//Build map from sphere data by aggregating all training data, building SOM exec string from UI input, and calling OS cmd to run somoclu
	public boolean buildNewMap(somocluDat _dat){
		SOMExeDat = _dat;				
		try {	runMap(SOMExeDat.execStrAra());	} 
		catch (IOException e){	p.outStr2Scr("Error running map : " + e.getMessage());	return false;}		
		return true;
	}//buildNewMap
	
	/**
	 * load data to represent map results
	 * @param cFN class file name
	 * @param diffsFN per-feature differences file name
	 * @param minsFN per-feature mins file name
	 * @param somTrainFN som training data file name - CMU data is csv
	 * @param somResFN
	 * @param _csvOutBaseFName file name prefix used to save class data in multiple formats to csv files
	 * @param isCMUData - whether we are loading the SOM for the cmu head data
	 */
	//(String _classFName, String _dFN, String _mnFN, String _trainDataFName, String _somResBaseFName, String _somCSVBaseFName){
	public void loadData(String cFN, String diffsFN, String minsFN, String somTrainFN, String somResFPrfx, String _csvOutBaseFName, boolean isCMUData){
		initData();			
		setFlag(loaderRtnIDX, false);
		fnames = new SOMDatFileConfig(p,this);
		fnames.setAllFileNames(cFN,diffsFN,minsFN, somTrainFN, somResFPrfx, _csvOutBaseFName);
		p.outStr2Scr("Current fnames before dataLoader Call : " + fnames.toString());
		p.th_exec.execute(new dataLoader(p,this,win.getPrivFlags(win.mapLoadFtrBMUsIDX),fnames,isCMUData));//fire and forget load task to load		
	}
	//load initial som data and results from cmu dataset
	public void setAndInitLoadCMUData(int idx, boolean loadCMU){
		initData();			//String trainDataFName, String somResBaseFName,
		if(loadCMU){
//			loadData(p.CMUclassFileName, p.mSOMSrcDiffsAra[idx],p.mSOMSrcMinsAra[idx],
//					p.mSOMSrcFileAra[idx], p.mSOMResFileAra[idx], p.MmntSOMSrcDir, true);
		}
	}//load initial file data
	
	public void initData(){
		trainData = new dataPoint[0];
		testData = new dataPoint[0];
		inputData = new dataPoint[0];
		nodesWithEx = new HashSet<SOMmapNode>();
		nodesWithNoEx = new HashSet<SOMmapNode>();
		numTrainData = 0;
		numFtrs = 0;
		numInputData = 0;
	}//initdata
	//return true if loader is done and if data is successfully loaded
	public boolean isMapDrawable(){return getFlag(loaderRtnIDX) && getFlag(dataLoadedIDX);}
	public void drawMap(){
		//draw map rectangle
		p.pushMatrix();p.pushStyle();
		p.noLights();
		if(win.getPrivFlags(win.mapShowLocClrIDX)){	p.image(mapLocClrImg,mapDims[0],mapDims[1]); if(getFlag(saveLocClrImgIDX)){mapLocClrImg.save(p.getSOMLocClrImgFName());  setFlag(saveLocClrImgIDX,false);}}
		else {										p.image(mapRndClrImg,mapDims[0],mapDims[1]);}
		p.lights();
		//p.setColorValFill(SOM_SphereMain.gui_OffWhite);
		//p.rect(mapDims);//TODO replace with a texture
		p.popStyle();p.popMatrix();
		
		p.pushMatrix();p.pushStyle();
		p.translate(mapDims[0],mapDims[1],0);
		//draw nodes
		p.pushMatrix();p.pushStyle();
		p.setFill(dpFillClr);p.setStroke(dpStkClr);
		if(this.mseOvrData != null){mseOvrData.drawMeLblMap();}
		if(win.getPrivFlags(win.mapDrawTrainDatIDX)){
			if(win.getPrivFlags(win.mapDrawTrDatLblIDX)){
				for(int i=0;i<trainData.length;++i){trainData[i].drawMeLblMap();}} 
			else {for(int i=0;i<trainData.length;++i){trainData[i].drawMeMap();}}}
		p.popStyle();p.popMatrix();
		//draw map nodes, either with or without empty nodes
		if(win.getPrivFlags(win.mapDrawAllMapNodesIDX)){		
			p.pushMatrix();p.pushStyle();
			p.setFill(dpFillClr);p.setStroke(dpStkClr);
			for(SOMmapNode node : MapNodes.values()){	node.drawMeSmallBk();	}
			p.popStyle();p.popMatrix();
		} 
		if(win.getPrivFlags(win.mapDrawMapNodesIDX)){
			p.pushMatrix();p.pushStyle();
			p.setFill(dpFillClr);p.setStroke(dpStkClr);
			for(SOMmapNode node : nodesWithEx){			node.drawMeMap();}
			p.popStyle();p.popMatrix();
		}
		p.popStyle();p.popMatrix();
	}//drawMap()	
	
	//get x and y locations relative to upper corner of map
	public float getSOMRelX (float x){return (x - mapDims[0]);}
	public float getSOMRelY (float y){return (y - mapDims[1]);}
	/**
	 * calc distance between two data points, using L2 calculation or standardized (divided by variance) euc
	 * @param a,b datapoints
	 * @param assumeZero if this is true then assume the smaller of two datapoints has a value of 0 for the missing features, otherwise only calculate distance between shared features
	 * 		(which assumes features share positions in arrays, and extra features are at tail of arrays)
	 * //passing variance (dataVar) for chi sq dist
	 * @return dist
	 */
	public float dpDistFunc(dataPoint a, dataPoint b, float[] dataVar){		
		if(win.getPrivFlags(win.mapUseSclFtrDistIDX)){
			if (a.numFtrs >= b.numFtrs){return calcPtDist(a.scFtrs, b.scFtrs);} else {return calcPtDist(b.scFtrs, a.scFtrs);}
		} else if(win.getPrivFlags(win.mapUseChiSqDistIDX)) {
			if (a.numFtrs >= b.numFtrs){return calcPtChiDist(a.ftrs, b.ftrs, dataVar);} else {return calcPtChiDist(b.ftrs, a.ftrs, dataVar);}
		} else {
			if (a.numFtrs >= b.numFtrs){return calcPtDist(a.ftrs, b.ftrs);} else {return calcPtDist(b.ftrs, a.ftrs);}
		}		
	}//distFunc	
	//passing variance for chi sq dist
	private float calcPtChiDist(float[] bigFtrs, float[] smFtrs, float[] dataVar){
		return PApplet.sqrt(calcSqPtChiDist(bigFtrs, smFtrs,dataVar));
	}//calcPtDist
	
	private float calcPtDist(float[] bigFtrs, float[] smFtrs){
		return PApplet.sqrt(calcSqPtDist(bigFtrs, smFtrs));
	}//calcPtDist
	//passing variance for chi sq dist
	private float calcSqPtChiDist(float[] bigFtrs, float[] smFtrs, float[] dataVar){
		float res = 0, diff;
		if(win.getPrivFlags(win.mapSetSmFtrZeroIDX)){	for(int i=(bigFtrs.length-1); i>= smFtrs.length;--i){res += bigFtrs[i] * bigFtrs[i];}}		
		for(int i =0; i<smFtrs.length; ++i){			diff = bigFtrs[i] - smFtrs[i];res +=  (diff * diff)/dataVar[i];}			
		return res;	
	}//calcPtDist
	
	private float calcSqPtDist(float[] bigFtrs, float[] smFtrs){
		float res = 0, diff;
		if(win.getPrivFlags(win.mapSetSmFtrZeroIDX)){	for(int i=(bigFtrs.length-1); i>= smFtrs.length;--i){res += bigFtrs[i] * bigFtrs[i];}}		
		for(int i =0; i<smFtrs.length; ++i){			diff = bigFtrs[i] - smFtrs[i];res += diff * diff;}			
		return res;	
	}//calcPtDist
	
	public float[] interpFloatAra(float[] a, float[] b, float t){
		if(a.length != b.length){p.pr("Error in interp float ara calc - sizes not equal."); return null;}
		float[] res = new float[a.length];
		for(int i=0;i<res.length;++i){res[i] = (a[i]*(1-t)) + (b[i]*t);}
		return res;		
	}
	
	public float[] interpFirstXFloatAraMult(float[] a, float[] b, float t, int x, float mult){
		if((a.length < x) || ( b.length < x)){p.pr("Error in interpFirstXFloatAra calc - arrays not big enough to return " + x + " values."); return null;}
		float[] res = new float[x];
		if (mult == 1.0){for(int i=0;i<res.length;++i){res[i] = (a[i]*(1-t)) + (b[i]*t);}} 
		else {			
			float mT = mult*t, m1t = mult * (1-t);
			for(int i=0;i<res.length;++i){res[i] = ((a[i]*m1t) + (b[i]*mT));}	
		}
		return res;		
	}
	
	//given pixel location relative to upper left corner of map, return map node
	public float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY){	return new float[]{mapX * mapPxlX/mapDims[2], mapY * mapPxlY/mapDims[3]};}	
	
	//check whether the mouse is over a legitimate map location
	public boolean chkMouseOvr(int mouseX, int mouseY){		
		float mapMseX = getSOMRelX(mouseX), mapMseY = getSOMRelY(mouseY);//, mapLocX = mapX * mapMseX/mapDims[2],mapLocY = mapY * mapMseY/mapDims[3] ;
		if((mapMseX >= 0) && (mapMseY >= 0) && (mapMseX < mapDims[2]) && (mapMseY < mapDims[3])){
			float[] mapNLoc=getMapNodeLocFromPxlLoc(mapMseX,mapMseY);
			//p.outStr2Scr("In Map : Mouse loc : " + mouseX + ","+mouseY+ "\tRel to upper corner ("+  mapMseX + ","+mapMseY +")" );
			mseOvrData = getDataPointAtLoc(mapNLoc[0], mapNLoc[1], new myPointf(mapMseX, mapMseY,0));
//			int[] tmp = getDataClrAtLoc(mapNLoc[0], mapNLoc[1]);
//			p.outStr2Scr("Color at mouse map loc :("+mapNLoc[0] + "," +mapNLoc[1]+") : " + tmp[0]+"|"+ tmp[1]+"|"+ tmp[2]);
			return true;
		} else {
			mseOvrData = null;
			return false;
		}
	}//chkMouseOvr
	
	//get datapoint at passed location in map coordinates (so should be in frame of map's upper right corner) - assume map is square and not hex
	private dataPoint getDataPointAtLoc(float x, float y, myPointf locPt){//, boolean useScFtrs){
		int xInt = PApplet.floor(x), yInt = PApplet.floor(y), xIntp1 = (xInt+1)%mapX, yIntp1 = (yInt+1)%mapY;		//assume torroidal map
		//need to divide by width/height of map * # cols/rows to get mapped to actual map nodes
		//p.outStr2Scr("In getDataPointAtLoc : Mouse loc in Nodes : " + x + ","+y+ "\txInt : "+ xInt + " yInt : " + yInt );
		float xInterp = x - xInt, yInterp = y - yInt;//, xIsq = xInterp*xInterp, yIsq = yInterp*yInterp,oneMxIsq = (1-xInterp)*(1-xInterp),oneMyIsq = (1-yInterp)*(1-yInterp);
		SOMmapNode LowXLowY = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)), LowXHiY= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)),
				 HiXLowY= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)),  HiXHiY= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1));
		float[] ftrs = (interpFloatAra(interpFloatAra (LowXLowY.ftrs, LowXHiY.ftrs, yInterp) , interpFloatAra (HiXLowY.ftrs, HiXHiY.ftrs,yInterp), xInterp));
		dataClass res = ((xInterp < .5 ) ? ((yInterp < .5) ? LowXLowY.getLabel() : LowXHiY.getLabel()) : ((yInterp < .5) ? HiXLowY.getLabel() : HiXHiY.getLabel()));
		
		dataPoint dp = buildDataPoint(ftrs,-1, false, false);
		dp.setCorrectScaling();
		dp.setLabel(res);
		dp.setMapLoc(locPt);		
		return dp;
	}//getClassAtLoc	
	//sets colors of background image of map
	public void setMapImgClrs(){ //mapRndClrImg
		float[] c;
		mapLocClrImg.loadPixels();
		//pixels[y*width+x] 
		for(int y = 0; y<mapLocClrImg.height; ++y){
			int yCol = y * mapLocClrImg.width;
			for(int x = 0; x < mapLocClrImg.width; ++x){
				c = getMapNodeLocFromPxlLoc(x, y);
				mapLocClrImg.pixels[x+yCol] = getDataClrAtLoc(c[0],c[1]);
			}
		}
		mapLocClrImg.updatePixels();		
		mapRndClrImg.loadPixels();
		//pixels[y*width+x] 
		for(int y = 0; y<mapRndClrImg.height; ++y){
			int yCol = y * mapRndClrImg.width;
			for(int x = 0; x < mapRndClrImg.width; ++x){
				c = getMapNodeLocFromPxlLoc(x, y);
				mapRndClrImg.pixels[x+yCol] = getNodeLblClrAtLoc(c[0],c[1]);
			}
		}
		mapRndClrImg.updatePixels();
	}//setMapImgClrs
	
	public boolean isToroidal(){
		if(null==SOMExeDat){return false;}
		return SOMExeDat.isToroidal();
	}
	
	
	//get pxl location in map that is closest to passed data point TODO
	public Tuple<Integer,Integer> getSmplLoc(dataPoint dp){
		//go through every map node, find closest nodes to datapoint, look around their neighborhoods
		
		Tuple<Integer,Integer> res = new Tuple<Integer,Integer>(0,0);
		return res;
	}//getSmplLoc
	
	//return a color for a location, where a color is an int array of the first 3 scaled features of the interpolated map nodes
	private int getDataClrAtLoc(float x, float y){
		int xInt = PApplet.floor(x), yInt = PApplet.floor(y), xIntp1 = (xInt+1)%mapX, yIntp1 = (yInt+1)%mapY;		//assume torroidal map
		//p.outStr2Scr("In getDataClrAtLoc : Mouse loc in Nodes : " + x + ","+y+ "\txInt : "+ xInt + " yInt : " + yInt );
		float xInterp = x - xInt, yInterp = y - yInt;		
		SOMmapNode LowXLowY = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)), LowXHiY= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)),
				 HiXLowY= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)),  HiXHiY= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1));
		float[] ftrs = interpFirstXFloatAraMult(interpFirstXFloatAraMult (LowXLowY.scFtrs, LowXHiY.scFtrs, yInterp,3, 1) , interpFirstXFloatAraMult (HiXLowY.scFtrs, HiXHiY.scFtrs,yInterp,3,1), xInterp,3,255);
		return (((int)ftrs[0] & 0xff) << 16) + (((int)ftrs[1] & 0xff) << 8) + ((int)ftrs[2] & 0xff);
	}
	//return a color for a location, where a color here is the color assigned to the training example closest to the location
	private int getNodeLblClrAtLoc(float xIn, float yIn){
		float x = (xIn - .5f), y = (yIn - .5f);
		int xInt = PApplet.floor(x)%mapX, yInt = PApplet.floor(y)%mapY, xIntp1 = (xInt+1)%mapX, yIntp1 = (yInt+1)%mapY;		//assume torroidal map
		//p.outStr2Scr("In getDataClrAtLoc : Mouse loc in Nodes : " + x + ","+y+ "\txInt : "+ xInt + " yInt : " + yInt );
		float xInterp = x - xInt, yInterp = y - yInt;//
//		dataClass res = ((xInterp < .5 ) ? (
//				(yInterp < .5) ? MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getLabel() : MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getLabel()) : 
//				((yInterp < .5) ? MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getLabel() : MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getLabel()));
		Tuple<Integer, Integer> keyTpl = ((xInterp < .5 ) ? (
				(yInterp < .5) ? new Tuple<Integer, Integer>(xInt,yInt): new Tuple<Integer, Integer>(xInt,yIntp1)) : 
				((yInterp < .5) ? new Tuple<Integer, Integer>(xIntp1,yInt) : new Tuple<Integer, Integer>(xIntp1,yIntp1)));
		SOMmapNode resNode = MapNodes.get(keyTpl);
		dataClass res = null;
		if(resNode == null){
			p.outStr2Scr("Null resnode in map at key : "+ keyTpl);}
		else {
			if(resNode.getExmplBMUSize() == 0){
				p.outStr2Scr("No best examples mapped to resnode in map at key : "+ keyTpl);						
			} else {
				res = resNode.getLabel();
				if(res == null){	p.outStr2Scr("Null label for resnode in map at key : "+ keyTpl);}
			}
		}
		//int[] clrVal = p.getClr(res.clrVal);
		if(res.clrVal == null){return (0xaa << 16) + (0xaa << 8) + 0xaa;}
		else {return ((res.clrVal[0] & 0xff) << 16) + ((res.clrVal[1] & 0xff) << 8) + (res.clrVal[2] & 0xff);}
	}
	//build a datapoint from passed features array
	//public dataPoint buildDataPoint(float[] _ftrsForPt, int idx){return buildDataPoint(_ftrsForPt,idx, false);}
	public dataPoint buildDataPoint(float[] _ftrsForPt, int idx, boolean isScaled, boolean mkClrAra){return new dataPoint(p,this, _ftrsForPt, isScaled, idx, isScaled, mkClrAra);	}//buildDataPt	
	
	//add a collected datapoint to this data
	public void addDataPoint(float[] _ftrsForPt, boolean _mkClrAra){
		ArrayList<dataPoint> tmpData = new ArrayList<dataPoint>(Arrays.asList(inputData));
		dataPoint dpt = buildDataPoint( _ftrsForPt,inputData.length, false, _mkClrAra);
		//TODO set label for this point
		tmpData.add(dpt);	
		if((null!=diffsVals) && (null!=minsVals)){dpt.setCorrectScaling();}
		inputData = tmpData.toArray(new dataPoint[0]);
		numInputData = inputData.length;
	}//buildData
	
	public myPoint buildScaledLoc(float x, float y){		
		float xLoc = (x + .5f) * (mapDims[2]/mapX), yLoc = (y + .5f) * (mapDims[3]/mapY);
		myPoint pt = new myPoint(xLoc, yLoc, 0);
		return pt;
	}
	
	public myPointf buildScaledLoc(Tuple<Integer,Integer> mapNodeLoc){		
		float xLoc = (mapNodeLoc.x + .5f) * (mapDims[2]/mapX), yLoc = (mapNodeLoc.y + .5f) * (mapDims[3]/mapY);
		myPointf pt = new myPointf(xLoc, yLoc, 0);
		return pt;
	}
	
	public int getMapX(){return mapX;}
	public int getMapY(){return mapY;}	
	public void setMapX(int _x){
		//need to update UI value in win
		mapX = _x;
		boolean didSet = win.setWinToUIVals(win.uiMapColsIDX, mapX);
		if(!didSet){p.outStr2Scr("Setting ui map x value failed for x = " + _x);}
	}
	public void setMapY(int _y){
		//need to update UI value in win
		mapY = _y;
		boolean didSet = win.setWinToUIVals(win.uiMapRowsIDX, mapY);
		if(!didSet){p.outStr2Scr("Setting ui map y value failed for y = " + _y);}
	}
	
	public float getMapWidth(){return mapDims[2];}
	public float getMapHeight(){return mapDims[3];}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}			
			case dataLoadedIDX	: {break;}		
			case loaderRtnIDX : {break;}
			case saveLocClrImgIDX : {break;}
		}
	}//setFlag	
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	public String toString(){
		String res = "Weights Data : \n";
		for(Tuple<Integer,Integer> key : MapNodes.keySet()){res+="Key:"+key.toString()+" : "+MapNodes.get(key).toCSVString()+"\n";}
		res += "Training Data : \n";
		for(int i =0; i<trainData.length;++i){ res += trainData[i].toString();}
		//TODO a lot of data is missing
		return res;	
	}	
}//SOMMapData

//class to hold the data that defines a somoclu map execution
class somocluDat{
	public SOM_SphereMain p;
	private String execDir;			//somoclu execution directory
	private int[] mapInts;			// mapCols (x), mapRows (y), mapEpochs, mapKType, mapStRad, mapEndRad;
	private float[] mapFloats;		// mapStLrnRate, mapEndLrnRate;
	private String[] mapStrings;		// mapGridShape, mapBounds, mapRadCool, mapNHood, mapLearnCool;	
	private String trainDataFN,		//training data file name 
			outFilesPrefix;			//output from map prefix
	
	public somocluDat(SOM_SphereMain _p, int[] _mapInts, float[] _mapFloats, String[] _mapStrings, String _tdFN, String _outPfx){
		p = _p;
		execDir = p.SOM_Dir;
		mapInts = _mapInts;
		mapFloats = _mapFloats;
		mapStrings = _mapStrings;
		trainDataFN = _tdFN;
		outFilesPrefix = _outPfx;
	}
	
	//build execution string for somoclu
	public String[] execStrAra(){
		String[] res = new String[]{execDir, "somoclu.exe",
				"-k",""+mapInts[3],"-x",""+mapInts[0],"-y",""+mapInts[1], "-e",""+mapInts[2],"-r",""+mapInts[4],"-R",""+mapInts[5],
				"-l",""+String.format("%.4f",mapFloats[0]),"-L",""+String.format("%.4f",mapFloats[1]), 
				"-m",""+mapStrings[1],"-g",""+mapStrings[0],"-n",""+mapStrings[3], "-T",""+mapStrings[4], 
				"-t",""+mapStrings[2], trainDataFN , outFilesPrefix};
		return res;		
	}//execString
	
	public boolean isToroidal(){return (mapStrings[0].equals("toroid"));}
	
	@Override
	public String toString(){
		String res = "Map config : Somoclu Dir : " + execDir +"\n";
		res += "Kernel(k) : "+mapInts[3] + "\t#Cols : " + mapInts[0] + "\t#Rows : " + mapInts[1] + "\t#Epochs : " + mapInts[2] + "\tStart Radius : " + mapInts[4] + "\tEnd Radius : " + mapInts[5]+"\n";
		res += "Start Learning Rate : " + String.format("%.4f",mapFloats[0])+"\tEnd Learning Rate : " + String.format("%.4f",mapFloats[1])+"\n";
		res += "Boundaries : "+mapStrings[1] + "\tGrid Shape : "+mapStrings[0]+"\tNeighborhood Function : " + mapStrings[3] + "\nLearning Cooling: " + mapStrings[4] + "\tRadius Cooling : "+ mapStrings[2]+"\n";		
		res += "Training data .lrn file : " + trainDataFN + "\nOutput files prefix : " + outFilesPrefix +"\n";
		return res;
	}
	
}//somocluDat

//class to describe a datapoint used by the map and also produced by the map upon queries
class dataPoint{
	public SOM_SphereMain p;
	public SOMMapData map;			//owning map
	public float[] ftrs,			//feature data for data point - not scaled
					scFtrs,			//scaled feature data to be 0-1 - based on min/max value of individual feature across all examples
					normFtrs;		//mag of ftr vec == 1
	public int seqNum, numFtrs;		//sequence of data point in list of data, # of features for this data point
	public String texID;		//this datapoint's unique identifier, used in 1st column of .lrn file's dense format (ignored by som).  "" denotes none; label is the classification of this data point
	public boolean wasBuiltScaled, makeClrAra;
	protected myPointf mapLoc;		//location in mapspace most closely matching this node - set to bmu map node location, needs to be actual map location
	public SOMmapNode bmu;			//reference to map node that best matches this node
	public dataClass label;
	
	public myPointf worldLoc;		//location in 3d world of point - first 3 values in ftr array, if available.  intended for use with spheres&samples proj, used to draw samples in 3D world
	public int[] locClrs;			//worldLoc as colors ara
	//draw-based vars
	private float rad;
	protected int drawDet;
		
	public dataPoint( SOM_SphereMain _p,SOMMapData _map,  float [] _ftrs, boolean _wasBuiltScaled, int _seq, boolean _skipFirstFtr, boolean _makeClrAra){
		map=_map;p=_p;
		seqNum = _seq;			
		wasBuiltScaled = _wasBuiltScaled;
		makeClrAra = _makeClrAra;
		//duplicate features now, will modify to be scaled/unscaled depending on data and whether mins&diffs exist for map
		label = null;
		if(_ftrs.length != 0){	
			setFtrsFromFloatAra(_ftrs,_skipFirstFtr );
		}	
		mapLoc = new myPointf();		
		if(label==null){label = new dataClass(map,seqNum,0,"Uninited label","Uninited Description", null);}
		bmu = null;
		setRad( 2.0f);
	}//ctor

	public static float minRad = 100000, maxRad = -100000;//for debugging purposes, gives min and max radii of spheres that will be displayed on map for each node proportional to # of samples
	protected void setRad(float _rad){
		rad = ((float)(Math.log(2.0f*(_rad+1))));
		minRad = minRad > rad ? rad : minRad;
		maxRad = maxRad < rad ? rad : maxRad;
		drawDet = ((int)(Math.log(2*(rad+1)))+1);
	}
	public float getRad(){return rad;}
	
	//	_skipFirstFtr is so that we can determine whether to use or ignore the first feature value in the ara
	//this is if reading a lrn-formatted file, where the first feature will be the label of the example
	public dataPoint(SOM_SphereMain _p, SOMMapData _map, String [] _tkns, boolean _isScaled, int _seq, boolean _skipFirstFtr, boolean _makeClrAra ){
		this(_p,_map, new float[0],_isScaled,_seq,_skipFirstFtr,_makeClrAra);
		if(_tkns.length != 0){	setFtrsFromStrAra(_tkns, _skipFirstFtr );	}
	}//ctor
	
	//return the subject/1st part of the per-example key (texID) as an integer => -1 means none
	public int getSubj(){
		if((null == label) || (texID == null) || (texID == "")){return -1;}
		return label.intKVal;
	}
	
	public void setLabel(dataClass _lbl){label=_lbl; texID = label.lrnKey;}
	public dataClass getLabel(){return label;}
	
	public void setFtrsFromFloatAra(float [] _ftrs,boolean _skipFirstFtr){
		int stIDX = _skipFirstFtr ? 1 : 0;
		numFtrs = _ftrs.length - stIDX;
		ftrs = new float[numFtrs];
		System.arraycopy(_ftrs, stIDX, ftrs, 0, numFtrs);	
		setFtrsEnd((_skipFirstFtr ? String.format("%3.3e", _ftrs[0]) : ""), stIDX,_skipFirstFtr);
	}
	public void setFtrsFromStrAra(String [] tkns,boolean _skipFirstFtr){
		int stIDX = _skipFirstFtr ? 1 : 0;
		numFtrs = tkns.length - stIDX;
		ftrs = new float[numFtrs];
		for(int i = stIDX; i < tkns.length; i++) {	ftrs[i-stIDX] = Float.parseFloat(tkns[i]);}
		setFtrsEnd((_skipFirstFtr ? dataClass.getPrfxFromData(tkns[0]) : ""),0,_skipFirstFtr);
	}
	
	private void setFtrsEnd(String _texID,int stIDX, boolean _skipFirstFtr){
		scFtrs = new float[numFtrs];	
		normFtrs = new float[numFtrs];
		//set them the same, then normalize scFtrs
		System.arraycopy(ftrs, stIDX, scFtrs, 0, numFtrs);	
		System.arraycopy(ftrs, stIDX, normFtrs, 0, numFtrs);	
		buildNormFtrs();
		texID = _texID;
		if(_skipFirstFtr && (null != map.TrainDataLabels.get(texID))){label = map.TrainDataLabels.get(texID);}
		if(!this.wasBuiltScaled){	worldLoc = new myPointf(ftrs[stIDX],ftrs[stIDX+1],ftrs[stIDX+2]);}		//onlycorrect if getting feature values are not scaled (i.e. coming from 3d cube)
		else {						worldLoc = new myPointf();}												//use normalized feature to build a world loc
		if(makeClrAra){locClrs = p.getClrFromCubeLoc(worldLoc.asArray());}				
	}

	//return an array of features multiplied by passed constant
	public float[] getMultFtrs(float n, boolean useScaled){
		float[] res = new float[numFtrs], src = (useScaled ? scFtrs : ftrs);
		for(int i =0; i<numFtrs;++i){res[i]=src[i]*n;}		
		return res;
	}
	
	public void setMapLoc(myPointf _pt){mapLoc = new myPointf(_pt);}
	public void setBMU(SOMmapNode _n, float[] dataVar){ 	
		bmu = _n;	
		mapLoc.set(_n.mapLoc);
		if(_n==null){
			p.pr("_n is null!");
		} else if(_n.mapLoc == null){
			p.pr("_n has no maploc!");
		}
		if(dataVar == null){
			p.pr("map variance not calculated : datavar is null!");
		}
		if(map == null){
			p.pr("Map is null!");
		}
		
		float dist = map.dpDistFunc(_n, this,dataVar);
		_n.addBMUExample(dist, this);
	}//setBMU
	//verify features expected to be normalized are normalized
	private void buildNormFtrs(){
		float sum = 0;
		for(int i=0;i<normFtrs.length;++i){	sum += normFtrs[i];}
		if(sum==0){return;}
		for(int i=0;i<normFtrs.length;++i){	normFtrs[i] /= sum;}		
	}
	//based on whether the features fed to this data point where scaled or not, calculate the other (eithe scaled or not scaled) data
	public void setCorrectScaling(){if(wasBuiltScaled){
		calcDeScaledFtrs();
		calcWorldLoc();
		if(makeClrAra){locClrs = p.getClrFromCubeLoc(worldLoc.asArray());}
	} else {calcScaledFtrs();}}	
	//modify scaled features to be scaled by owning map's constants (assumes ftrs are correctly raw)//subtract min values, divide by diff values
	private void calcScaledFtrs(){for(int i=0; i<scFtrs.length;++i){scFtrs[i] = ((ftrs[i] - map.minsVals[i])/map.diffsVals[i]);}}
	//modify regular features to be de-scaled by owning map's constants (assumes scaledFtrs are correctly scaled)
	private void calcDeScaledFtrs(){for(int i=0; i<scFtrs.length;++i){ftrs[i] = ((scFtrs[i] * map.diffsVals[i]) + map.minsVals[i]);}}

	private void calcWorldLoc(){
		float[] f=new float[3];
		for(int i=0; i<3;++i){	f[i] = ((scFtrs[i] * map.diffsVals[i]) + map.minsVals[i]);}
		worldLoc = new myVectorf(f[0],f[1],f[2]);
	}
	//if building data from spheres - pass in mins and diffs -> min coords and diff between min and max coord
	public void setCorrectScaling(float[] min, float[] diff){if(wasBuiltScaled){calcDeScaledFtrs(min,diff);} else {calcScaledFtrs(min,diff);}}	
	//modify scaled features to be scaled by owning map's constants (assumes ftrs are correctly raw)//subtract min values, divide by diff values
	private void calcScaledFtrs(float[] min, float[] diff){for(int i=0; i<scFtrs.length;++i){scFtrs[i] = ((ftrs[i] - min[i])/diff[i]);}}
	//modify regular features to be de-scaled by owning map's constants (assumes scaledFtrs are correctly scaled)
	private void calcDeScaledFtrs(float[] min, float[] diff){for(int i=0; i<scFtrs.length;++i){ftrs[i] = ((scFtrs[i] * diff[i]) + min[i]);}}
	
	//override drawing in map nodes
	public void drawMeMap(){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, label.clrVal,label.clrVal);
		p.popStyle();p.popMatrix();		
	}//drawMe
	
	public void drawMeLblMap(){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.show(mapLoc, rad, label.clrVal,label.clrVal, SOM_SphereMain.gui_DarkGreen, label.label);
		p.popStyle();p.popMatrix();		
	}//drawLabel
	
	//override drawing in map nodes
	public void drawMeMapClr(int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at mapLoc
		p.show(mapLoc, rad,drawDet, clr, clr);
		p.popStyle();p.popMatrix();		
	}//drawMe
	
	public void drawMeLblMapClr(int[] clr){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		//p.show(mapLoc, rad, label.clrVal,label.clrVal, SOM_SphereMain.gui_DarkGreen, label.label);
		p.show(mapLoc, rad, drawDet, clr, clr, SOM_SphereMain.gui_DarkGreen, label.label);
		p.popStyle();p.popMatrix();		
	}//drawLabel
	
//	//return location of unscaled features in 3D space
//	public float[] get3dLoc(){
//		
//	}
	
	public String toCSVString(){
		String res = ""+seqNum+",";
		for(int i=0;i<ftrs.length;++i){res += String.format("%1.7g", ftrs[i]) + ",";}
		return res;
	}
	
	public String toLRNString(String sep){
		String res = ""+seqNum+sep;
		for(int i=0;i<scFtrs.length;++i){res += String.format("%1.7g", scFtrs[i]) + sep;}
		return res;		
	}
	
	//return a comma-sep string of this obj's seqNum, texID (float-rep name of training example for .lrn dense file) and label
	public String getLabelInfo(){
		return "" + seqNum + "," + texID + ","+ (null == label ?  "Unknown Label" : label.getFullLabel());	
	}
	public String getLabelBriefInfo(){
		return "" + (null == label ?  "Unknown Label" : label.getFullLabel());	
	}	

	public String toString(){
		String res = "Example Seq# : "+seqNum+"\t" +  (  "" != texID ? "Dense format lrnID : " + texID + "\t" : "" ) + (null == label ?  "Unknown Label\t" : "Label : " + label.toString() +"\t");
		if(null!=mapLoc){res+="Location on SOM map : " + mapLoc.toStrBrf();;}
		if(null!=worldLoc){res+="Location in 3d : " + worldLoc.toStrBrf();}
		res += "\nUnscaled Features (" +numFtrs+ " ) :";
		if((ftrs==null) || (ftrs.length == 0)){res+="\tNone\n";} else {res +="\n\t";for(int i=0;i<ftrs.length;++i){res += String.format("%1.4g", ftrs[i]) + " | "; if((numFtrs > 40) && ((i+1)%20 == 0)){res +="\n\t";}}}
		res +="\nScaled Features : ";
		if((scFtrs==null) || (scFtrs.length == 0)){res+="\tNone\n";} else {res +="\n\t";for(int i=0;i<scFtrs.length;++i){res += String.format("%1.4g", scFtrs[i]) + " | "; if((numFtrs > 40) && ((i+1)%20 == 0)){res +="\n\t";}}}
		return res;
	}	
}//dataPoint

//only used to load map nodes - will always be scaled 0-1 in features 
class SOMmapNode extends dataPoint{
	public Tuple<Integer,Integer> mapNode;	
	public ArrayList<SOMFeature> ftrsBestInThisUnit;//idxs in features array
	private TreeMap<Float,dataPoint> examplesBMU;	//best training examples in this unit, keyed by distance
	private int numMappedTEx;						//# of mapped training examples to this node
	public SOMmapNode(SOM_SphereMain _p,SOMMapData _map, float[] _ftrs, int _seq, Tuple<Integer,Integer> _mapNode, boolean _skipFirstFtr, boolean _mkClrAra) {
		super(_p, _map,_ftrs, true, _seq, _skipFirstFtr,_mkClrAra);
		initMapNode( _mapNode);
	}
	public SOMmapNode(SOM_SphereMain _p,SOMMapData _map, String[] _strftrs, int _seq, Tuple<Integer,Integer> _mapNode, boolean _skipFirstFtr, boolean _mkClrAra) {
		super(_p,_map, _strftrs, true, _seq,_skipFirstFtr,_mkClrAra);
		initMapNode( _mapNode);
	}
	
	private void initMapNode(Tuple<Integer,Integer> _mapNode){
		mapNode = _mapNode;
		//p.pr("map node: "+ mapNode);
		mapLoc = map.buildScaledLoc(mapNode);
		bmu = this;		
		numMappedTEx = 0;
		examplesBMU = new TreeMap<Float,dataPoint>();		//defaults to small->large ordering	
		ftrsBestInThisUnit = new ArrayList<SOMFeature>();
	}
	
	public void addBMUExample(float dist, dataPoint dpt){
		examplesBMU.put(dist, dpt);		
		numMappedTEx = examplesBMU.size();
		setRad( 2*numMappedTEx);// PApplet.sqrt(numMappedTEx) + 1;
		label = examplesBMU.firstEntry().getValue().getLabel();
	}
	
	public boolean hasMappings(){return numMappedTEx != 0;}
	@Override
	public dataClass getLabel(){
		if(numMappedTEx == 0){
			p.outStr2Scr("Mapnode :"+mapNode.toString()+" has no mapped BMU examples.");
			return null;
		}
		return examplesBMU.firstEntry().getValue().getLabel();}
	public int getExmplBMUSize() {return  examplesBMU.size();}
	public void drawMeSmallBk(){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, 2, 2, 0, 0);		
		p.popStyle();p.popMatrix();		
	}
	@Override
	public void drawMeMap(){
		p.pushMatrix();p.pushStyle();
		p.show(mapLoc, getRad(), drawDet, label.clrVal,label.clrVal);		
		p.popStyle();p.popMatrix();		
	}
	@Override
	public void drawMeLblMap(){
		p.pushMatrix();p.pushStyle();
		//draw point of radius rad at maploc with label	
		p.show(mapLoc, getRad(), label.clrVal,label.clrVal, SOM_SphereMain.gui_FaintGray, label.label);
		p.popStyle();p.popMatrix();		
	}//drawLabel

	
	public String toString(){
		String res = "Node Loc : " + mapNode.toString()+"\t" + super.toString();
		return res;		
	}
}//SOMmapNode

//class holding data about a som feature
class SOMFeature{
	public SOMMapData map;			//owning map
	public SOM_SphereMain p;
	public int fIdx;
	public String name;
	public TreeMap<Float,SOMmapNode> sortedBMUs;		//best units for this particular feature, based on weight ratio (this is not actual weight of feature in node)

	public SOMFeature(SOMMapData _map, SOM_SphereMain _p, String _name, int _fIdx, String[] _tkns){//_tkns is in order idx%3==0 : wt ration, idx%3==1 : y coord, idx%3==2 : x coord
		map=_map; p=_p;fIdx=_fIdx;name=_name;
		setBMUWts(_tkns);
	}
	
	public void setBMUWts(String[] _tkns){
		if(_tkns == null){p.pr("Feature wts not found for feature : " + name + " idx : "+ fIdx);return;}
		sortedBMUs = new TreeMap<Float,SOMmapNode>();
	}
	
	public String toString(){
		String res = "Feature Name : "+name;
		return res;
	}

}//SOMFeature
//class description for a data point
class dataClass implements Comparable<dataClass> {
	public SOMMapData p;
	public String cls;
	public String label;
	public String lrnKey;
	public int intKVal, floatKVal;
	//color of this class, for vis rep
	public int[] clrVal;
	
	public dataClass(SOMMapData _p,int _ikv, int _fkv,  String _lbl, String _cls, int[] _clrVal){
		p=_p;intKVal=_ikv; floatKVal = _fkv;buildLrnKey(intKVal,floatKVal);
		label = _lbl;	
		cls = _cls;
		clrVal = _clrVal;
	}	
	public dataClass(dataClass _o){this(_o.p,_o.intKVal,_o.floatKVal, _o.label,_o.cls, _o.clrVal);}//copy ctor
	//builds the .lrn file key given the passed two integers
	protected void buildLrnKey(int a, int b){
		lrnKey = (buildPrfx(a) + "."+buildPrfx(b));
	}

	//this will guarantee that, so long as a string has only one period, the value returned will be in the appropriate format for this mocapClass to match it
	//reparses and recalcs subject and clip from passed val
	public static String getPrfxFromData(String val){
		String[] valTkns = val.trim().split("\\.");
		return  buildPrfx(Integer.parseInt(valTkns[0])) + "."+ buildPrfx(Integer.parseInt(valTkns[1]));		
	}	
	@Override
	public int compareTo(dataClass o) {	return label.compareTo(o.label);}
	public String toCSVString(){String res = "" + lrnKey +","+label+","+cls;	return res;}
	public String getFullLabel(){return label +"|"+cls;}
	public static String buildPrfx(int val){return (val < 100 ? (val < 10 ? "00" : "0") : "") + val;}//handles up to 999 val to be prefixed with 0's	
	public String toString(){
		String res = "Label :  " +label + "\tLrnKey : " + lrnKey  + "\tPrimKey # : " + intKVal+ "\tSecKey # : "+buildPrfx(floatKVal)+"\tDesc : "+cls;
		return res;		
	}	
}//dataClass

class dataDesc{
	public SOMMapData p;
	public String[] ftrNames;
	public int numFtrs;		
	
	public dataDesc(SOMMapData _p, int _numFtrs){
		p=_p;
		numFtrs = _numFtrs;
		ftrNames = new String[numFtrs];		
	}
	
	public dataDesc(SOMMapData _p,String [] tkns){
		this(_p, tkns.length);
		System.arraycopy(tkns, 0, ftrNames, 0, tkns.length);
	}
	
	//build the default header for this data descriptor
	public void buildDefHdr(){for(int i =0; i<numFtrs; ++i){ftrNames[i] = "ftr_"+i;}}//buildDefHdr
	
	public String toString(){
		String res = "";
		for(int i=0;i<ftrNames.length;++i){res += ftrNames[i] + ",";}
		return res;
	}
	
}//class dataDesc


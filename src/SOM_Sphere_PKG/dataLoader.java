package SOM_Sphere_PKG;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

//class that describes the hierarchy of files required for running and analysing a SOM
public class dataLoader implements Runnable {
	public SOM_SphereMain p;
	public SOMMapData map;				//the map these files will use
	public SOMDatFileConfig fnames;			//struct maintaining file names for all files in som, along with 
	
	public final float nodeDistThresh = 100000.0f;
	//idxs of different kinds of files
	public static final int
		wtsIDX = 0,
		fwtsIDX = 1,
		bmuIDX = 2,
		umtxIDX = 3;	
	
	//public boolean loadFtrBMUs;

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 				= 0,
			isCMUMocapIDX			= 1,		//use specific functionality designed for CMU mocap data config 
			loadFtrBMUsIDX			= 2;
	public static final int numFlags = 3;
	
	public dataLoader(SOM_SphereMain _p,SOMMapData _map, boolean _lBMUs, SOMDatFileConfig _fnames, boolean isCMUMocap) {
		p = _p;
		map = _map;
		fnames = _fnames;
		initFlags();
		setFlag(isCMUMocapIDX, isCMUMocap);
		setFlag(loadFtrBMUsIDX,  _lBMUs);
	}

	@Override
	public void run(){
		if(fnames.allReqFilesLoaded()){
			boolean success = (getFlag(isCMUMocapIDX) ? execCMUDataLoad() : execSphereDataLoad()) ;
			map.setFlag(SOMMapData.dataLoadedIDX,success);
			map.setFlag(SOMMapData.loaderRtnIDX,true);
			p.setCurSphrMapMade();//tell anim res window that map is made and data is loaded
			map.setMapImgClrs();
			p.pr("Finished data loader : data Loaded : " + map.getFlag(SOMMapData.dataLoadedIDX) + " | loader ret code : " +map.getFlag(SOMMapData.loaderRtnIDX) );
			
		}
		else {
			map.setFlag(SOMMapData.loaderRtnIDX,false);
			p.pr("Data loader Failed : fnames structure out of sync or file IO error ");
		}
	}
	//load results from sphere map processing - fnames needs to be modified to handle this
	private boolean execSphereDataLoad(){
		//load map weights for all map nodes
		boolean success = loadSOMWts();	
		//get classes from sphere data -  fnames.getClassFname() is to be ignored
		
		//get training data
		dataPoint[] tmpTrainAra = p.getSphrWinTrainData(),  
				tmpTestAra = p.getSphrWinTestData();
		map.trainData = new dataPoint[tmpTrainAra.length];
		map.testData = new dataPoint[tmpTestAra.length];
		map.numTrainData = map.trainData.length;
		map.numTestData = map.testData.length;
		System.arraycopy(tmpTrainAra, 0, map.trainData, 0, map.numTrainData);
		System.arraycopy(tmpTestAra, 0, map.testData, 0, map.numTestData);
		p.pr("Finished assigning Training and Testing data from sphere data -> added " + map.numTrainData + " training examples and " +map.numTestData + " testing examples");
		//load SOM's best matching units for training data - must be after map wts and training data has been loaded
		success = loadSOM_BMs();
//		//load SOM's sorted best matching units for each feature - must be after map wts and training data has been loaded
		if (getFlag(loadFtrBMUsIDX)){
			success = loadSOM_ftrBMUs();	
		}
		//save csv class files
		map.setFlag(SOMMapData.saveLocClrImgIDX,true);
		return success;
	}//execSphereDataLoad
	
	//manage specific process for loading CMU data from disk
	private boolean execCMUDataLoad(){
		//load map weights for all map nodes
		boolean success = loadSOMWts();	
		//load class data txt file that was scraped off CMU website
		success = loadCMUClassesData();
		//load training data
		success = loadCSVTrainingData(true);		//TODO might not be 1st col having tag info
		//set all features and scaled features for all loaded dataPoints and SOMmapNodes - requires that all mins and diffs be loaded
		success = condAllData();		
		//load SOM's best matching units for training data - must be after map wts and training data has been loaded
		success = loadSOM_BMs();		
		//load SOM's sorted best matching units for each feature - must be after map wts and training data has been loaded
		if (getFlag(loadFtrBMUsIDX)){
			success = loadSOM_ftrBMUs();	
		}
		//reformat and save multiple-format training class csv files
		saveCSVTrainingClasses("IDX,lrnFileKey,class_and_description");
		return success;	
	}//execCMUDataLoad
	
	
	//this will make sure that all scaled features are actually scaled and nonscaled are actually nonscaled
	public boolean condAllData(){
		String diffsFileName = fnames.getDiffsFname(), minsFileName = fnames.getMinsFname();
		//load normalizing values for datapoints in weights - differences and mins, used to scale/descale training and map data
		map.diffsVals = loadCSVSrcDataPoint(diffsFileName);
		if((null==map.diffsVals) || (map.diffsVals.length < 1)){p.pr("!!error reading diffsFile : " + diffsFileName); return false;}
		map.minsVals = loadCSVSrcDataPoint(minsFileName);
		if((null==map.minsVals)|| (map.minsVals.length < 1)){p.pr("!!error reading minsFile : " + minsFileName); return false;}	

		//for map nodes - descale features aras for map nodes
		for(Tuple<Integer,Integer> key : map.MapNodes.keySet()){SOMmapNode tmp = map.MapNodes.get(key); tmp.setCorrectScaling();}//p.pr(tmp.toString());}
		//training & testing data points scale/descale
		for(int i=0; i<map.trainData.length;++i){map.trainData[i].setCorrectScaling(); }//p.pr(trainData[i].toString());}
		for(int i=0; i<map.testData.length;++i){map.testData[i].setCorrectScaling();}
		return true;
	}//condAllData()
	
	//reading in description of mocap data from cmu website - apparently clips are missing
	public boolean loadCMUClassesData(){
		String classFileName = fnames.getClassFname();
		map.TrainDataLabels = new TreeMap<String,dataClass>();		
		String[] strs = p.loadFileIntoStringAra(classFileName,"Class File loaded : " + classFileName, "Error reading class file : " + classFileName);
		if(strs == null){return false;}
		int curSubject = -1;
		String curTopic = "";
		int numObjs = 1;
		for (int i=0;i<strs.length;++i){
			String[] tkns = strs[i].trim().split("\\s* \\s*");
			if(tkns.length < 2){continue;}
			if(tkns[0].contains("Subject")){
				String[] tmpTkns = tkns[1].trim().split("#");
				curSubject = Integer.parseInt(tmpTkns[1]);
				int stIdx = strs[i].indexOf("("), endIdx = strs[i].lastIndexOf(")");				
				curTopic = strs[i].substring(stIdx+1, endIdx).replace(", ", "&");
				continue;
			};
			String tmpStr = strs[i].trim(), desc = tmpStr.substring(tmpStr.indexOf(" ")+1, tmpStr.length());
			dataClass tmpMC = new dataClass(map,curSubject, Integer.parseInt(tkns[0]), curTopic, desc.replace(", ", "->"), null);
			map.TrainDataLabels.put(tmpMC.lrnKey, tmpMC);
//			/p.pr(tmpMC.toString());
			numObjs++;
		}//for every string in file data	
		p.pr("Done processing mocap data classification file : " + p.getFName(classFileName) + " # clips = " + (numObjs-1) );
		return true;
	}//loadMocapClassesData
//	
//	public void saveMocapClassesData(String outFileName, String classCSVHdr){
//		String[] stringRes = new String[map.TrainDataLabels.size()+1];
//		stringRes[0] = classCSVHdr;
//		int i = 1;
//		for(String key : map.TrainDataLabels.keySet()){	stringRes[i++] = map.TrainDataLabels.get(key).toString();}
//		p.saveStrings(outFileName, stringRes);
//		p.pr("Done saving mocap data classification file :"+ outFileName);		
//	}//saveMocapClassesData
		
	//load scaled training data used to build SOM - CSV version of data
	public boolean loadCSVTrainingData(boolean _firstColIsTag){
		String trainDataFName = fnames.getTrainFname();
		if(trainDataFName.length() < 1){return false;}
		String [] strs= p.loadFileIntoStringAra(trainDataFName, "Loaded CSV training data file : "+trainDataFName, "Error reading CSV training data file : "+trainDataFName);
		if(strs==null){return false;}
		dataPoint tmp;
		int numTrainRecs = 0;
		ArrayList<dataPoint> _tmpTrainData = new ArrayList<dataPoint>();
		//since this is saved in a format to be conducive to the lrn file format, the first column is a node identifier.  for moments training data it is "subject"."clip#"
		for(int i = 0; i<strs.length; ++i){
			String[] _tkns = strs[i].split(p.csvFileToken);
			tmp = new dataPoint(p, map,_tkns, _firstColIsTag, ++numTrainRecs, _firstColIsTag, false);			
			//if(_firstColIsTag && (null != TrainDataLabels.get(tmp.texID))){tmp.label = TrainDataLabels.get(tmp.texID);}
			_tmpTrainData.add(tmp);
		}			
		map.trainData = _tmpTrainData.toArray(new dataPoint[0]);
		map.numTrainData = map.trainData.length;
		p.pr("Finished Loading CSV SOM Training data from file : " + p.getFName(trainDataFName) + " Loaded " + map.numTrainData + " training examples");
		return true;
	}//loadTrainingData	
	
	//read file with scaling/min values for Map to convert data back to original feature space - single row of data
	private Float[] loadCSVSrcDataPoint(String fileName){		
		if(fileName.length() < 1){return null;}
		String [] strs= p.loadFileIntoStringAra(fileName, "Loaded data file : "+fileName, "Error reading file : "+fileName);
		if(strs==null){return null;}
		//strs should only be length 1
		if(strs.length > 1){p.pr("error reading file : " + fileName + " String array has more than 1 row.");return null;}		
		String[] tkns = strs[0].split(p.csvFileToken);
		ArrayList<Float> tmpData = new ArrayList<Float>();
		for(int i =0; i<tkns.length;++i){
			tmpData.add(Float.parseFloat(tkns[i]));
		}
		return tmpData.toArray(new Float[0]);
	}//loadCSVData
		
/////source independent file loading
	//verify file map dimensions agree
	private boolean checkMapDim(String[] tkns, String errorMSG){
		int tmapY = Integer.parseInt(tkns[0]), tmapX = Integer.parseInt(tkns[1]);
		if((tmapY != map.getMapY()) || (tmapX != map.getMapX())) { 
			p.pr("!!"+ errorMSG + " dimensions : " + tmapX +","+tmapY+" do not match dimensions of learned weights " + map.getMapX() +","+map.getMapY()+". Loading aborted."); 
			return false;} 
		return true;		
	}
	//load map wts from file build by SOMOCLU
	private boolean loadSOMWts(){//builds mapnodes structure - each map node's weights 
		String wtsFileName = fnames.getSOMResFName(wtsIDX);
		map.MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMmapNode>();
		if(wtsFileName.length() < 1){return false;}
		String [] strs= p.loadFileIntoStringAra(wtsFileName, "Loaded wts data file : "+wtsFileName, "Error wts reading file : "+wtsFileName);
		if(strs==null){return false;}
		String[] tkns,ftrNames;
		SOMmapNode dpt;	
		int numEx = 0, mapX=1, mapY=1,numWtData = 0;
		Tuple<Integer,Integer> mapLoc;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(p.SOM_FileToken);
				if(i==0){mapY = Integer.parseInt(tkns[0]);map.setMapY(mapY);mapX = Integer.parseInt(tkns[1]);map.setMapX(mapX);	} 
				else {	
					map.numFtrs = Integer.parseInt(tkns[0]);
					ftrNames = new String[map.numFtrs];
					for(int j=0;j<map.numFtrs;++j){ftrNames[j]=""+j;}					
					map.dataHdr = new dataDesc(map, ftrNames);				//assign numbers to feature name data header
					map.map_ftrsMean = new float[map.numFtrs];
					map.map_ftrsVar = new float[map.numFtrs];
				}	
				continue;
			}//if first 2 lines in wts file
			tkns = strs[i].split(p.SOM_FileToken);
			if(tkns.length < 2){continue;}
			mapLoc = new Tuple<Integer, Integer>((i-2)%mapX, (i-2)/mapX);//map locations in som data are increasing in x first, then y (row major)
			dpt = new SOMmapNode(p,map, tkns, ++numWtData, mapLoc, false, true);//give each map node its color, so that if the map is displayed in color, the node and its text can be the opposite color and contrast
			++numEx;
			for(int d = 0; d<map.numFtrs; ++d){map.map_ftrsMean[d] += dpt.ftrs[d];}
			map.MapNodes.put(mapLoc, dpt);			
			map.nodesWithNoEx.add(dpt);				//add all nodes to set, will remove nodes when they get mappings
		}
		for(int d = 0; d<map.numFtrs; ++d){map.map_ftrsMean[d] /= 1.0f*numEx;}
		//variance calc
		float diff;
		for(Tuple<Integer, Integer> key : map.MapNodes.keySet()){
			SOMmapNode tmp = map.MapNodes.get(key);
			for(int d = 0; d<map.numFtrs; ++d){
				diff = map.map_ftrsMean[d] - tmp.ftrs[d];
				map.map_ftrsVar[d] += diff*diff;
			}
		}
		for(int d = 0; d<map.numFtrs; ++d){map.map_ftrsVar[d] /= 1.0f*numEx;}
		p.pr("Finished Loading SOM weight data from file : " + p.getFName(wtsFileName));
		return true;
	}//loadSOMWts	
	//load best matching units for each training example - has values : idx, mapy, mapx
	private boolean loadSOM_BMs(){//modifies existing nodes and datapoints only
		String bmFileName = fnames.getSOMResFName(bmuIDX);
		if(bmFileName.length() < 1){return false;}
		map.nodesWithEx.clear();
		String [] strs= p.loadFileIntoStringAra(bmFileName, "Loaded best matching unit data file : "+bmFileName, "Error reading best matching unit file : "+bmFileName);
		if(strs==null){return false;}
		String[] tkns;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(p.SOM_FileToken);
				if(i==0){
					if(!checkMapDim(tkns,"Best Matching Units file " + p.getFName(bmFileName))){return false;}
				} else {	
					int tNumTDat = Integer.parseInt(tkns[0]);
					if(tNumTDat != map.numTrainData) { 
						p.pr("!!Best Matching Units file " + p.getFName(bmFileName) + " # of training examples : " + tNumTDat +" does not match # of training examples in training data " + map.numTrainData+". Loading aborted." ); 
						return false;}
				}
				continue;
			} 
			tkns = strs[i].split(p.SOM_FileToken);
			if(tkns.length < 2){continue;}
			Tuple<Integer,Integer> mapLoc = new Tuple<Integer, Integer>(Integer.parseInt(tkns[2]),Integer.parseInt(tkns[1]));//map locations in bmu data are in (y,x) order (row major)
			SOMmapNode tmpMapNode = map.MapNodes.get(mapLoc);
			if(null==tmpMapNode){ p.pr("!!Map node stated as best matching unit for training example " + tkns[0] + " not found in map ... somehow. "); return false;}//catastrophic error shouldn't happen
			dataPoint tmpDP = map.trainData[Integer.parseInt(tkns[0])];
			if(null==tmpDP){ p.pr("!!Training Datapoint given by idx in BMU file str tok : " + tkns[0] + " of string : --" + strs[i] + "-- not found in training data. "); return false;}//catastrophic error shouldn't happen
			//passing per-ftr variance for chi sq distance
			tmpDP.setBMU(tmpMapNode, map.map_ftrsVar);
			map.nodesWithEx.add(tmpMapNode);
			map.nodesWithNoEx.remove(tmpMapNode);
			//p.pr("Tuple "  + mapLoc + " from str @ i-2 = " + (i-2) + " node : " + tmpMapNode.toString());
		}
		//set all empty mapnodes to have a label based on the most common label of their 4 neighbors (up,down,left,right)
		for(SOMmapNode node : map.nodesWithNoEx){
			//tmpMapNode has no mappings, so need to determine label
			for(SOMmapNode node2 : map.nodesWithEx){
				float dist = getSqMapDist(node2, node);			//actual map topology dist
				if(dist <= nodeDistThresh){
					node.addBMUExample(dist, node2);			//adds a node we know has a label
				}
			}			
		}
		
		p.pr("Finished Loading SOM BMUs from file : " + p.getFName(bmFileName) + "| Found "+map.nodesWithEx.size()+" nodes with example mappings.");
		return true;
	}//loadSOM_BMs
	//returns sq distance between two map locations
	public float getSqMapDist(SOMmapNode a, SOMmapNode b){return (float)(a.mapLoc._SqrDist(b.mapLoc));	}
	
	private boolean loadSOM_ftrBMUs(){
		String ftrBMUFname =  fnames.getSOMResFName(fwtsIDX);
		if(ftrBMUFname.length() < 1){return false;}
		String [] strs= p.loadFileIntoStringAra(ftrBMUFname, "Loaded features with bmu data file : "+ftrBMUFname, "Error reading feature bmu file : "+ftrBMUFname);
		if(strs==null){return false;}
		String[] tkns;
		SOMFeature tmp;
		ArrayList<SOMFeature> tmpAra = new ArrayList<SOMFeature>();
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(p.SOM_FileToken);
				if(i==0){
					if(!checkMapDim(tkns,"Feature Best Matching Units file " + p.getFName(ftrBMUFname))){return false;}
				} else {	
					int tNumTFtr = Integer.parseInt(tkns[0]);
					if(tNumTFtr != map.numFtrs) { 
						p.pr("!!Best Matching Units file " + p.getFName(ftrBMUFname) + " # of training examples : " + tNumTFtr +" does not match # of training examples in training data " + map.numFtrs+". Loading aborted." ); 
						return false;}
				}
				continue;
			} 
			tkns = strs[i].split(":");
			tmp = new SOMFeature(map,p,tkns[0].trim(),Integer.parseInt(tkns[0].trim()),tkns[1].trim().split(p.SOM_FileToken));
			tmpAra.add(tmp);
		}
		map.featuresBMUs = tmpAra.toArray(new SOMFeature[0]);		
		p.pr("Finished Loading SOM per-feature BMU list from file : " + p.getFName(ftrBMUFname));
		return true;
	}//loadSOM_ftrBMUs	
		
	//save current data set entered by user as csv file
	public void saveInputData(String saveFileName){
		String[] vals = new String[map.numInputData+1];
		vals[0] = map.dataHdr.toString();
		for(int i=0;i<map.numInputData;++i){vals[i+1]=map.inputData[i].toCSVString();}
		p.saveStrings(saveFileName,vals);		
		p.pr("Done saving data points to file : " + saveFileName + "\tLength : " + map.numInputData + "\tSize of feature vector : " + map.numFtrs);
	}//saveEncodedData
	//save training data labels in map in various formats (exemplar, brief labels, verbose labels) as cvs files
	public void saveCSVTrainingClasses(String TrainMCClassCSVHdr){				
		String[] stringRes = new String[map.trainData.length+1], brfStringRes = new String[map.trainData.length], sparseStringRes = new String[map.trainData.length];
		TreeMap<Integer, String[]> perClassRes = new TreeMap<Integer, String[]>();
		stringRes[0] = TrainMCClassCSVHdr;
		for(int i = 0;i<map.trainData.length;++i){
			stringRes[i+1] = map.trainData[i].getLabelInfo();
			brfStringRes[i] = map.trainData[i].getLabelBriefInfo();
			sparseStringRes[i] = ((i+1)%10==0 ?  brfStringRes[i] : "");
			Integer key = map.trainData[i].getSubj();
			if(key != -1){
				String[] tmpAra = perClassRes.get(key);						
				if (tmpAra == null){
					tmpAra = new String[map.trainData.length];
					for(int j=0;j<map.trainData.length;++j){tmpAra[j]="";}
				}
				tmpAra[i]=brfStringRes[i];
				perClassRes.put(key, tmpAra);
			}						
		}
		String sfx = "_useAll_" + "1";//p.useAllMmnts;	
		p.saveStrings(fnames.getCSVSvFName(0,0, sfx), stringRes);  //fnames.getSvFName(0,0);
		p.saveStrings(fnames.getCSVSvFName(1,0, sfx), brfStringRes);
		p.saveStrings(fnames.getCSVSvFName(2,0, sfx), sparseStringRes);
		for (Integer key : perClassRes.keySet()){
			String tmpFileName = fnames.getCSVSvFName(3,key, sfx);
			p.saveStrings(tmpFileName, perClassRes.get(key));
		}
		p.pr("Done with saveCSVTrainingClasses : saving training data classification files built from map's training data");		
	}//saveCSVTrainingClasses
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}	
			case isCMUMocapIDX : {break;}
			case loadFtrBMUsIDX : {break;}
		}
	}//setFlag	
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
}//dataLoader

//runnable data loader so that processing doesn't time out
class sphereLoader implements Runnable {
	public SOM_SphereMain pa;
	public mySOMAnimResWin win;

	public sphereLoader(SOM_SphereMain _p,mySOMAnimResWin _win) {
		pa = _p;
		win = _win;
		mySOMSphere.IDGen = 0;
	}

	@Override
	public void run() {
		win.spheres = new mySOMSphere[win.numSpheres];
		win.sphereCtrData = new dataPoint[win.numSpheres];
		win.sphereSmplData = new dataPoint[win.numSpheres * win.numSmplPoints];
		int[] colorVal;
		float rad;
		for(int i = 0; i<win.numSpheres;++i){
			colorVal = pa.getClr((int)(ThreadLocalRandom.current().nextDouble(1,23)),255);
			rad = (float)(ThreadLocalRandom.current().nextDouble(win.minSphRad,win.maxSphRad));
			myVectorf loc = pa.getRandPosInCube(pa.cubeBnds, rad);
			win.spheres[i] = new mySOMSphere(pa, win, loc, rad, win.numSmplPoints, colorVal);			
			win.sphereCtrData[i] = new dataPoint(pa,win.SOMSpheres_Data,loc.asArray(), false, i, false, true);//make color ara
			win.sphereCtrData[i].setCorrectScaling(pa.cubeBnds[0],pa.cubeBnds[1]);
			win.sphereCtrData[i].label = new dataClass(win.SOMSpheres_Data,win.spheres[i].ID,0,"Sphere "+ win.spheres[i].ID,"Sphere "+ win.spheres[i].ID + " loc : "+win.spheres[i].loc.toStrBrf(), win.spheres[i].clrVal);
			System.arraycopy(win.spheres[i].smplPts, 0, win.sphereSmplData, (i*win.numSmplPoints), win.numSmplPoints);
		}	
		pa.outStr2Scr("************* Spheres generated **********************");
		pa.setSphereLRNFileName(win.numSpheres, win.numSmplPoints, win.maxSphRad);
		win.setPrivFlags(win.sphereDataLoadedIDX, true); 		//sheres are made
		win.setPrivFlags(win.mapBuiltToCurSphrsIDX, false);  	//current map is now out of sync
		win.setPrivFlags(win.currSphrDatSavedIDX, false);   	//current spheres have not been saved to file
	}
}//sphereLoader class

//save all sphere data to appropriate format
class sphereWriter implements Runnable{
	public SOM_SphereMain pa;
	public mySOMAnimResWin win;
	public String lrnFileName, testFileName, minsFileName, diffsFileName;
	public sphereWriter(SOM_SphereMain _p,mySOMAnimResWin _win) {
		pa = _p;
		win = _win;
		lrnFileName = pa.getSphereLRNFileName();
		testFileName = pa.getSphereTestFileName();		
		minsFileName = pa.getSphereMinsFileName();
		diffsFileName = pa.getSphereDiffsFileName();		
	}


	//write all sphere data to appropriate files
	@Override
	public void run() {
		//save to lrnFileName - build lrn file
		//4 extra lines that describe dense .lrn file - started with '%'
		//0 : # of examples
		//1 : # of features + 1 for name column
		//2 : format of columns -> 9 1 1 1 1 ...
		//3 : names of columns (not used by somoclu)
		String ctrFileName, sampleFileName, ctrSep, smplSep, sphereMsg, smplMsg;
		if(win.getPrivFlags(win.useSmplsForTrainIDX)){//samples are training data (.lrn), centers are test(.csv)
			ctrFileName = testFileName;
			sphereMsg = "Sphere Centers CSV testing data file saved";
			ctrSep = ",";
			sampleFileName = lrnFileName;
			smplSep = " ";
			smplMsg = "Surface Samples CSV training data file saved";
			win.sphereTrainData = win.sphereSmplData; 
			win.sphereTestData = win.sphereCtrData;

		} else {//centers are training data, samples are test
			ctrFileName = lrnFileName;
			ctrSep = " ";
			sphereMsg = "Sphere Centers LRN training data file saved";
			sampleFileName = testFileName;
			smplSep = ",";
			smplMsg = "Surface Samples CSV testing data file saved";
			win.sphereTestData = win.sphereSmplData; 
			win.sphereTrainData = win.sphereCtrData;
		}
		
		String[] outStrings = new String[win.numSpheres + 4];
		outStrings[0]="% "+win.numSpheres;
		outStrings[1]="% 3";
		outStrings[2]="% 9 1 1 1";
		outStrings[3]="% Key c1 c2 c3";
		
		for(int i = 0; i<win.sphereCtrData.length;++i){
			outStrings[i+4] = win.sphereCtrData[i].toLRNString(ctrSep);
//			pa.outStr2Scr("sphere ctr ex : " + (i+4) + " : "+win.sphereTrainData[i].toString());
		}
		pa.saveStrings(ctrFileName,outStrings);
		pa.outStr2Scr(sphereMsg);
		//save to testFileName
		int numSmpls = (win.numSpheres * win.numSmplPoints);
		outStrings = new String[numSmpls + 4];
		outStrings[0]="% "+(numSmpls);
		outStrings[1]="% 3";
		outStrings[2]="% 9 1 1 1";
		outStrings[3]="% Key c1 c2 c3";
		int strIDX = 4;
		for(int i = 0; i<win.numSpheres;++i){
			dataPoint[] tmp = win.spheres[i].smplPts;
			for(dataPoint dp : tmp){	
				outStrings[strIDX++] = dp.toLRNString(smplSep);			
//				pa.outStr2Scr("sphere smple ex : sphere : " + i + " str idx : " + (strIDX-1) + " : "+dp.toString());
			}
		}
		pa.saveStrings(sampleFileName,outStrings);		
		pa.outStr2Scr(smplMsg);	
		//save diffs and mins - csv files with each field value sep'ed by a comma
		//sphereBnds  //boundary region for sphere locations - min val for all ftrs is idx 0, idx 1 is ara of diffs
		String diffStr = "", minStr = "";
		for(int i =0; i<pa.cubeBnds[0].length; ++i){
			minStr += String.format("%1.7g", pa.cubeBnds[0][i]) + ",";
			diffStr += String.format("%1.7g", pa.cubeBnds[1][i]) + ",";
		}
		pa.saveStrings(minsFileName,new String[]{minStr});		
		pa.saveStrings(diffsFileName,new String[]{diffStr});		
		pa.outStr2Scr("Mins and Diffs Files Saved");
		win.setPrivFlags(win.currSphrDatSavedIDX, true);
	}
}//sphereWriter

package SOM_Sphere_PKG;
//class to hold location, radius, color and samples of sphere used to train SOM
public class mySOMSphere {
	public static SOM_SphereMain pa;
	public static mySOMAnimResWin win;
	public final int ID;
	public static int IDGen = 0;
	public myVectorf loc;
	public int[] locClrAra;
	public float rad, ptRad;
	
	public dataPoint dp;						//ref to best matching unit of map
		
	public dataPoint[] smplPts;
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 				= 0;		//draw this sphere's sample points
	public static final int numFlags = 1;		
	
	public int sphrDet, ptDet;
	public int[] clrVal;
	
	public mySOMSphere(SOM_SphereMain _p, mySOMAnimResWin _win, myVectorf _loc, float _rad, int numSmplPts, int[] _clrVal) {
		pa=_p; win = _win;
		ID = IDGen++;
		initFlags();
		loc = _loc;
		locClrAra = pa.getClrFromCubeLoc(loc.asArray());
		rad = _rad;

		ptRad = pa.min(.1f*rad, 3);
		sphrDet = (int)(pa.sqrt(rad) + 10);
		ptDet = 2;//(int)(pa.sqrt(ptRad) + 3);
		clrVal = _clrVal;

		smplPts = new dataPoint[numSmplPts];
		int offset = numSmplPts * ID;
		for(int i = 0; i<numSmplPts;++i){
			smplPts[i] = new dataPoint(pa,win.SOMSpheres_Data, pa.getRandPosOnSphere(rad,loc).asArray(),false, (offset+i) , false, true);
			smplPts[i].setCorrectScaling(pa.cubeBnds[0],pa.cubeBnds[1]);
			smplPts[i].label = new dataClass(win.SOMSpheres_Data,ID,i+1,"Sphr "+ ID + " Smpl "+i,"Sphere "+ ID + " loc : "+smplPts[i].worldLoc.toStrBrf(), clrVal);
			//pa.outStr2Scr("Sphere ID:"+ID+" smpl : "+ i+" : \tlabel : " + smplPts[i].label.toString());
		}
		//pa.outStr2Scr("Done building sphere " + ID);
	}//ctor
	
	public void drawMeClrRnd(){
		pa.pushMatrix();pa.pushStyle();		
			pa.show(loc, rad, sphrDet, clrVal, clrVal);		//show main sphere in random color
		pa.popStyle();pa.popMatrix();
	}//
	public void drawMeClrLoc(){
		pa.pushMatrix();pa.pushStyle();		
			pa.show(loc, rad, sphrDet, locClrAra, locClrAra);		//show main sphere in location color
		pa.popStyle();pa.popMatrix();
	}//
	private static float modCnt = 0;//counter that will determine when the color should switch
	public void drawMeSelected(float animTmMod){//animTmMod is time since last frame
		modCnt += animTmMod;
		if(modCnt > 1.0){	modCnt = 0;	}//blink every ~second
		pa.pushMatrix();pa.pushStyle();		
		pa.noFill();//fill(255*modCnt,255);
		pa.stroke(255*modCnt, 255);		
		pa.translate(loc); 
		pa.sphere(rad*(modCnt + 1.0f)); 
		pa.popStyle();pa.popMatrix();
	}
	
	public void drawMeLabel(){
		pa.pushMatrix();pa.pushStyle();		
		pa.setColorValFill(0,255); 
		pa.setColorValStroke(0,255);		
		pa.translate(loc); 
		pa.unSetCamOrient();
		pa.scale(.75f);
		pa.text(""+ID, rad,-rad,0); 
		pa.popStyle();pa.popMatrix();
	}
	
	public void drawMeSmplsClrRnd(){
		pa.pushMatrix();pa.pushStyle();
//		pa.setColorValFill(clrVal,255); 
//		pa.setColorValStroke(clrVal,255);
		pa.setFill(clrVal,255); 
		pa.setStroke(clrVal,255);
		pa.sphereDetail(ptDet);
		for(dataPoint pt : smplPts){
			pa.pushMatrix(); 
			pa.translate(pt.worldLoc); 
			pa.sphere(ptRad); 
			pa.popMatrix();
		}
		pa.popStyle();pa.popMatrix();
	}//
	public void drawMeSmplsClrLoc(){
		pa.pushMatrix();pa.pushStyle();		
		pa.setFill(locClrAra,255);
		pa.setStroke(locClrAra,255);
		pa.sphereDetail(ptDet);
		for(dataPoint pt : smplPts){
			pa.pushMatrix(); 
			pa.translate(pt.worldLoc); 
			pa.sphere(ptRad); 
			pa.popMatrix();
		}
		pa.popStyle();pa.popMatrix();
	}//	
	
	public void drawMeSmplsClrSmplLoc(){
		pa.pushMatrix();pa.pushStyle();		
		//pa.noStroke();
		pa.sphereDetail(ptDet);
		for(dataPoint pt : smplPts){
			pa.pushMatrix(); pa.pushStyle();
			pa.fill(pt.locClrs[0],pt.locClrs[1],pt.locClrs[2], pt.locClrs[3]);
			pa.stroke(pt.locClrs[0],pt.locClrs[1],pt.locClrs[2], pt.locClrs[3]);
			
			pa.translate(pt.worldLoc); 
			pa.sphere(ptRad); 
			pa.popStyle();pa.popMatrix();
		}
		pa.popStyle();pa.popMatrix();
	}
	
///////////////BMU / datapoint drawing
	public void drawMeClrRnd_BMU(){
		pa.pushMatrix();pa.pushStyle();		
			pa.show(dp.bmu.worldLoc, rad, sphrDet, clrVal, clrVal);		//show main sphere in random color
		pa.popStyle();pa.popMatrix();
	}//
	public void drawMeClrLoc_BMU(){
		pa.pushMatrix();pa.pushStyle();		
			pa.show(dp.bmu.worldLoc, rad, sphrDet, locClrAra, locClrAra);		//show main sphere in location color
		pa.popStyle();pa.popMatrix();
	}//

	
	public void drawMeSelected_BMU(float animTmMod){//animTmMod is time since last frame
		modCnt += animTmMod;
		if(modCnt > 1.0){	modCnt = 0;	}//blink every ~second
		pa.pushMatrix();pa.pushStyle();		
		pa.noFill();
		pa.stroke(255*modCnt, 255);		
		pa.translate(dp.bmu.mapLoc); 
		pa.sphere(rad*(modCnt + 1.0f)); 
		pa.popStyle();pa.popMatrix();
	}
	
	public void drawMeLabel_BMU(){
		pa.pushMatrix();pa.pushStyle();		
		pa.setColorValFill(0,255); 
		pa.setColorValStroke(0,255);		
		pa.translate(dp.bmu.mapLoc); 
		pa.unSetCamOrient();
		pa.scale(.75f);
		pa.text(""+ID, rad,-rad,0); 
		pa.popStyle();pa.popMatrix();
	}

	public void drawMeSmplsClrRnd_BMU(){
		pa.pushMatrix();pa.pushStyle();
		pa.setFill(clrVal,255); 
		pa.setStroke(clrVal,255);
		pa.sphereDetail(ptDet);
		for(dataPoint pt : smplPts){
			pa.pushMatrix(); 
			pa.translate(pt.bmu.mapLoc); 
			pa.sphere(ptRad); 
			pa.popMatrix();
		}
		pa.popStyle();pa.popMatrix();
	}//
	
	public void drawMeSmplsClrLoc_BMU(){
		pa.pushMatrix();pa.pushStyle();		
		pa.setFill(locClrAra,255);
		pa.setStroke(locClrAra,255);
		pa.sphereDetail(ptDet);
		for(dataPoint pt : smplPts){
			pa.pushMatrix(); 
			pa.translate(pt.bmu.worldLoc); 
			pa.sphere(ptRad); 
			pa.popMatrix();
		}
		pa.popStyle();pa.popMatrix();
	}//	

	public void drawMeSmplsClrSmplLoc_BMU(){
		pa.pushMatrix();pa.pushStyle();		
		//pa.noStroke();
		pa.sphereDetail(ptDet);
		for(dataPoint pt : smplPts){
			pa.pushMatrix(); pa.pushStyle();
			pa.fill(pt.locClrs[0],pt.locClrs[1],pt.locClrs[2], pt.locClrs[3]);
			pa.stroke(pt.locClrs[0],pt.locClrs[1],pt.locClrs[2], pt.locClrs[3]);
			
			pa.translate(pt.bmu.worldLoc); 
			pa.sphere(ptRad); 
			pa.popStyle();pa.popMatrix();
		}
		pa.popStyle();pa.popMatrix();
	}
	
	//returns ara of coords to be used as training features for som
	//public dataPoint getDataPoint(){		return dp;}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}			
		}
	}//setFlag	
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		

}//mySOMSphere

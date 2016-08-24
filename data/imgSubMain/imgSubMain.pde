
PImage img1, img2;
PImage diff;
void setup(){
  size(1000, 1000,P3D);  
    img1 = loadImage("data/sphereCtrs.png");
    img2 = loadImage("sphereSamples.png");
    //img1.loadPixels();
    //img2.loadPixels();
    int w = img1.width, h = img1.height;
    img1.blend(img2,0,0,w,h,0,0,w,h,DIFFERENCE );
    img1.save("diffImg.png");
}

void draw(){
  image(img1, 0,0);
}
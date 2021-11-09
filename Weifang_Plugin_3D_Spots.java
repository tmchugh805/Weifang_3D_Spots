import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.plugin.filter.MaximumFinder;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

public class Weifang_Plugin_3D_Spots implements PlugIn {

    String directory;
    String filename;
    RoiManager roiManager;
    double pixelWidth;
    double pixelHeight;
    double pixelDepth;
    double redTolerance;
    double greenTolerance;

    public void run(String arg) {

        new WaitForUserDialog("Open Image", "Open Images. SPLIT CHANNELS!").show();
        IJ.run("Bio-Formats Importer");// Open new file
        directory = IJ.getDirectory("Current");
        ImagePlus imp = WindowManager.getCurrentImage();
        filename = imp.getShortTitle();
        IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        pixelWidth = imp.getCalibration().pixelWidth;
        pixelHeight = imp.getCalibration().pixelHeight;
        pixelDepth = imp.getCalibration().pixelDepth;

        //Select Blue Green and Red Channels and set tolerances
        String[] channelTitles = WindowManager.getImageTitles();
        String[] fileNameList = channelSelector(channelTitles);

        IJ.log(filename);
        IJ.log("Pixel Width: " + pixelWidth + "  Pixel Height: " + pixelHeight + "  Pixel Depth: " + pixelDepth);


        roiManager = new RoiManager();

        ImagePlus greenChannel = WindowManager.getImage(fileNameList[0]);
        greenChannel.setTitle("Green");
        ImagePlus redChannel = WindowManager.getImage(fileNameList[1]);
        redChannel.setTitle("Red");


        IJ.log("Red Tolerance: "+ redTolerance+ " Green Tolerance: "+ greenTolerance);

        //Zproject Green Channel and find the x-y Maxima using ImageJ 'Find Maxima...' and user set tolerance
        ImagePlus greenChannelZproject= ZProjector.run(greenChannel,"max");
        ImageProcessor ip = greenChannelZproject.getProcessor();
        MaximumFinder maxGreen = new MaximumFinder();
        Polygon greenMaxima = maxGreen.getMaxima(ip,greenTolerance,true);
        int[] xGreen =  greenMaxima.xpoints;
        int[] yGreen = greenMaxima.ypoints;
        int nGreen = greenMaxima.npoints;
        double[][] xyGreen = new double[nGreen][2];
        for(int i = 0;i<nGreen;i++){
            xyGreen[i][0]=(double) xGreen[i];
            xyGreen[i][1]= (double) yGreen[i];
        }

        //Zproject Red Channel and find the x-y Maxima using ImageJ 'Find Maxima...' and user set tolerance
        ImagePlus redChannelZproject= ZProjector.run(redChannel,"max");
        ImageProcessor ip2 = redChannelZproject.getProcessor();
        MaximumFinder maxRed = new MaximumFinder();
        Polygon redMaxima = maxRed.getMaxima(ip2,redTolerance,true);
        int[] xRed =  redMaxima.xpoints;
        int[] yRed = redMaxima.ypoints;
        int nRed = redMaxima.npoints;
        double[][] xyRed = new double[nRed][2];
        for(int i = 0;i<nRed;i++){
            xyRed[i][0]=(double) xRed[i];
            xyRed[i][1]= (double) yRed[i];
        }

        //Find z positions of the maxima in the green and red channels
        double[][] xyzGreen = findZPositions(greenChannel, xyGreen);
        double[][] xyzRed = findZPositions(redChannel, xyRed);

        //Find distances between all green and red spots
        double[][] distances = findDistances(xyzGreen, xyzRed);

        // For each green spot find the closest red spot and report total number of spots found
        double[][] closestGreen = findNearest(distances);
        IJ.log("Green Positions found: "+ xyzGreen.length);
        IJ.log("Red Positions found: "+ xyzRed.length);


        //If the closest Red spot is within 3 microns label the spots on the output image and write to IJ log
        for (int j = 0; j < closestGreen.length; j++) {
            if(closestGreen[j][2]<3){
                int greenValue = (int)closestGreen[j][1];
                int redValue = (int)closestGreen[j][0];
                setImageNumbersSlices(greenValue+1, greenChannel, (int) xyzGreen[greenValue][0], (int) xyzGreen[greenValue][1],(int) xyzGreen[greenValue][2]);
                setImageNumbersSlices(redValue+1, redChannel, (int) xyzRed[redValue][0], (int) xyzRed[redValue][1],(int) xyzRed[redValue][2]);
                IJ.log("Red:  " + " " + (closestGreen[j][0] +1) + "  Green: " + (closestGreen[j][1] +1)+ "  Distance: " + closestGreen[j][2]);
            }

        }

        //Make Z-projections and save to file close all other windows
        redChannelZproject= ZProjector.run(redChannel,"max");
        redChannelZproject.setTitle("Red Z-project");
        redChannelZproject.show();
        greenChannelZproject= ZProjector.run(greenChannel,"max");
        greenChannelZproject.setTitle("Green Z-project");
        greenChannelZproject.show();
        String[] FinalChannelTitles = WindowManager.getImageTitles();
        makeFolder();
        for(int i =0; i< FinalChannelTitles.length;i++){
            if(FinalChannelTitles[i].equals("Red")||FinalChannelTitles[i].equals("Green")
            ||FinalChannelTitles[i].equals("Red Z-project") || FinalChannelTitles[i].equals("Green Z-project")){
                ImagePlus saveImage = WindowManager.getImage(FinalChannelTitles[i]);
                String CreateName = Paths.get( directory , filename,FinalChannelTitles[i]).toString();
                IJ.saveAs(saveImage, "Tiff", CreateName);
            }else{
                WindowManager.getImage(FinalChannelTitles[i]).close();
            }
        }

        //Make and save results file
        makeResultsFile(closestGreen, xyzGreen, xyzRed);
        roiManager.close();

    }

    //Takes a String array of open window names and greates a NonBlocking Dialog to select which window is Green, Red and
    // to set a channel prominence threshold for the ImageJ 'Find Maxima..' function.
    // Returns an array String[Green Channel window, Red Channel Window]
    public String[] channelSelector(String[] filenameList){
        GenericDialog channelDialog = new NonBlockingGenericDialog("Channel Selector");
        int n = filenameList.length;

        IJ.log(filenameList.toString());
        String[] coloursArray = new String[]{"Green","Red"};
        if(filenameList.length<2){
            IJ.log("Not enough channels open!");
        }
        for (int j = 0; j < 2; j++) {
            channelDialog.addChoice(coloursArray[j], filenameList, filenameList[j]);
        }
        channelDialog.addNumericField("Green Channel Prominence: ", 500 );
        channelDialog.addNumericField("Red Channel Prominence: ", 500 );
        channelDialog.showDialog();
        String[] choicesArray = new String[3];
        for (int i = 0; i < 2; i++){
            choicesArray[i] =  channelDialog.getNextChoice();
        }
        greenTolerance= channelDialog.getNextNumber();
        redTolerance = channelDialog.getNextNumber();
        return choicesArray;
    }

    //Takes an ImagePlus z-stack and double[n][2] of x-y positions. Returns a double[n][3] with the brightest z position
    // for each x-y.
    private double[][] findZPositions(ImagePlus channel, double[][] xyPositions) {

        double[][] zPositions = new double[xyPositions.length][3];

        for (int i = 0; i < xyPositions.length; i++) {
            double maxIntensity = 0;
            zPositions[i][0] = xyPositions[i][0];
            zPositions[i][1] = xyPositions[i][1];
            for (int j = 0; j < channel.getNSlices(); j++) {
                channel.setSlice(j);
                PointRoi point = new PointRoi(xyPositions[i][0], xyPositions[i][1]);
                channel.setRoi(point);
                double intensity = channel.getStatistics().max;
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                    zPositions[i][2] = j;
                }

            }
        }


        return zPositions;
    }

    //Takes two double[][3] arrays (green and red) of xyz coordinates and returns a double[nRed][nGreen] array of the
    // distances between each green and red position
    private double[][] findDistances(double[][] xyzGreen, double[][] xyzRed) {
        double[][] distances = new double[xyzRed.length][xyzGreen.length];
        for (int i = 0; i < xyzRed.length; i++) {
            for (int j = 0; j < xyzGreen.length; j++) {
                double xDist = (xyzRed[i][0] - xyzGreen[j][0]) * pixelWidth;
                double yDist = (xyzRed[i][1] - xyzGreen[j][1]) * pixelHeight;
                double zDist = (xyzRed[i][2] - xyzGreen[j][2]) * pixelDepth;
                distances[i][j] = Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
            }
        }

        return distances;

    }

    //Takes a double[nRed][nGreen] array of distances and for each Red finds the smallest distance, returns an array
    // double[nRed][Red index, Green index, smallest distance]
    private double[][] findNearest(double[][] distances) {
        double[][] outputArray = new double[distances.length][3];
        for (int i = 0; i < distances.length; i++) {
            outputArray[i][0] = i;
            outputArray[i][1] = 0;
            outputArray[i][2] = Double.MAX_VALUE;
            for (int j = 0; j < distances[i].length; j++) {
                if (distances[i][j] < outputArray[i][2]) {
                    outputArray[i][1] = j;
                    outputArray[i][2] = distances[i][j];
                }
            }
        }
        return outputArray;
    }

    //Takes an integer of the index of the spot, an Image plus z-stack and xyz integers. Draws Counter on the image at
    //the position specified.
    public void setImageNumbersSlices(int Counter, ImagePlus ProjectedWindow, int xID,int yID, int zID){
        IJ.setForegroundColor(255, 255, 255);
        ImageProcessor ip = ProjectedWindow.getProcessor();
        Font font = new Font("SansSerif", Font.BOLD, 20);
        ip.setFont(font);
        ip.setColor(new Color(255, 255, 255, 255));
        String cellnumber = String.valueOf(Counter);
        int xpos = (int) xID;
        int ypos = (int) yID;
        int zpos = (int) zID;
        ProjectedWindow.setSlice(zpos);
        ip.drawString(cellnumber, xpos, ypos);
        ProjectedWindow.updateAndDraw();
    }

    //Makes a folder called 'filename' in the current directory
    private void makeFolder(){

        File tmpDir = new File(filename);
        String filePath = Paths.get(directory, filename).toString();
        //If the folder already exists gives options to overwrite or use directory to select a different folder.
        if (tmpDir.exists()){
            return;

        }else {
            new File(filePath).mkdir();
        }
    }

    //Creates a text file _Results.txt saves in current directory with filename, pixel size, tolerances, Number of Red
    // and Green spots found and 3D distances of nearest neighbours (< 3 microns).
    private void makeResultsFile(double[][] closestGreen, double[][] xyzGreen, double[][] xyzRed){

        String CreateName = Paths.get( directory , filename+"_Results.txt").toString();
        File resultsFile = new File(CreateName);


        int i = 1;
        while (resultsFile.exists()){
            CreateName= Paths.get( directory , filename+"_Results_"+i+".txt").toString();
            resultsFile = new File(CreateName);
            IJ.log(CreateName);
            i++;
        }
        try{
            FileWriter fileWriter = new FileWriter(CreateName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write("File= " + filename);
            bufferedWriter.newLine();
            bufferedWriter.write("Pixel size (x/y/z): "+pixelWidth+" "+pixelHeight+" "+pixelDepth );
            bufferedWriter.newLine();
            bufferedWriter.write("Red Tolerance: "+ redTolerance+ " Green Tolerance: "+ greenTolerance);
            bufferedWriter.newLine();
            bufferedWriter.write("Number of Red Points Found: "+ xyzRed.length);
            bufferedWriter.newLine();
            bufferedWriter.write("Number of Green Points Found: "+ xyzGreen.length);
            bufferedWriter.newLine();
            bufferedWriter.write("3D distances (filtered to <3 microns) ");
            bufferedWriter.newLine();
            for(int j=0; j<closestGreen.length;j++) {
                if(closestGreen[j][2]<3) {
                    bufferedWriter.write("Red:  " + " " + (closestGreen[j][0] + 1) + "  Green: " + (closestGreen[j][1] + 1) + "  Distance: " + closestGreen[j][2]);
                    bufferedWriter.newLine();
                }
            }
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
    }


}

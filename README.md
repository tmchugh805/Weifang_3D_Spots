# Weifang_3D_Spots
Image J Plugin which will locate maxima positions in two channel z-stacks and find distances between them. Takes a 2 (or more) colour image file software as input. Asks the user to select a green and a red channel and set thresholds for spot detection. Suitable thresholds should be determined by the user before use using the ImageJ Analyze->'Find Maxima..' function. Spots are detected in each channel on a Z-projection using 'Find Maxima...' function and the z position is taken as the slice with the highest intensity at the x/y coordinates. 3D Distances between each spot in the green channel and every spot in the red channel are calculated and the nearest neighbour (<3 microns) is found. Results are saved in a filename_Results.txt file and z projections of the channels with located spots numbered are also saved.

INSTALLATION

Save the Weifang_Plugin_3D_Spots.class in the plugins folder of your ImageJ installation. ImageJ should be version 1.5 or later and should have BioFormats installed. Weifang_Plugin_3D_Spots should appear in the Plugins menu.

USAGE

Before using the plugin use Analyze->'Find Maxima..' on a Z-projection of each channel to determine suitable thresholds
Select Weifang_Plugin_3D_Spots from the Plugins drop down menu, when prompted select file image file to be opened
When the Bioformats menu appears only the split channels box should be selected
Select the green and red channels from the drop down menu and enter your predetermined threshold values, click OK.
The plugin will run, a text file 'filename_Results.txt' and a 2 image files will save in your image directory.

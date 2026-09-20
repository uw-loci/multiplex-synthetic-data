/*
 * 01_build_project.groovy -- create a QuPath project from the dataset images and
 * run watershed cell detection on each image.
 *
 * Run (first arg = project dir to create; remaining args = image paths):
 *   QuPath script 01_build_project.groovy --args PROJ --args tme_00.tif --args tme_01.tif ...
 *
 * ==== USER-EDITABLE PARAMETERS (cell detection) ==========================
 */
DETECTION_CHANNEL    = "DAPI"   // channel holding the nuclei (this dataset's channel 0)
PIXEL_SIZE_UM        = 0.5      // detection resolution; the dataset is 0.5 um/pixel
BACKGROUND_RADIUS_UM = 0.0      // 0 = OFF. DAPI has no background; a nonzero radius
                                //   smaller than the largest nucleus (~12 um) hollows
                                //   out and DROPS the biggest nuclei. Keep at 0.
MEDIAN_RADIUS_UM     = 0.0      // median pre-filter radius (0 = off)
SIGMA_UM             = 1.5      // Gaussian smoothing before watershed
MIN_AREA_UM2         = 8.0      // discard nuclei smaller than this
MAX_AREA_UM2         = 1000.0   // discard nuclei larger than this
THRESHOLD            = 50.0     // nucleus intensity threshold (DAPI ~240 on positive nuclei)
CELL_EXPANSION_UM    = 5.0      // grow each nucleus outward into a "cell" by this much
INCLUDE_NUCLEI       = true
SMOOTH_BOUNDARIES    = true
MAKE_MEASUREMENTS    = true     // REQUIRED: produces the per-channel means used downstream
// ========================================================================

import qupath.lib.projects.Projects
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.images.ImageData
import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import qupath.lib.scripting.QP
import groovy.json.JsonOutput
import java.awt.image.BufferedImage

def projDir = new File(args[0])
if (projDir.exists()) projDir.deleteDir()   // recreated fresh each run -- pass a dedicated dir
projDir.mkdirs()
def project = Projects.createProject(projDir, BufferedImage.class)

def detJson = JsonOutput.toJson([
    detectionImage: DETECTION_CHANNEL, requestedPixelSizeMicrons: PIXEL_SIZE_UM,
    backgroundRadiusMicrons: BACKGROUND_RADIUS_UM, medianRadiusMicrons: MEDIAN_RADIUS_UM,
    sigmaMicrons: SIGMA_UM, minAreaMicrons: MIN_AREA_UM2, maxAreaMicrons: MAX_AREA_UM2,
    threshold: THRESHOLD, watershedPostProcess: true, cellExpansionMicrons: CELL_EXPANSION_UM,
    includeNuclei: INCLUDE_NUCLEI, smoothBoundaries: SMOOTH_BOUNDARIES, makeMeasurements: MAKE_MEASUREMENTS,
])

for (int i = 1; i < args.size(); i++) {
    def path = args[i]
    def support = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class, path)
    def entry = project.addImage(support.builders.get(0))
    def imageData = entry.readImageData()
    imageData.setImageType(ImageData.ImageType.FLUORESCENCE)
    entry.setImageName(new File(path).getName())
    def server = imageData.getServer()
    def roi = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane())
    def ann = PathObjects.createAnnotationObject(roi)
    imageData.getHierarchy().addObject(ann)
    imageData.getHierarchy().getSelectionModel().setSelectedObject(ann)
    QP.runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', imageData, detJson)
    entry.saveImageData(imageData)
    println "IMG ${entry.getImageName()} cells=${imageData.getHierarchy().getDetectionObjects().size()}"
}
project.syncChanges()
println "PROJECT_SAVED " + projDir.getAbsolutePath()

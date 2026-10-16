/*
 * generate_cell_detections.groovy -- (re)generate the cell detections for the
 * demo project, on the CURRENT image. This is exactly how the detections shipped
 * in this project were produced. Open an image and Run; or Run > Run for project
 * to (re)do all 8. Detections are also already present, so you only need this if
 * you want to reproduce or tweak them.
 *
 * The one parameter that matters: BACKGROUND_RADIUS_UM = 0. DAPI has no
 * background; a nonzero radius hollows out and drops the largest nuclei.
 *
 * ==== USER-EDITABLE PARAMETERS (cell detection) ==========================
 */
DETECTION_CHANNEL    = "DAPI"
PIXEL_SIZE_UM        = 0.5
BACKGROUND_RADIUS_UM = 0.0
MEDIAN_RADIUS_UM     = 0.0
SIGMA_UM             = 1.5
MIN_AREA_UM2         = 8.0
MAX_AREA_UM2         = 1000.0
THRESHOLD            = 50.0
CELL_EXPANSION_UM    = 5.0
INCLUDE_NUCLEI       = true
SMOOTH_BOUNDARIES    = true
MAKE_MEASUREMENTS    = true
// ========================================================================

import static qupath.lib.gui.scripting.QPEx.*
import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import groovy.json.JsonOutput

setImageType('FLUORESCENCE')
def imageData = getCurrentImageData()
def server = imageData.getServer()

// Fresh detections; keep annotations (the full-image region + the ground-truth points).
clearDetections()
def region = getAnnotationObjects().find { it.getROI() != null && it.getROI().isArea() }
if (region == null) {
    region = PathObjects.createAnnotationObject(
        ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane()))
    addObject(region)
}
selectObjects(region)

def detJson = JsonOutput.toJson([
    detectionImage: DETECTION_CHANNEL, requestedPixelSizeMicrons: PIXEL_SIZE_UM,
    backgroundRadiusMicrons: BACKGROUND_RADIUS_UM, medianRadiusMicrons: MEDIAN_RADIUS_UM,
    sigmaMicrons: SIGMA_UM, minAreaMicrons: MIN_AREA_UM2, maxAreaMicrons: MAX_AREA_UM2,
    threshold: THRESHOLD, watershedPostProcess: true, cellExpansionMicrons: CELL_EXPANSION_UM,
    includeNuclei: INCLUDE_NUCLEI, smoothBoundaries: SMOOTH_BOUNDARIES, makeMeasurements: MAKE_MEASUREMENTS,
])
runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', imageData, detJson)
fireHierarchyUpdate()
println "detections: " + getDetectionObjects().size()

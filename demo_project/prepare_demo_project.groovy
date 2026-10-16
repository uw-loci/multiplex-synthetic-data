/*
 * prepare_demo_project.groovy -- the demo-project step between detection and
 * training: import the ground-truth points and leave every cell UNCLASSIFIED.
 *
 * For every image in a project built by analytical_logs/scripts/01_build_project.groovy:
 *   1. removes any existing point annotations, then imports tme_NN_points.geojson
 *      (the classified ground-truth points, one point per cell);
 *   2. clears every cell's class, so the demo starts from unclassified cells.
 *
 * Run (arg 1 = project dir; arg 2 = folder holding tme_NN_points.geojson):
 *   QuPath script prepare_demo_project.groovy --args PROJ --args ground_truth
 */
import qupath.lib.io.PathIO
import qupath.lib.projects.ProjectIO
import java.awt.image.BufferedImage

def project = ProjectIO.loadProject(new File(args[0], "project.qpproj"), BufferedImage.class)
def gtDir = new File(args[1])

for (entry in project.getImageList()) {
    def imageData = entry.readImageData()
    def hierarchy = imageData.getHierarchy()
    def old = hierarchy.getAnnotationObjects().findAll { it.getROI() != null && it.getROI().isPoint() }
    if (!old.isEmpty()) hierarchy.removeObjects(old, true)
    def stem = entry.getImageName().replaceAll(/\.tif+$/, "")
    def gj = new File(gtDir, "${stem}_points.geojson")
    if (!gj.isFile()) { println "ERROR no points file ${gj}"; continue }
    def objs = PathIO.readObjects(gj)
    hierarchy.addObjects(objs)
    def cells = hierarchy.getCellObjects()
    cells.each { it.setPathClass(null) }
    entry.saveImageData(imageData)
    println "PREP ${entry.getImageName()} points=${objs.size()} cells=${cells.size()} " +
            "metadata=${entry.getMetadata().keySet().findAll { it.startsWith('synth.') }.size()}"
}
project.syncChanges()
println "PREPARED " + args[0]

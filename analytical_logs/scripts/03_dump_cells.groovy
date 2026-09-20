/*
 * 03_dump_cells.groovy -- one row per detected cell: position, the clustering
 * PathClass (Cluster N, from step 2), and per-cell marker means in the Cell,
 * Cytoplasm, and Nucleus compartments.
 *
 * Cytoplasmic markers live in the CYTOPLASM (the nucleus is a dark hole), so
 * classification (step 4) gates on the Cytoplasm compartment; nuclear Ki67 uses
 * the Nucleus. Both are dumped here so you can compare.
 *
 * Run (arg 1 = project dir from step 1; arg 2 = output CSV path):
 *   QuPath script 03_dump_cells.groovy --args PROJ --args cells.csv
 *
 * ==== USER-EDITABLE PARAMETERS ==========================================
 */
MARKERS = ["PanCK", "Ki67", "aSMA", "CD3", "CD8", "CD20", "CD68"]  // markers to dump
COMPARTMENTS = ["Cell", "Cytoplasm", "Nucleus"]                    // QuPath measurement compartments
// ========================================================================

import qupath.lib.projects.ProjectIO
import java.awt.image.BufferedImage

def project = ProjectIO.loadProject(new File(args[0], "project.qpproj"), BufferedImage.class)
def cols = []
for (c in COMPARTMENTS) for (m in MARKERS) cols << "${c}_${m}"
new File(args[1]).withWriter { w ->
    w.println("image,x_px,y_px,cluster," + cols.join(","))
    for (entry in project.getImageList()) {
        def imageData = entry.readImageData()
        def name = entry.getImageName()
        for (cell in imageData.getHierarchy().getDetectionObjects()) {
            def roi = cell.getROI()
            def ml = cell.getMeasurementList()
            def pc = cell.getPathClass()
            def clu = (pc != null && pc.toString().startsWith("Cluster")) ? pc.toString() : ""
            def vals = []
            for (c in COMPARTMENTS) for (m in MARKERS) {
                def v = ml.get("${c}: ${m} mean")
                vals << ((v == null || Double.isNaN(v)) ? "" : String.format("%.2f", v))
            }
            w.println("${name},${String.format('%.1f', roi.getCentroidX())},${String.format('%.1f', roi.getCentroidY())},${clu}," + vals.join(","))
        }
    }
}
println "DUMPED " + args[1]

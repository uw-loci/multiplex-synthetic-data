/*
 * 06_classify_for_confusion_matrix.groovy -- prepare a project for the QuPath
 * Confusion Matrix extension (https://github.com/kgallik/QuPath_Confusion_Matrix_Extension).
 *
 * It does two things to every image in a project built by 01_build_project.groovy:
 *
 *   1. PREDICTS a cell type for each detected cell with a deliberately imperfect
 *      classifier -- a single-marker Otsu gate (the same rule set 04_score.py
 *      scores). This is the "classifier under test": it is right ~97-98% of the
 *      time, and its mistakes are real (cell-expansion spillover at nest
 *      boundaries), so the confusion matrix has genuine off-diagonal cells to
 *      inspect. The predicted class is written as the cell's PathClass, using the
 *      SAME class names as the ground-truth points (tumor, fibroblast, cd8_t,
 *      helper_t, b_cell, macrophage) so the extension can line them up.
 *
 *   2. IMPORTS the shipped ground-truth points (tme_NN_points.geojson) as
 *      classified point annotations -- these are the "actual" labels the extension
 *      compares the predictions against.
 *
 * Then, in QuPath: Extensions > Confusion Matrix > Analyze Current Image... (or
 * Analyze Project...). Click any OFF-DIAGONAL matrix cell to select those
 * misclassified cells in the viewer and jump straight to where the classifier
 * got it wrong.
 *
 * To demo a BETTER classifier for contrast, re-run with GATING_COMPARTMENT =
 * "Cytoplasm" (cleaner than "Cell": the nucleus is a dark hole and the 5 um cell
 * expansion picks up neighbours), or train QuPath's object classifier on the
 * imported points and compare its matrix to this one.
 *
 * Run (arg 1 = project dir from step 1; arg 2 = dir holding tme_NN_points.geojson,
 * e.g. the unzipped release folder):
 *   QuPath script 06_classify_for_confusion_matrix.groovy --args PROJ --args DATA_DIR
 *
 * ==== USER-EDITABLE PARAMETERS ==========================================
 */
// Compartment whose marker means the gate reads. "Cell" is the QuPath-standard
// measurement and shows the most (boundary-spillover) errors; "Cytoplasm" is
// cleaner and scores a bit higher. Ki67 is nuclear and is not used for gating.
GATING_COMPARTMENT = "Cell"
// Import the ground-truth points as annotations too (needed by the extension).
// Set false if you have already imported them.
IMPORT_GROUND_TRUTH_POINTS = true
// Remove any existing point annotations before importing, so re-runs do not stack.
REPLACE_EXISTING_POINTS = true
// Display colors (R,G,B) for the predicted classes; match the point colors.
TYPE_COLORS = [
    "tumor":      [220, 20, 60],  "fibroblast": [160, 110, 40],
    "cd8_t":      [255, 0, 255],  "helper_t":   [0, 180, 0],
    "b_cell":     [60, 130, 255], "macrophage": [255, 140, 0],
]
// Markers used by the gate (Ki67 is nuclear-only and excluded from gating).
GATE_MARKERS = ["PanCK", "aSMA", "CD3", "CD8", "CD20", "CD68"]
// ========================================================================

import qupath.lib.projects.ProjectIO
import qupath.lib.objects.classes.PathClass
import qupath.lib.io.PathIO
import qupath.lib.common.ColorTools
import java.awt.image.BufferedImage

// Otsu threshold on a 256-bin histogram, clipped at the 99.5th percentile
// (matches otsu() in 04_score.py so predictions reproduce the scored numbers).
double otsuThreshold(double[] vals) {
    if (vals.length == 0) return 0.0
    double[] sorted = vals.clone(); Arrays.sort(sorted)
    double hi = sorted[(int) Math.min(sorted.length - 1, Math.round(0.995 * (sorted.length - 1)))]
    if (hi <= 0) hi = 1.0
    int B = 256
    double[] p = new double[B]
    double tot = vals.length
    for (double v : vals) {
        double c = Math.max(0.0, Math.min(hi, v))
        int b = (int) Math.min(B - 1, Math.floor(c / hi * B))
        p[b] += 1.0 / tot
    }
    double mt = 0.0
    for (int i = 0; i < B; i++) mt += (i + 0.5) * p[i]
    double w = 0.0, mu = 0.0, best = -1.0
    int k = 0
    for (int i = 0; i < B; i++) {
        w += p[i]; mu += (i + 0.5) * p[i]
        double denom = w * (1.0 - w)
        if (denom <= 0) continue
        double sb = Math.pow(mt * w - mu, 2) / denom
        if (sb > best) { best = sb; k = i }
    }
    return (k + 1.0) / B * hi
}

def project = ProjectIO.loadProject(new File(args[0], "project.qpproj"), BufferedImage.class)
def dataDir = args.length > 1 ? new File(args[1]) : new File(args[0]).getParentFile()

// pre-build PathClasses with colors
def pathClassOf = [:]
for (e in TYPE_COLORS) {
    def pc = PathClass.fromString(e.key)
    try { pc.setColor(ColorTools.packRGB(e.value[0], e.value[1], e.value[2])) } catch (ignored) {}
    pathClassOf[e.key] = pc
}

for (entry in project.getImageList()) {
    def imageData = entry.readImageData()
    def hierarchy = imageData.getHierarchy()
    def cells = new ArrayList<>(hierarchy.getCellObjects())
    if (cells.isEmpty()) { println "SKIP ${entry.getImageName()} (no cells)"; continue }

    // per-marker Otsu thresholds over this image
    def thr = [:]
    for (m in GATE_MARKERS) {
        def vals = cells.collect { c ->
            def v = c.getMeasurementList().get("${GATING_COMPARTMENT}: ${m} mean")
            (v == null || Double.isNaN(v)) ? 0.0 : v
        } as double[]
        thr[m] = otsuThreshold(vals)
    }

    // first-match gating (later rules override earlier -- identical order to 04_score.py)
    int nClassified = 0
    for (cell in cells) {
        def ml = cell.getMeasurementList()
        def pos = [:]
        for (m in GATE_MARKERS) {
            def v = ml.get("${GATING_COMPARTMENT}: ${m} mean")
            pos[m] = (v != null && !Double.isNaN(v) && v >= thr[m])
        }
        String pred = null
        if (pos["aSMA"]) pred = "fibroblast"
        if (pos["CD68"]) pred = "macrophage"
        if (pos["CD20"]) pred = "b_cell"
        if (pos["CD3"] && pos["CD8"]) pred = "cd8_t"
        if (pos["CD3"] && !pos["CD8"]) pred = "helper_t"
        if (pos["PanCK"]) pred = "tumor"
        if (pred != null) { cell.setPathClass(pathClassOf[pred]); nClassified++ }
        else cell.setPathClass(null)
    }

    // import ground-truth points
    int nPoints = 0
    if (IMPORT_GROUND_TRUTH_POINTS) {
        def stem = entry.getImageName().replaceAll(/\.tif+$/, "")
        def gj = new File(dataDir, "${stem}_points.geojson")
        if (gj.exists()) {
            if (REPLACE_EXISTING_POINTS) {
                def old = hierarchy.getAnnotationObjects().findAll { it.getROI() != null && it.getROI().isPoint() }
                if (!old.isEmpty()) hierarchy.removeObjects(old, true)
            }
            def objs = PathIO.readObjects(gj)
            hierarchy.addObjects(objs)
            nPoints = objs.size()
        } else {
            println "  (no points geojson at ${gj.getPath()})"
        }
    }

    entry.saveImageData(imageData)
    def thrStr = GATE_MARKERS.collect { "${it}=${String.format('%.1f', thr[it])}" }.join(", ")
    println "${entry.getImageName()}: predicted ${nClassified}/${cells.size()} cells, " +
            "${nPoints} GT point features imported  [${GATING_COMPARTMENT} Otsu: ${thrStr}]"
}
project.syncChanges()
println "DONE. Open an image, then Extensions > Confusion Matrix > Analyze Current Image..."

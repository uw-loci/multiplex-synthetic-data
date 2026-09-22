/*
 * 08_apply_otsu_gate.groovy -- the deliberately imperfect classifier, as a script
 * you can run straight from QuPath's Script Editor.
 *
 * This is the single-marker Otsu gate from 06_classify_for_confusion_matrix.groovy,
 * rewritten to run on the CURRENT image with no command-line arguments, so it works
 * inside the bundled demo project: open an image and Run, or Run > Run for project
 * to do all eight.
 *
 * Unlike 06 it does NOT import the ground-truth points -- the demo project already
 * ships them as classified point annotations.
 *
 * It is right about 97-98% of the time, and its mistakes are real: the 5 um cell
 * expansion picks up PanCK from neighbouring tumour cytoplasm, so T cells sitting
 * at the edge of a tumour nest get called tumor. A few cells match no rule at all
 * and are left unclassified. Both are the point: they give you something to find
 * with the Confusion Matrix, and something to repair with Classify Object Subset.
 *
 * ==== USER-EDITABLE PARAMETERS ==========================================
 */
// Compartment whose marker means the gate reads. "Cell" is the QuPath-standard
// measurement and shows the most boundary-spillover errors; "Cytoplasm" is cleaner.
GATING_COMPARTMENT = "Cell"
// Markers used by the gate (Ki67 is nuclear-only and excluded from gating).
GATE_MARKERS = ["PanCK", "aSMA", "CD3", "CD8", "CD20", "CD68"]
// Display colors (R,G,B) for the predicted classes; match the ground-truth points.
TYPE_COLORS = [
    "tumor":      [220, 20, 60],  "fibroblast": [160, 110, 40],
    "cd8_t":      [255, 0, 255],  "helper_t":   [0, 180, 0],
    "b_cell":     [60, 130, 255], "macrophage": [255, 140, 0],
]
// ========================================================================

import qupath.lib.objects.classes.PathClass
import qupath.lib.common.ColorTools
import static qupath.lib.gui.scripting.QPEx.*

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

def pathClassOf = [:]
for (e in TYPE_COLORS) {
    def pc = PathClass.fromString(e.key)
    try { pc.setColor(ColorTools.packRGB(e.value[0], e.value[1], e.value[2])) } catch (ignored) {}
    pathClassOf[e.key] = pc
}

def cells = new ArrayList<>(getCellObjects())
if (cells.isEmpty()) {
    println "No cells in this image -- nothing to do."
    return
}

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
fireHierarchyUpdate()

def thrStr = GATE_MARKERS.collect { "${it}=${String.format('%.1f', thr[it])}" }.join(", ")
println "predicted ${nClassified}/${cells.size()} cells, ${cells.size() - nClassified} left unclassified"
println "[${GATING_COMPARTMENT} Otsu thresholds: ${thrStr}]"
println "Next: Extensions > Confusion Matrix > Analyze Current Image..."

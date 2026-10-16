/*
 * classify_with_marker_gate.groovy -- classify the CURRENT image's cells with a
 * deliberately imperfect single-marker Otsu gate (the "classifier under test").
 * Unlike the bundled trained classifier (near-perfect on this clean data), this
 * gate makes real, interpretable mistakes -- mostly T cells read as tumor from
 * PanCK spillover at nest boundaries -- so it is the better one for demonstrating
 * WHERE a classifier goes wrong. Open an image and Run, then run
 * check_against_ground_truth.groovy to score it and highlight the errors.
 *
 * ==== USER-EDITABLE PARAMETERS ==========================================
 */
GATING_COMPARTMENT = "Cell"   // "Cell" (more boundary errors) or "Cytoplasm" (cleaner)
GATE_MARKERS = ["PanCK", "CD31", "CD3", "CD8", "CD20", "CD68"]
TYPE_COLORS = [
    "tumor":[220,20,60], "fibroblast":[160,110,40], "endothelial":[150,80,220], "cd8_t":[255,0,255],
    "helper_t":[0,180,0], "b_cell":[60,130,255], "macrophage":[255,140,0],
]
// ========================================================================
import static qupath.lib.gui.scripting.QPEx.*
import qupath.lib.objects.classes.PathClass
import qupath.lib.common.ColorTools

double otsuThreshold(double[] vals) {
    if (vals.length == 0) return 0.0
    double[] s = vals.clone(); Arrays.sort(s)
    double hi = s[(int) Math.min(s.length - 1, Math.round(0.995 * (s.length - 1)))]
    if (hi <= 0) hi = 1.0
    int B = 256; double[] p = new double[B]; double tot = vals.length
    for (double v : vals) { double c = Math.max(0.0, Math.min(hi, v)); p[(int) Math.min(B - 1, Math.floor(c / hi * B))] += 1.0 / tot }
    double mt = 0.0; for (int i = 0; i < B; i++) mt += (i + 0.5) * p[i]
    double wv = 0.0, mu = 0.0, best = -1.0; int k = 0
    for (int i = 0; i < B; i++) { wv += p[i]; mu += (i + 0.5) * p[i]; double d = wv * (1.0 - wv); if (d <= 0) continue; double sb = Math.pow(mt * wv - mu, 2) / d; if (sb > best) { best = sb; k = i } }
    return (k + 1.0) / B * hi
}

def cells = getCellObjects()
if (cells.isEmpty()) { println "No cells -- run cell detection first."; return }
def pcOf = [:]
TYPE_COLORS.each { n, c -> def pc = PathClass.fromString(n); try { pc.setColor(ColorTools.packRGB(c[0], c[1], c[2])) } catch (ignored) {}; pcOf[n] = pc }

def thr = [:]
for (m in GATE_MARKERS) {
    def vals = cells.collect { def v = it.getMeasurementList().get("${GATING_COMPARTMENT}: ${m} mean"); (v == null || Double.isNaN(v)) ? 0.0d : v } as double[]
    thr[m] = otsuThreshold(vals)
}
int n = 0
for (cell in cells) {
    def ml = cell.getMeasurementList(); def pos = [:]
    for (m in GATE_MARKERS) { def v = ml.get("${GATING_COMPARTMENT}: ${m} mean"); pos[m] = (v != null && !Double.isNaN(v) && v >= thr[m]) }
    String pred = "fibroblast"   // fibroblasts carry no marker: negative for every gate
    if (pos["CD31"]) pred = "endothelial"
    if (pos["CD68"]) pred = "macrophage"
    if (pos["CD20"]) pred = "b_cell"
    if (pos["CD3"] && pos["CD8"]) pred = "cd8_t"
    if (pos["CD3"] && !pos["CD8"]) pred = "helper_t"
    if (pos["PanCK"]) pred = "tumor"
    cell.setPathClass(pred == null ? null : pcOf[pred]); if (pred != null) n++
}
fireHierarchyUpdate()
println "marker-gate (${GATING_COMPARTMENT}) classified ${n}/${cells.size()} cells. Now run check_against_ground_truth.groovy."

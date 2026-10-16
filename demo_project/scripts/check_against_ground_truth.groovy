/*
 * check_against_ground_truth.groovy -- score the CURRENT image's cell
 * classifications against the ground-truth points, using ONLY core QuPath (no
 * extensions). It prints a text confusion matrix + overall accuracy to the log,
 * and SELECTS the misclassified cells in the viewer so you can see exactly where
 * the classifier got it wrong. Open an image, classify the cells (see
 * apply_trained_classifier.groovy or classify_with_marker_gate.groovy), then Run.
 *
 * This is the public-tools equivalent of the Confusion Matrix extension demo:
 * the extension gives the same matrix interactively, but it is not publicly
 * installable yet -- this script needs nothing but QuPath.
 */
import static qupath.lib.gui.scripting.QPEx.*
import qupath.lib.objects.PathObjectTools

def CLASSES = ["tumor", "fibroblast", "endothelial", "cd8_t", "helper_t", "b_cell", "macrophage"]
def idx = [:]; CLASSES.eachWithIndex { c, i -> idx[c] = i }
def COLS = CLASSES + ["unclassified"]

def hierarchy = getCurrentHierarchy()
def points = hierarchy.getAnnotationObjects().findAll { it.getROI() != null && it.getROI().isPoint() }
if (points.isEmpty()) { println "No ground-truth point annotations found -- nothing to score."; return }

int[][] mat = new int[CLASSES.size()][COLS.size()]
def wrong = []
int correct = 0, total = 0
for (ann in points) {
    def truth = ann.getPathClass()?.toString()
    if (!idx.containsKey(truth)) continue
    for (p in ann.getROI().getAllPoints()) {
        def cell = PathObjectTools.getObjectsForLocation(hierarchy, p.getX(), p.getY(), 0, 0, -1).find { it.isCell() }
        if (cell == null) continue
        def pred = cell.getPathClass()?.toString()
        int predCol = idx.containsKey(pred) ? idx[pred] : CLASSES.size()  // last col = unclassified/other
        mat[idx[truth]][predCol]++
        total++
        if (pred == truth) correct++ else wrong << cell
    }
}
if (total == 0) { println "No ground-truth points landed inside a cell."; return }

// print the confusion matrix (rows = actual, cols = predicted)
def w = COLS.collect { Math.max(it.length(), 5) }
def pad = { s, n -> s.toString().padLeft(n) }
println "\nConfusion matrix (rows = actual / ground truth, cols = predicted) -- ${getProjectEntry()?.getImageName() ?: 'current image'}"
println "actual\\pred".padRight(12) + COLS.withIndex().collect { c, i -> pad(c, w[i] + 2) }.join("")
CLASSES.eachWithIndex { a, r ->
    def row = (0..<COLS.size()).collect { c -> pad(mat[r][c], w[c] + 2) }.join("")
    println a.padRight(12) + row + "   (n=${mat[r].sum()})"
}
println "\noverall accuracy: ${String.format('%.4f', correct / (double) total)}  (${correct}/${total})"
println "misclassified:    ${wrong.size()}"

// highlight the errors in the viewer
selectObjects(wrong)
println "Selected ${wrong.size()} misclassified cells -- they are now highlighted in the viewer."

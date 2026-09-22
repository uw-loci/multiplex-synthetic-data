/*
 * 07_train_object_classifier.groovy -- train a QuPath object classifier (RTrees)
 * from the ground-truth points and save it as a standard object-classifier JSON,
 * the kind you load via Classify > Object classification > Load object classifier.
 *
 * This is how the classifier shipped in the demo project was produced. It labels
 * each cell by the ground-truth point inside it (import the tme_NN_points.geojson
 * first, e.g. with 06_classify_for_confusion_matrix.groovy), then trains on the
 * marker means. Unlike the single-marker Otsu gate (06), a multivariate classifier
 * is robust to cell-expansion spillover and recovers this clean data near-perfectly.
 *
 * Run (arg 1 = project dir with detections + imported GT points; arg 2 = output
 * .json path, e.g. PROJ/classifiers/object_classifiers/cell_type_classifier.json):
 *   QuPath script 07_train_object_classifier.groovy --args PROJ --args OUT.json
 *
 * ==== USER-EDITABLE PARAMETERS ==========================================
 */
// Feature measurements (must match those on the detections). Ki67 is nuclear.
MEAS = ["Cell: PanCK mean", "Nucleus: Ki67 mean", "Cell: aSMA mean",
        "Cell: CD3 mean", "Cell: CD8 mean", "Cell: CD20 mean", "Cell: CD68 mean"]
// Class names (must match the ground-truth point classes).
CLASSES = ["tumor", "fibroblast", "cd8_t", "helper_t", "b_cell", "macrophage"]
// ========================================================================

import qupath.lib.projects.ProjectIO
import qupath.lib.objects.PathObjectTools
import qupath.lib.objects.PathObjectFilter
import qupath.lib.objects.classes.PathClass
import qupath.lib.classifiers.object.ObjectClassifiers
import qupath.opencv.ml.OpenCVClassifiers
import qupath.opencv.ml.objects.OpenCVMLClassifier
import qupath.opencv.ml.objects.features.FeatureExtractors
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_ml.RTrees
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.IntIndexer
import java.awt.image.BufferedImage
import java.nio.file.Paths

def project = ProjectIO.loadProject(new File(args[0], "project.qpproj"), BufferedImage.class)
def clsIndex = [:]; CLASSES.eachWithIndex { c, i -> clsIndex[c] = i }

// Label each cell by the ground-truth point that falls inside it.
def featRows = []; def labels = []
for (entry in project.getImageList()) {
    def hierarchy = entry.readImageData().getHierarchy()
    for (ann in hierarchy.getAnnotationObjects().findAll { it.getROI() != null && it.getROI().isPoint() }) {
        def name = ann.getPathClass()?.toString()
        if (!clsIndex.containsKey(name)) continue
        for (p in ann.getROI().getAllPoints()) {
            def cell = PathObjectTools.getObjectsForLocation(hierarchy, p.getX(), p.getY(), 0, 0, -1).find { it.isCell() }
            if (cell == null) continue
            def ml = cell.getMeasurementList()
            featRows << MEAS.collect { def v = ml.get(it); (v == null || Double.isNaN(v)) ? 0.0f : (float) v }
            labels << clsIndex[name]
        }
    }
}
println "training rows: ${featRows.size()}"

int n = featRows.size(); int k = MEAS.size()
def feat = new Mat(n, k, opencv_core.CV_32FC1)
def resp = new Mat(n, 1, opencv_core.CV_32SC1)
FloatIndexer fi = feat.createIndexer(); IntIndexer ri = resp.createIndexer()
for (int i = 0; i < n; i++) {
    for (int j = 0; j < k; j++) fi.put(i, j, featRows[i][j])
    ri.put(i, 0, labels[i])
}
fi.release(); ri.release()

def statModel = OpenCVClassifiers.createStatModel(RTrees.class)
statModel.train(statModel.createTrainData(feat, resp, null, false))
def fe = FeatureExtractors.createMeasurementListFeatureExtractor(MEAS)
def classifier = OpenCVMLClassifier.create(
    statModel, PathObjectFilter.CELLS, fe, CLASSES.collect { PathClass.fromString(it) })

def outPath = Paths.get(args[1])
outPath.getParent().toFile().mkdirs()
ObjectClassifiers.writeClassifier(classifier, outPath)
println "WROTE ${outPath}  (trained=${statModel.isTrained()})"

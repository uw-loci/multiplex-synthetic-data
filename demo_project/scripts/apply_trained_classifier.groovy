/*
 * apply_trained_classifier.groovy -- classify the cells in the CURRENT image with
 * the bundled object classifier (classifiers/object_classifiers/cell_type_classifier.json),
 * an RTrees model trained on all 8 images' ground-truth labels using the 7 marker
 * means. Open an image and Run; or Run > Run for project to classify all 8.
 *
 * After this, the cells carry a predicted cell-type class. Run
 * check_against_ground_truth.groovy to compare the predictions against the
 * ground-truth points and see where (if anywhere) it errs.
 */
import static qupath.lib.gui.scripting.QPEx.*

def classifier = loadObjectClassifier("cell_type_classifier")
classifier.classifyObjects(getCurrentImageData(), true)
fireHierarchyUpdate()
def n = getCellObjects().count { it.getPathClass() != null }
println "classified ${n} / ${getCellObjects().size()} cells"

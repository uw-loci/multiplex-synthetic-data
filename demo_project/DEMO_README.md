# Multiplex synthetic data -- QuPath demo project

A QuPath project for the [multiplex synthetic dataset](https://github.com/uw-loci/multiplex-synthetic-data),
set up to demo cell classification and classifier validation. The images are
bundled in `images/`; because a QuPath project stores absolute image paths, you
point it at that folder **once** after unzipping (step 1 below) -- then everything
works. **The whole demo runs with core QuPath alone; no extra extensions are
required.**

## What's inside

```
project.qpproj                 open this in QuPath (File > Open Project)
images/                        the 8 tme_*.tif images (referenced relatively)
data/                          per-image data: cell DETECTIONS (unclassified) +
                                 ground-truth POINT annotations (classified by type)
classifiers/object_classifiers/
  cell_type_classifier.json    a trained RTrees object classifier (7 marker means,
                                 7 cell types), trained on all 8 images' ground truth
scripts/                       Automate > Project scripts:
  fix_image_paths.groovy            point the project at the bundled images/ (run once, step 1)
  generate_cell_detections.groovy   how the detections were produced (reproduce/tweak)
  apply_trained_classifier.groovy   classify cells with the bundled trained classifier
  classify_with_marker_gate.groovy  classify with an imperfect single-marker gate (makes errors)
  check_against_ground_truth.groovy PUBLIC scoring: prints a confusion matrix + highlights errors
```

The cells ship **unclassified**; the classified **point annotations are the ground
truth** (one per cell, coloured by type) -- that's the "cell class annotations".

**Every image says what was varied.** Each project entry carries `synth.*`
metadata (shown in the Project tab, and in any metadata browser): `synth.note`
is a one-line summary such as *"variant image; marker intensity x0.80 vs reference
(-20%)"*, and the other keys give the same facts one per field
(`synth.intensity_scale`, `synth.layout`, `synth.role`, ...). Images with
`synth.role = reference` had nothing varied.

## The demo (core QuPath only, ~3 minutes)

1. **Open the project + point it at the images (once).** File > Open Project... >
   `project.qpproj`. A QuPath project stores absolute image paths, so the images
   first show as missing. Fix them once, either way:
   - **Automate > Project scripts > `fix_image_paths` > Run**, then reopen the
     project (this repoints every image to the bundled `images/` folder); or
   - if QuPath offers to locate the missing images, point it at the `images/`
     folder next to `project.qpproj` (fixing one fixes all).

   Then double-click an image (e.g. `tme_00`): you'll see the cell detections and
   the coloured ground-truth points.
2. **Classify the cells.** Automate > Project scripts, then Run one of:
   - `apply_trained_classifier` -- the bundled RTrees classifier (near-perfect on
     this clean data), or
   - `classify_with_marker_gate` -- a deliberately imperfect single-marker gate, so
     there are real, interpretable mistakes to look at.

   (You can also use Classify > Object classification > Load object classifier >
   `cell_type_classifier` > Apply.)
3. **Score + see the errors.** Run `check_against_ground_truth`. It prints a
   **confusion matrix and overall accuracy to the log** (View > Show log) and
   **selects the misclassified cells in the viewer**, so you can jump straight to
   where the classifier was wrong (with the gate, mostly T cells read as tumor from
   PanCK spillover at nest boundaries, and the marker-negative necrotic tumor cells
   in nest centres read as fibroblasts). No extension needed.

Use **Run > Run for project** on any of these scripts to do all 8 images at once.

## Optional: Confusion Matrix extension

If you have the QuPath **Confusion Matrix** extension, you can instead get the same
comparison as an interactive matrix -- click a matrix cell to select those objects
in the viewer. That is how the matrix is shown in the workshop. **Note: that
extension is not publicly installable at this time**, so the `check_against_ground_truth`
script above is the way to reproduce the result yourself today.

## Notes

- **Detection was done on DAPI with background radius 0** (a nonzero radius hollows
  out the largest nuclei). See `generate_cell_detections.groovy`.
- The images are ImageJ-hyperstack TIFFs with embedded channel names + colours, so
  the 8-plex displays correctly with no manual setup.
- Fibroblasts carry no marker in this dataset (v2.0 and later), so the gate calls a
  cell "fibroblast" when it is negative for every marker.
- Full dataset docs, ground-truth CSVs, and analysis logs are in the main release:
  https://github.com/uw-loci/multiplex-synthetic-data

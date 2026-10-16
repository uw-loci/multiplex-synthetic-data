# Analytical logs

Reproducible **clustering**, **classification**, and **spatial-neighborhood**
analysis of this dataset (v2.0) with [QP-CAT](https://github.com/uw-loci/qupath-extension-cell-analysis-tools),
plus the exact scripts so you can re-run and score it yourself against the
per-cell ground truth. Every version number is in **[metadata.txt](metadata.txt)**.
(The v1.2 logs used by the BINA / I2K 2026 workshop are at the
[v1.2 tag](https://github.com/uw-loci/multiplex-synthetic-data/tree/v1.2/analytical_logs).)

## Results summary (v2.0)

- **Detection**: 11,423 cells detected vs 11,424 in the ground truth; no image is
  off by more than 3 cells (`logs/detection.log`).
- **Clustering** (QP-CAT Leiden, 7 marker means): cluster **purity 0.9993**, ARI
  0.9979 after merging sub-clusters to their dominant type; every cell type is
  captured at 0.998 or better. Fibroblasts, which carry no marker, form the
  all-negative clusters.
- **Classification**: a **multivariate** classifier (nearest class-centroid on the
  6 lineage markers, 50/50 train/test) reaches **0.994**. A **single-marker Otsu gate**
  reaches 0.983 (Cytoplasm) / 0.982 (Cell). Its main error is CD8 T cells read as
  tumor from PanCK spillover at nest boundaries (71 of 1,336 on Cytoplasm, 117 on
  Cell). The 36 necrotic tumor cells carry no marker and are called fibroblast by
  any marker-based method; excluding them the gate scores 0.984 / 0.983.
- **Neighborhood enrichment** (tme_00) matches the designed niches: B<->B (z 57),
  endothelial<->endothelial (z 52) and tumor<->tumor (z 12) strongly positive,
  tumor<->fibroblast negative (z -8), tumor<->CD8-T positive (z +3.2) while
  tumor<->helper-T is negative (z -3.2).

## Files

```
metadata.txt                   every version: dataset, QP-CAT, QuPath, Python libs
logs/
  detection.log                QuPath cell-detection counts per image
  clustering_run.log           key lines from the QP-CAT batch run (env + per-image + spatial)
  clustering.log               clustering scored vs ground truth
  classification.log           classification scored vs ground truth (Otsu gate + multivariate)
  confusion_matrix.csv         Otsu-gate confusion matrix (actual x predicted), Cell + Cytoplasm
  neighborhood_enrichment.csv  type x type neighborhood z-scores
scripts/
  01_build_project.groovy      create a QuPath project from the images + run cell detection,
                               and copy each image's synth.* variation metadata into the project
  02_cluster.yaml              QP-CAT headless batch config (clustering + spatial stats)
  03_dump_cells.groovy         dump per-cell cluster label + Cell/Cytoplasm/Nucleus marker means
  04_score.py                  score clustering + classification + confusion + neighborhood matrix
  05_make_points_geojson.py    build QuPath-importable classified points (to train a classifier)
  06_classify_for_confusion_matrix.groovy   predict cell types + import GT points, for the
                                            Confusion Matrix extension demo
  07_train_object_classifier.groovy         train an RTrees object classifier from the GT
                                            points -> object-classifier JSON (as in the demo project)
  08_apply_otsu_gate.groovy                 the same Otsu gate as 06, on the CURRENT image, no args
```

The demo project itself is built by [`../demo_project/build_demo_project.sh`](../demo_project/).

A **ready-to-open QuPath demo project** (bundled images, unclassified detections,
classified ground-truth points, and the trained `cell_type_classifier`) is
attached to each release as `multiplex-synthetic-data-demo-project-*.zip` -- open
it, apply the classifier, and run `check_against_ground_truth` with no setup.

Each release also ships a `tme_NN_points.geojson` per image: the ground truth as
QuPath point objects, one per cell, classified by cell type. Import it
(File > Import objects, or `PathIO.readObjects`) to overlay the truth or to train
QuPath's object classifier / seed QP-CAT's autoencoder labels.

## Validating a classifier (confusion matrix)

Because this dataset ships exact per-cell ground truth as classified points, you
can score any classification and see *where* a classifier goes wrong. Two ways,
both showing the same thing:

- **Core QuPath (no extensions).** The demo-project script
  `check_against_ground_truth.groovy` prints an actual-vs-predicted confusion
  matrix + accuracy to the log and selects the misclassified cells in the viewer.
  `logs/confusion_matrix.csv` here is the same matrix computed offline.
- **Confusion Matrix extension (optional).** Gives the matrix interactively -- click
  an off-diagonal cell to jump to those cells. This is how it's shown in the
  workshop, but the extension is **not publicly installable at this time**, so use
  the script above to reproduce it yourself today.

Setup (after building a project with `01_build_project.groovy`):

```bash
# Predict cell types with an imperfect single-marker Otsu gate AND import the
# shipped ground-truth points (second arg = the ground_truth/ folder holding the
# tme_NN_points.geojson files).
$QP script scripts/06_classify_for_confusion_matrix.groovy --args PROJ --args ground_truth
```

Then score it -- run `check_against_ground_truth.groovy` (core QuPath), or the
Confusion Matrix extension if you have it. The gate is deliberately imperfect
(98.2% on the Cell compartment), and its errors are the *real* ones this dataset
was built to expose:

- **PanCK spillover at nest boundaries** mislabels CD8 T cells as `tumor` -- the
  `cd8_t -> tumor` off-diagonal cell (117 on Cell, 71 on Cytoplasm): T cells
  sitting right against a tumor nest.
- **Dim / below-threshold** cells are negative for every marker and so fall to
  `fibroblast`, the one marker-negative type. That includes the 36 necrotic tumor
  cells, which carry no marker at all.

`logs/confusion_matrix.csv` holds this matrix (Cell and Cytoplasm compartments) so
you can see the expected numbers without any GUI. For contrast, a *better*
classifier -- `GATING_COMPARTMENT = "Cytoplasm"`, or the trained object classifier
(`07_train_object_classifier.groovy`) -- has a far cleaner diagonal; the offline
multivariate classifier in `logs/classification.log` scores 0.994.

## How to reproduce

Requires **QuPath 0.7+** with the **QP-CAT** extension installed and its Python
environment set up. Unzip a dataset release, then (paths are examples):

```bash
QP=/path/to/QuPath            # the QuPath launcher
BATCH=<qpcat-scripts>/batch/qpcat_batch.groovy   # ships inside the QP-CAT jar

# 1. Build a project and detect cells (DAPI, background radius 0)
$QP script scripts/01_build_project.groovy --args PROJ --args images/tme_00.tif --args images/tme_01.tif ... 

# 2. Cluster + spatial stats (edit scripts/02_cluster.yaml: set <PROJECT_DIR> to PROJ)
$QP script "$BATCH" --args scripts/02_cluster.yaml

# 3. Dump per-cell cluster label + marker means
$QP script scripts/03_dump_cells.groovy --args PROJ --args cells.csv

# 4. Score against the ground truth shipped in the release
python3 scripts/04_score.py --cells cells.csv --groundtruth ground_truth/all_groundtruth.csv \
    --result-json PROJ/qpcat/cluster_results/yaml_tme_00.tif.json --outdir logs/
```

Notes:
- **Detection uses background radius 0** -- DAPI has no background, and a nonzero
  radius drops the largest nuclei (see the dataset INSTRUCTIONS).
- **Classification here is rule-based marker gating** (Otsu threshold per marker),
  which mirrors QP-CAT's rule-based phenotyping. QP-CAT's autoencoder classifier
  is an interactive (GUI) feature and is not scripted here.
- `04_score.py` needs only `numpy` and `scipy`.

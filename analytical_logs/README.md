# Analytical logs

Reproducible **clustering**, **classification**, and **spatial-neighborhood**
analysis of this dataset (v1.2) with [QP-CAT](https://github.com/uw-loci/qupath-extension-cell-analysis-tools),
plus the exact scripts so you can re-run and score it yourself against the
per-cell ground truth. Every version number is in **[metadata.txt](metadata.txt)**.

## Results summary (v1.2)

- **Clustering** (QP-CAT Leiden, 7 marker means): cluster **purity 0.9998**; every
  cell type captured ~100%. (Leiden over-clusters into many *pure* sub-types, so
  purity -- not raw ARI on the sub-clusters -- is the recovery metric; ARI after
  merging sub-clusters to their dominant type is reported too.)
- **Classification**: a proper **multivariate** classifier (nearest class-centroid on
  the 6-lineage-marker vector, 50/50 train/test) reaches **0.996 accuracy** -- the data
  is fully classifiable. A naive **single-marker Otsu gate** reaches only ~0.970 (Cell) /
  0.968 (Cytoplasm): thresholding one marker at a time is brittle to cell-expansion
  spillover in dense tissue, whereas using all markers jointly (multivariate, or
  clustering) is robust. The ground truth itself is **exact** -- every type's markers are
  100% clean -- so the residual is measurement spillover, not a data defect.
- **Neighborhood enrichment** matches the designed niches: tumor<->tumor strongly
  positive, tumor<->fibroblast negative, tumor<->CD8-T positive (nest boundary),
  B<->B positive (follicles).

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
  01_build_project.groovy      create a QuPath project from the images + run cell detection
  02_cluster.yaml              QP-CAT headless batch config (clustering + spatial stats)
  03_dump_cells.groovy         dump per-cell cluster label + Cell/Cytoplasm/Nucleus marker means
  04_score.py                  score clustering + classification + confusion + neighborhood matrix
  05_make_points_geojson.py    build QuPath-importable classified points (to train a classifier)
  06_classify_for_confusion_matrix.groovy   predict cell types + import GT points, for the
                                            Confusion Matrix extension demo
```

Each release also ships a `tme_NN_points.geojson` per image: the ground truth as
QuPath point objects, one per cell, classified by cell type. Import it
(File > Import objects, or `PathIO.readObjects`) to overlay the truth or to train
QuPath's object classifier / seed QP-CAT's autoencoder labels.

## Confusion Matrix extension demo

The [Confusion Matrix extension](https://github.com/kgallik/QuPath_Confusion_Matrix_Extension)
compares a classifier's predictions against ground truth and lets you **click any
off-diagonal matrix cell to jump straight to the misclassified cells in the
viewer** -- so this dataset (which ships exact per-cell ground truth as classified
points) is a natural demo of *where* a classifier goes wrong.

Setup (after building a project with `01_build_project.groovy`):

```bash
# Predict cell types with an imperfect single-marker Otsu gate AND import the
# shipped ground-truth points (DATA_DIR = the unzipped release folder).
$QP script scripts/06_classify_for_confusion_matrix.groovy --args PROJ --args DATA_DIR
```

Then in QuPath: open an image and run **Extensions > Confusion Matrix > Analyze
Current Image...** (or **Analyze Project...** for the aggregate across all 8). The
gate is deliberately imperfect (~97.0% on the Cell compartment), and its errors
are the *real* ones this dataset was built to expose:

- **PanCK spillover at nest boundaries** mislabels some T cells as `tumor` -- the
  `cd8_t -> tumor` and `helper_t -> tumor` off-diagonal cells. Click them and the
  viewer highlights T cells sitting right against a tumor nest.
- **Dim / below-threshold** cells fall to no class (`unknown`), the largest error
  source for a one-marker-at-a-time gate.

`logs/confusion_matrix.csv` is that matrix computed offline (Cell and Cytoplasm
compartments) so you can see the expected numbers without the GUI. To demo a
*better* classifier for contrast, re-run step 6 with `GATING_COMPARTMENT =
"Cytoplasm"`, or train QuPath's object classifier on the imported points -- the
multivariate result (~99.6%) has a far cleaner diagonal.

## How to reproduce

Requires **QuPath 0.7+** with the **QP-CAT** extension installed and its Python
environment set up. Unzip a dataset release, then (paths are examples):

```bash
QP=/path/to/QuPath            # the QuPath launcher
BATCH=<qpcat-scripts>/batch/qpcat_batch.groovy   # ships inside the QP-CAT jar

# 1. Build a project and detect cells (DAPI, background radius 0)
$QP script scripts/01_build_project.groovy --args PROJ --args tme_00.tif --args tme_01.tif ... 

# 2. Cluster + spatial stats (edit scripts/02_cluster.yaml: set <PROJECT_DIR> to PROJ)
$QP script "$BATCH" --args scripts/02_cluster.yaml

# 3. Dump per-cell cluster label + marker means
$QP script scripts/03_dump_cells.groovy --args PROJ --args cells.csv

# 4. Score against the ground truth shipped in the release
python3 scripts/04_score.py --cells cells.csv --groundtruth all_groundtruth.csv \
    --result-json PROJ/qpcat/cluster_results/yaml_tme_00.tif.json --outdir logs/
```

Notes:
- **Detection uses background radius 0** -- DAPI has no background, and a nonzero
  radius drops the largest nuclei (see the dataset INSTRUCTIONS).
- **Classification here is rule-based marker gating** (Otsu threshold per marker),
  which mirrors QP-CAT's rule-based phenotyping. QP-CAT's autoencoder classifier
  is an interactive (GUI) feature and is not scripted here.
- `04_score.py` needs only `numpy` and `scipy`.

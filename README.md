# Multiplex synthetic data

A small, **fully-labelled synthetic multiplexed-imaging dataset** -- a schematic
tissue of several cell types organised into niches -- for learning, testing, and
validating cell **clustering, phenotyping, classification, and
spatial-neighborhood analysis** in [QuPath](https://qupath.github.io/), especially
with the
[QP-CAT extension](https://github.com/uw-loci/qupath-extension-cell-analysis-tools).

![The eight synthetic images as color composites -- white DAPI, cyan PanCK, yellow Ki67, brown aSMA, green CD3, magenta CD8, blue CD20, red CD68. Cyan tumor nests, brown spindle-cell stroma, blue B-cell follicles, and scattered immune cells; the last two images are immune-rich and immune-poor variants.](docs/overview.png)

## Why this exists

Tools that cluster cells, call phenotypes, and measure spatial relationships are
hard to trust on real data, because **real multiplexed tissue has no ground
truth** -- you never actually know which cell is which type, or whether two
populations really co-localize. Real datasets are also large, licensed, and
messy, which makes them a poor way to *learn* a workflow or *check that an
install works*.

This dataset is the opposite: **small, self-contained, and fully ground-truthed.**
Every cell has a known type, known marker positivity, and a known place in the
tissue, so you can run an analysis and check that it recovered the right answer.

It is deliberately built so that **every major analysis has something to
recover**:

- **Clustering / phenotyping / classification** -- six cell types, each defined
  by a distinct marker combination *and* a distinct morphology.
- **Spatial neighborhoods** -- the cells are organized into tissue niches (tumor
  nests, B-cell follicles, an immune-infiltrated nest boundary, and stroma), so
  neighborhood-enrichment, co-occurrence, Ripley, and cellular-neighborhood
  analyses produce clear, correct signals.
- **Batch correction** -- some images carry a deliberate per-image intensity
  offset, so integration methods (e.g. Harmony) have a batch effect to remove.

It is synthetic and makes no claim to biological realism beyond what is needed to
exercise these tools; it is a **test fixture and teaching aid**, not a substitute
for real data. In particular it has **no extracellular matrix / collagen and no
realistic tissue architecture** -- the "nests" and "follicles" are schematic blobs
that exist only to give the spatial analyses something to find. Adding real tissue
structure (e.g. an ECM/collagen channel, more faithful tumor architecture) is a
possible future direction, which is also why the dataset is named for what it is
(multiplex synthetic data) rather than for any specific tissue.

## What's in it

- **8 images**, 8 channels (DAPI + PanCK, Ki67, aSMA, CD3, CD8, CD20, CD68), 2D,
  `uint8`, **0.5 um/pixel**, ImageJ-hyperstack TIFF (opens in QuPath / Fiji with
  channels, pixel size, and channel names intact).
- **6 cell types** (tumor, fibroblast, CD8 T, helper T, B cell, macrophage)
  arranged into tissue niches.
- **Per-cell ground-truth CSV** -- for every cell: type, tissue region, position,
  morphology, and per-marker positivity + intensity.
- **2 variant layouts** (immune-rich and immune-poor) and **3 batch-offset
  images** for batch-correction testing.

![Zoomed features: an aligned band of parallel spindle nuclei (a tissue-edge / palisade), loose non-overlapping stroma with each fibroblast's cytoplasm sparing the nucleus, dendritic CD68 macrophages, a tumor nest with PanCK cytoplasm rings and Ki67-positive nuclei, and a B-cell follicle.](docs/features.png)

Full channel/type/region tables, the QuPath cell-detection recipe, a
feature-by-feature guide to what each analysis should recover, and the
ground-truth CSV column reference are in **[INSTRUCTIONS.md](INSTRUCTIONS.md)**.

## Get the data

Two downloads are attached to the
**[latest release](https://github.com/uw-loci/multiplex-synthetic-data/releases/latest)**
(the data is distributed only as release assets, not stored in the repository):

- **`multiplex-synthetic-data-*.zip`** -- the dataset, with each file type in its
  own folder so you can point QuPath's *Add images* straight at `images/`:
  `images/` (8 TIFFs), `ground_truth/` (per-image + combined CSVs, and
  QuPath-importable classified points), `params/` (per-image generation
  parameters), and `analytical_logs/` (scripts + logs to reproduce the analyses).
- **`multiplex-synthetic-data-demo-project-*.zip`** -- see below.

### Ready-to-run demo project

Prefer to skip setup? The **demo-project** zip is a complete QuPath project with
the images bundled in `images/`. (A QuPath project stores absolute image paths, so
on first open you point it at that folder **once** -- run the included
`fix_image_paths` project script, or use QuPath's locate-missing-images prompt.) It
ships the cell **detections** (unclassified), the **ground-truth points** as
classified annotations, a **trained object classifier** in
`classifiers/object_classifiers/`, and a set of **project scripts**. The whole demo
then runs with **core QuPath alone**:
open it, apply the classifier (Automate > Project scripts >
`apply_trained_classifier`, or the imperfect `classify_with_marker_gate` if you
want visible errors), then run `check_against_ground_truth` -- it prints a
confusion matrix + accuracy to the log and highlights the misclassified cells in
the viewer. (The QuPath **Confusion Matrix** extension gives the same comparison
interactively and is how it's shown in the workshop, but it is not publicly
installable at this time, so the script is the way to reproduce it yourself today.)
Details in the project's `DEMO_README.md`.

## Quick start

1. Download and unzip the latest release (or grab the demo-project zip above to
   skip straight to step 4).
2. In QuPath, create a project and add the images from the `images/` folder (they
   load as 8-channel fluorescence at 0.5 um/pixel).
3. Run **cell detection** on the `DAPI` channel with background radius **0**
   (the channel has no background; a nonzero radius drops the largest nuclei).
4. Run your analysis (clustering, phenotyping, spatial stats) and compare the
   result against the matching `*_groundtruth.csv`.

Step-by-step detail, parameter values, and what to expect from each analysis are
in **[INSTRUCTIONS.md](INSTRUCTIONS.md)**.

## Associated projects

- **[QP-CAT (qupath-extension-cell-analysis-tools)](https://github.com/uw-loci/qupath-extension-cell-analysis-tools)**
  -- the QuPath extension this dataset is primarily built to exercise
  (clustering, phenotyping, autoencoder classification, spatial stats, batch
  correction).
- **[QuPath](https://qupath.github.io/)** -- the host application.

The Python generator that produces this data is maintained separately by
[LOCI](https://eliceirilab.org/) and is **not distributed here** -- this
repository ships the data and its documentation only.

## License

Released under [CC0 1.0](LICENSE) (public domain dedication) -- use it for
anything, no attribution required. A credit to
[LOCI](https://eliceirilab.org/) / the QP-CAT project is appreciated but not
required.

## Provenance

Produced at the [Laboratory for Optical and Computational Instrumentation
(LOCI)](https://eliceirilab.org/), University of Wisconsin-Madison. The data is
entirely synthetic: no patient or animal tissue is involved, and there is no
identifiable information of any kind.

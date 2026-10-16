# Using the multiplex synthetic dataset

This is the detailed guide: what the data contains, how to load and detect cells
in QuPath, what each analysis should recover, and the ground-truth format. For
the short version see [README.md](README.md).

This guide describes **v2.0**. The BINA / I2K 2026 workshop uses **v1.2**, which
has an aSMA channel instead of CD31 and six cell types instead of seven; its guide
is [INSTRUCTIONS.md at v1.2](https://github.com/uw-loci/multiplex-synthetic-data/blob/v1.2/INSTRUCTIONS.md).
What changed is listed in [section 10](#10-changes-from-v12).

---

## 1. Contents of a release

Unzipping a release gives you, with each kind of file in its own folder:

```
INSTRUCTIONS.md                     this file
settings.json                       the generator settings every image was made with
images/
  tme_00.tif ... tme_07.tif         8 images (8-channel, 2D, uint8, 0.5 um/pixel)
ground_truth/
  tme_00_groundtruth.csv ...        per-image per-cell ground truth
  all_groundtruth.csv               every cell from every image, combined
  tme_00_points.geojson ...         QuPath-importable classified points (one per cell, by type)
params/
  tme_00_params.json ...            per-image generation parameters (seed, layout, counts,
                                      and the synth.* variation metadata -- section 7)
analytical_logs/                    scripts + logs to reproduce and score the analyses
```

To build a QuPath project, point **Add images** at the `images/` folder and select
all 8. The `ground_truth/` and `params/` folders are reference data you pull in as
needed (importing the points, scoring a result, or reading what was varied).

The `tme_NN_points.geojson` files are the ground truth as **QuPath point objects**,
one point per cell, classified by cell type. Import a file (File > Import objects,
or `PathIO.readObjects`) to overlay the true labels, or to **train a classifier**:
use the points to label a subset of detections, then train QuPath's object
classifier or seed QP-CAT's autoencoder.

Images are **ImageJ-hyperstack TIFFs**. They open in QuPath and Fiji with the
correct channel count, channel names, and pixel calibration (0.5 um/pixel). Set
the image type to **Fluorescence** if QuPath prompts.

---

## 2. Channels (8)

| # | Channel | Localization | Color | Positive on | Notes |
|---|---------|--------------|-------|-------------|-------|
| 0 | **DAPI** | nuclear | white | all cells | detection channel; no background; textured chromatin |
| 1 | **PanCK** | cytoplasmic | cyan | tumor | epithelial marker; a tumor nest's cytoplasm reads as one continuous sheet |
| 2 | **Ki67** | nuclear | yellow | ~30% of tumor, all mitoses | proliferation; carries a smooth spatial **gradient** across the slide (a signal for Moran's I / Geary's C) |
| 3 | **CD31** | membrane | orange | endothelial | vessel lining |
| 4 | **CD3** | membrane | green | CD8 T + helper T | pan-T-cell |
| 5 | **CD8** | membrane | magenta | CD8 T only | cytotoxic T |
| 6 | **CD20** | membrane | blue | B cell | B-cell marker |
| 7 | **CD68** | cytoplasmic | red | macrophage | myeloid; some cells have an irregular / dendritic cytoplasm |

Channel display colors are embedded in the TIFF (as ImageJ LUTs), so QuPath shows
them without any manual setup.

**No marker is ever drawn over a nucleus.** Cytoplasmic markers (PanCK, CD68) fill
the cell's cytoplasm; membrane markers (CD3, CD8, CD20, CD31) are a bright band at
the cell's outer edge with a faint fill inside it. So a marker's signal lives in
`Cell: <marker> mean` and `Cytoplasm: <marker> mean`; the one nuclear marker
(Ki67) lives in `Nucleus: Ki67 mean`. Markers have **no background** (a positive
cell against black), so each marker is cleanly **bimodal** -- which is what makes
both clustering and threshold gating work.

**Stromal fibroblasts carry no marker.** They show as DAPI-only nuclei with
unstained cytoplasm. A gate therefore identifies them as "negative for every
marker", not by a marker of their own.

---

## 3. Cell types (7)

Each type is distinguished by **both markers and morphology**, so it can be
recovered from measurements (clustering / phenotyping), from image patches
(morphology-based classification), or both.

| Type | Markers | Nucleus morphology | Primary location |
|------|---------|--------------------|------------------|
| **tumor** | PanCK+ (Ki67+ in a subset) | large, round (~18 um), irregular outline | tumor nests |
| **fibroblast** | none | large, **elongated** (spindle) | stroma, aligned bands |
| **endothelial** | CD31+ | small, flattened | vessels |
| **cd8_t** | CD3+ CD8+ | small, round (~10 um) | nest boundary + stroma |
| **helper_t** | CD3+ (CD8-) | small, round | stroma + boundary |
| **b_cell** | CD20+ | small, round | follicles |
| **macrophage** | CD68+ | medium (~13 um); some irregular / dendritic | dispersed, some in vessels |

Note that **CD8 T and helper T differ only by CD8** -- a deliberately subtle
split to test cluster resolution / a two-marker gate.

**Cell states.** Most cells are `viable`. Tumor nests deeper than 75 um have a
**necrotic centre** (the innermost 28% of the nest depth): its cells carry no
markers and their nuclei are `ghost` (full size, faint), `pyknotic` (shrunken,
bright) or `karyorrhectic` (shrunken, mid-bright), and 30% of them are missing
altogether. About 3% of tumor cells are `mitotic`: a condensed, bar-shaped nucleus
with bright Ki67. The `state` column records which.

Necrotic cells keep `cell_type = tumor` but are **negative for every marker**, so
any marker-based method (a gate, clustering, a trained classifier) will put them
with the marker-negative fibroblasts. That is the correct reading of the pixels;
when scoring, either drop rows whose `state` is `ghost` / `pyknotic` /
`karyorrhectic` or count them as expected misses. They are a few dozen cells per
image at most.

**Nuclei look like nuclei.** DAPI carries chromatin texture (a smooth euchromatin
field plus bright heterochromatin spots), each cell has its own brightness, nuclei
have irregular outlines and sit off-centre in their cell, and neighbouring cells
share curved borders. This model is ported from SynOrg (section 9).

---

## 4. Tissue organization (what drives the spatial signals)

Cells are not scattered at random. Each image is built from tissue **regions**:

- **Tumor nests** -- tumor cells tile the whole nest. => tumor is spatially
  *clustered*; strong tumor<->tumor neighborhood self-enrichment; Ripley shows
  clustering.
- **Nest boundary** -- a band just outside each nest, enriched for T cells.
  => tumor<->CD8-T positive enrichment / co-occurrence peak at the nest radius,
  while helper-T (more stromal) is *not* enriched next to tumor.
- **B-cell follicles** -- dense CD20+ aggregates in the stroma. => strong
  B<->B self-enrichment; a distinct "follicle" cellular neighborhood.
- **Vessels** -- 3 to 7 per image, short elongated structures (35-90 um by
  12-26 um) packed with CD31+ endothelial cells (about 10% macrophages).
  => strong endothelial<->endothelial self-enrichment (second only to B<->B)
  and the most tightly clustered type for Ripley.
- **Aligned stroma bands** -- a few elongated regions where fibroblasts are packed
  tightly and oriented **parallel** to the band's long axis, simulating a tissue
  edge or an epithelial-like palisade. Marked `aligned_stroma` in the `region`
  column; the per-cell `angle_deg` clusters around the band orientation there.
- **Stroma** -- fibroblasts between the nests, **loosely and unevenly spaced**,
  denser hugging a nest and thinning into open ECM voids away from it. Dispersed
  macrophages and some T cells fill in. => fibroblasts are spatially *dispersed*;
  tumor<->fibroblast strong *avoidance* (they occupy different regions).

The per-cell `region` / `region_id` columns in the ground truth record which
region each cell came from, so you can check any of the above directly.

---

## 5. Loading + cell detection in QuPath

1. **Create a project** and add the images from the `images/` folder (all 8
   `tme_*.tif`). Confirm each reads as 8 channels at pixel size 0.5 um (Image tab).
2. **Full-image annotation**: select all, or add a rectangle covering the image,
   so detection has a parent region.
3. **Cell detection** (Analyze > Cell detection) on the **DAPI** channel. The one
   parameter that matters most:

   > **Background radius = 0.** DAPI has no background. A nonzero background radius
   > larger than 0 but smaller than the biggest nucleus radius (~12 um) performs a
   > background subtraction that *hollows out* the largest round nuclei and
   > silently drops many of the tumor cells. Set it to 0.

   Other settings that work well: requested pixel size 0.5 um, sigma ~1.5 um,
   minimum area ~8 um^2, maximum area ~1000 um^2, threshold ~50, cell expansion
   ~5 um, include nuclei + make measurements on. With these, detected counts match
   the ground-truth cell count to within 3 cells per image (0.2%; per image: section 7).

4. QuPath will produce per-cell measurements named `Cell: PanCK mean`,
   `Nucleus: Ki67 mean`, `Cell: CD31 mean`, etc. -- these are the features to
   cluster / gate on.

Headless: `analytical_logs/scripts/01_build_project.groovy` builds the project,
runs this detection, and copies each image's variation metadata into the project
(section 7). QP-CAT additionally supports a YAML batch runner.

---

## 6. What each analysis should recover

Match your result against the ground truth to confirm the tool is working.

| Analysis | Recommended features / setup | What you should see |
|----------|------------------------------|---------------------|
| **Clustering** (Leiden / KMeans / ...) | the 7 marker means, z-scored | clusters map cleanly onto the 7 types (graph methods like Leiden over-cluster into pure sub-types; each sub-cluster is a single type). Fibroblasts form the all-negative cluster |
| **Phenotyping** (marker gating) | one threshold per marker, in the valley between 0 and the positive intensity | each type gated by its own marker(s); CD8 T = CD3+ CD8+, helper T = CD3+ CD8-, fibroblast = negative for all |
| **Autoencoder / morphology classification** | image patches (+/- measurements), a labelled subset | types separable by appearance too (large round tumor, spindle fibroblast, small flattened endothelium, small round lymphocytes) |
| **Neighborhood enrichment** | classify cells first, then run on the labels | tumor<->tumor, B<->B and endothelial<->endothelial strongly positive; tumor<->fibroblast strongly negative; tumor<->CD8-T positive |
| **Ripley K/L** | per type | tumor, B and endothelial clustered (nests / follicles / vessels); fibroblast dispersed |
| **Co-occurrence vs distance** | tumor vs CD8-T | conditional CD8-T density peaks near the nest boundary distance |
| **Cellular neighborhoods** | windowed composition + clustering | recurring niches: tumor core, tumor-immune interface, stroma, follicle, vessel |
| **Moran's I / Geary's C** | on `Ki67` | high spatial autocorrelation (Ki67 carries a smooth gradient) |
| **Batch correction** (Harmony) | joint clustering across all 8 images | a cell type clusters together across all images despite the per-image intensity offsets (section 7) |

**Validating a classifier.** Because every cell ships with an exact label as a
classified point (`tme_NN_points.geojson`), you can score any classification
against ground truth. With **core QuPath only**, the demo-project script
`check_against_ground_truth.groovy` prints an actual-vs-predicted confusion matrix
+ accuracy to the log and selects the misclassified cells in the viewer, so you can
see exactly where the classifier failed. A ready-to-run setup and the expected
matrix are in
[`analytical_logs/`](analytical_logs/README.md#validating-a-classifier-confusion-matrix)
and in the demo-project release asset (see its `DEMO_README.md`).

For batch correction specifically: cluster all 8 images **jointly**, once without
and once with correction. With correction, cells of a given type from the offset
images should share clusters with the same type from the other images rather than
splitting off by image.

---

## 7. Image layouts and what was varied (variation metadata)

| Image | Layout | Marker intensity | `synth.note` |
|-------|--------|------------------|--------------|
| tme_00, tme_01, tme_03 | standard | x1.00 | reference image |
| tme_02 | standard | **x0.80** | variant image; marker intensity x0.80 vs reference (-20%) |
| tme_04 | standard | **x1.20** | variant image; marker intensity x1.20 vs reference (+20%) |
| tme_05 | standard | **x0.85** | variant image; marker intensity x0.85 vs reference (-15%) |
| tme_06 | **immune-rich** (more T cells, extra follicles) | x1.00 | variant image; high_immune tissue layout |
| tme_07 | **immune-poor** (few T cells, **no** B-cell follicles) | x1.00 | variant image; low_immune tissue layout |

The intensity scale applies to every marker channel, never to DAPI, so detection
is unaffected.

**Every image says what was varied, in machine-readable form.** Each
`params/tme_NN_params.json` has a `synth` block of `synth.*` keys, and the
project-building scripts (and the demo project) copy those keys into the QuPath
project entry's metadata, where they show in the Project tab and in any
metadata browser:

| Key | Meaning | Values in this release |
|-----|---------|------------------------|
| `synth.role` | `reference` (nothing varied) or `variant` | per image |
| `synth.intensity_scale` | marker-intensity multiplier vs the reference images | 1.00 / 0.80 / 1.20 / 0.85 |
| `synth.intensity_scale_channels` | which channels the scale applies to | `markers` (all but DAPI) |
| `synth.layout` | tissue-composition variant | `standard` / `high_immune` / `low_immune` |
| `synth.noise` | simulated imaging noise | `off` |
| `synth.section_um` | simulated section thickness | `none` (every nucleus cut through its centre) |
| `synth.stain_preset` | stain-appearance preset | `default` |
| `synth.generator` | generator + version | `cell-phantom-creation 0.4.0` |
| `synth.settings` | settings file, relative to the dataset root | `settings.json` |
| `synth.note` | one-line summary, generated from the keys above | see the table above |

The keys and their wording come from one controlled vocabulary, so the same kind
of variation is always described the same way across our synthetic datasets.

Per-image cell counts (ground truth vs QuPath detection with the section 5
settings):

| Image | Ground-truth cells | Detected | Difference |
|-------|-------|-------|-------|
| tme_00 | 1478 | 1476 | -2 |
| tme_01 | 1461 | 1464 | +3 |
| tme_02 | 1489 | 1489 | +0 |
| tme_03 | 1299 | 1298 | -1 |
| tme_04 | 1480 | 1479 | -1 |
| tme_05 | 1511 | 1512 | +1 |
| tme_06 | 1720 | 1719 | -1 |
| tme_07 | 986 | 986 | +0 |
| **total** | **11424** | **11423** | **-1** |

---

## 8. Ground-truth CSV reference

One row per cell. `all_groundtruth.csv` is every image concatenated; each
`tme_NN_groundtruth.csv` is one image.

| Column | Meaning |
|--------|---------|
| `image_name` | source image (`tme_NN`) |
| `cell_id` | id within the image |
| `centroid_x_px`, `centroid_y_px` | nucleus centroid in pixels |
| `centroid_x_um`, `centroid_y_um` | nucleus centroid in microns |
| `cell_type` | one of the 7 types |
| `region` | `nest` / `follicle` / `vessel` / `boundary` / `aligned_stroma` / `stroma` |
| `region_id` | nest/follicle/vessel instance id (0 for boundary/aligned/stroma) |
| `size_mode` | `small` / `medium` / `large` |
| `shape` | `round` / `elliptical` |
| `major_axis_um`, `minor_axis_um`, `equiv_diameter_um` | nucleus size (before outline irregularity) |
| `area_px`, `area_um2` | nucleus area as drawn |
| `eccentricity`, `angle_deg` | nucleus shape / orientation |
| `clipped_at_edge` | whether the nucleus touches the image border (always false in the shipped data) |
| `dapi_brightness` | the cell's peak DAPI level (0-255) |
| `state` | `viable` / `mitotic` / `ghost` / `pyknotic` / `karyorrhectic` |
| `nucleus_in_section` | whether the nucleus is in the image plane (always true here: no section simulation) |
| `section_z_um` | the cut height through the nucleus (0 here) |
| `<marker>_positive` | boolean, per marker (PanCK, Ki67, CD31, CD3, CD8, CD20, CD68) |
| `<marker>_intensity` | designed positive intensity (0 if negative) |

To score a detected result, match each QuPath detection to the nearest
ground-truth centroid (within a few pixels) and compare its predicted
cluster/phenotype to the row's `cell_type`.

---

## 9. Notes, caveats and sources

- **Synthetic, not realistic.** Proportions, morphology, and marker intensities
  are chosen to exercise analysis tools, not to reproduce any specific tumor or
  panel. Treat it as a test fixture. There is no collagen / ECM channel.
- **Where the realism comes from.** Nuclear texture, per-cell brightness,
  irregular nuclei, cell territories, membrane rendering and the necrotic
  phenotypes are ported from **SynOrg** (S. Bhattiprolu, *Parametric
  physics-based synthesis of 3D organoid microscopy*, bioRxiv 2026,
  doi:[10.64898/2026.04.16.719066](https://doi.org/10.64898/2026.04.16.719066);
  code [github.com/bnsreenu/SynOrg](https://github.com/bnsreenu/SynOrg)). SynOrg's
  values are calibrated on 3D organoid confocal data, not on 2D tissue sections,
  so they are a starting point, not a measurement of tissue.
- **ImageJ TIFF, not OME-TIFF.** The data ships as ImageJ hyperstacks because that
  format reads back reliably (channels + names + pixel size) across QuPath/Fiji;
  some readers mis-map multi-channel OME-TIFFs written by scientific-Python tools.
- **No background or noise.** Markers and DAPI are background-free and there is no
  simulated imaging noise (`synth.noise = off`). If your tool expects
  autofluorescence or a nonzero floor, this data will look unusually clean. The
  generator can add both; a release that does will say so in `synth.noise`.

---

## 10. Changes from v1.2

- **CD31 replaces aSMA** (channel 3). aSMA does not mark resting stromal
  fibroblasts, so the scattered fibroblasts now carry **no marker**.
- **New cell type: endothelial** (CD31+), clustered into **vessels**, a new region.
- **Membrane markers.** CD3, CD8, CD20 and CD31 are drawn at the cell membrane
  instead of filling the cytoplasm.
- **PanCK paints together.** A tumor nest's cytoplasm is one continuous PanCK sheet,
  interrupted only where another cell's cytoplasm is.
- **Realistic nuclei** (SynOrg-derived texture, brightness spread, irregular
  outlines), **necrotic nest centres** and **mitoses**; new ground-truth columns
  `dapi_brightness`, `state`, `nucleus_in_section`, `section_z_um`.
- **Variation metadata**: `synth.*` keys in each `_params.json` and on each QuPath
  project entry (section 7).
- Cell counts differ from v1.2; the layout plan (seeds, batch gains, immune-rich
  and immune-poor images) is the same.

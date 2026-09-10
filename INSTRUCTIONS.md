# Using the multiplex synthetic dataset

This is the detailed guide: what the data contains, how to load and detect cells
in QuPath, what each analysis should recover, and the ground-truth format. For
the short version see [README.md](README.md).

---

## 1. Contents of a release

Unzipping a release gives you:

```
tme_00.tif ... tme_07.tif        8 images (8-channel, 2D, uint8, 0.5 um/pixel)
tme_00_groundtruth.csv ...       per-image per-cell ground truth
tme_00_params.json ...           per-image generation parameters (seed, layout, counts)
all_groundtruth.csv              every cell from every image, combined
INSTRUCTIONS.md                  this file
```

Images are **ImageJ-hyperstack TIFFs**. They open in QuPath and Fiji with the
correct channel count, channel names, and pixel calibration (0.5 um/pixel). Set
the image type to **Fluorescence** if QuPath prompts.

---

## 2. Channels (8)

| # | Channel | Localization | Positive on | Notes |
|---|---------|--------------|-------------|-------|
| 0 | **DAPI** | nuclear | all cells | detection channel; no background |
| 1 | **PanCK** | cytoplasmic | tumor | epithelial marker |
| 2 | **Ki67** | nuclear | ~30% of tumor | proliferation; carries a smooth spatial **gradient** across the slide (a signal for Moran's I / Geary's C) |
| 3 | **aSMA** | cytoplasmic | fibroblast | stromal marker; on elongated cells |
| 4 | **CD3** | membrane | CD8 T + helper T | pan-T-cell |
| 5 | **CD8** | membrane | CD8 T only | cytotoxic T |
| 6 | **CD20** | membrane | B cell | B-cell marker |
| 7 | **CD68** | cytoplasmic | macrophage | myeloid marker |

Markers other than DAPI have **no background** (a positive cell against black).
Measured in QuPath, a cell's `Cell: <marker> mean` (or `Nucleus: Ki67 mean` for
the nuclear marker) is high on its type and ~0 elsewhere -- i.e. each marker is
cleanly **bimodal**, which is what makes both clustering and threshold gating work.

---

## 3. Cell types (6)

Each type is distinguished by **both markers and morphology**, so it can be
recovered from measurements (clustering / phenotyping), from image patches
(morphology-based classification), or both.

| Type | Markers | Nucleus morphology | Primary location |
|------|---------|--------------------|------------------|
| **tumor** | PanCK+ (Ki67+ in a subset) | large, round (~18 um) | tumor nests |
| **fibroblast** | aSMA+ | large, **elongated** (spindle) | stroma |
| **cd8_t** | CD3+ CD8+ | small, round (~10 um) | nest boundary + stroma |
| **helper_t** | CD3+ (CD8-) | small, round | stroma + boundary |
| **b_cell** | CD20+ | small, round | follicles |
| **macrophage** | CD68+ | medium, round (~13 um) | dispersed |

Note that **CD8 T and helper T differ only by CD8** -- a deliberately subtle
split to test cluster resolution / a two-marker gate.

---

## 4. Tissue organization (what drives the spatial signals)

Cells are not scattered at random. Each image is built from tissue **regions**:

- **Tumor nests** -- dense blobs of tumor cells. => tumor is spatially
  *clustered*; strong tumor<->tumor neighborhood self-enrichment; Ripley shows
  clustering.
- **Nest boundary** -- a band just outside each nest, enriched for T cells.
  => tumor<->CD8-T positive enrichment / co-occurrence peak at the nest radius,
  while helper-T (more stromal) is *not* enriched next to tumor.
- **B-cell follicles** -- dense CD20+ aggregates in the stroma. => strong
  B<->B self-enrichment; a distinct "follicle" cellular neighborhood.
- **Stroma** -- fibroblasts filling the space between nests, with dispersed
  macrophages and some T cells. => fibroblasts are spatially *dispersed*;
  tumor<->fibroblast strong *avoidance* (they occupy different regions).

The per-cell `region` / `region_id` columns in the ground truth record which
region each cell came from, so you can check any of the above directly.

---

## 5. Loading + cell detection in QuPath

1. **Create a project** and add the `tme_*.tif` images. Confirm each reads as
   8 channels at pixel size 0.5 um (Image tab).
2. **Full-image annotation**: select all, or add a rectangle covering the image,
   so detection has a parent region.
3. **Cell detection** (Analyze > Cell detection) on the **DAPI** channel. The one
   parameter that matters most:

   > **Background radius = 0.** DAPI has no background. A nonzero background radius
   > larger than 0 but smaller than the biggest nucleus radius (~12 um) performs a
   > background subtraction that *hollows out* the largest round nuclei and
   > silently drops ~20% of the tumor cells. Set it to 0.

   Other settings that work well: requested pixel size 0.5 um, sigma ~1.5 um,
   minimum area ~8 um^2, maximum area ~1000 um^2, threshold ~50, cell expansion
   ~5 um, include nuclei + make measurements on. With these, detected counts match
   the ground-truth cell count to within a fraction of a percent, and every
   non-edge cell is detected.

4. QuPath will produce per-cell measurements named `Cell: PanCK mean`,
   `Nucleus: Ki67 mean`, `Cell: CD3 mean`, etc. -- these are the features to
   cluster / gate on.

Headless: the same detection + analysis can be scripted via QuPath's `script`
subcommand; QP-CAT additionally supports a YAML batch runner.

---

## 6. What each analysis should recover

Match your result against the ground truth to confirm the tool is working.

| Analysis | Recommended features / setup | What you should see |
|----------|------------------------------|---------------------|
| **Clustering** (Leiden / KMeans / ...) | the 7 marker means, z-scored | clusters map cleanly onto the 6 types (graph methods like Leiden over-cluster into pure sub-types; each sub-cluster is a single type) |
| **Phenotyping** (marker gating) | one threshold per marker, in the valley between 0 and the positive intensity | each type gated by its own marker(s); CD8 T = CD3+ CD8+, helper T = CD3+ CD8- |
| **Autoencoder / morphology classification** | image patches (+/- measurements), a labelled subset | types separable by appearance too (large round tumor, spindle fibroblast, small lymphocytes) |
| **Neighborhood enrichment** | classify cells first, then run on the labels | tumor<->tumor and B<->B strongly positive; tumor<->fibroblast strongly negative; tumor<->CD8-T positive |
| **Ripley K/L** | per type | tumor + B clustered (nests / follicles); fibroblast dispersed |
| **Co-occurrence vs distance** | tumor vs CD8-T | conditional CD8-T density peaks near the nest boundary distance |
| **Cellular neighborhoods** | windowed composition + clustering | recurring niches: tumor core, tumor-immune interface, stroma, follicle |
| **Moran's I / Geary's C** | on `Ki67` | high spatial autocorrelation (Ki67 carries a smooth gradient) |
| **Batch correction** (Harmony) | joint clustering across all 8 images | a cell type clusters together across all images despite the per-image intensity offsets (see below) |

For batch correction specifically: cluster all 8 images **jointly**, once without
and once with correction. Images `tme_02`, `tme_04`, and `tme_05` carry marker
intensity offsets (roughly x0.8, x1.2, x0.85). With correction, cells of a given
type from the offset images should share clusters with the same type from the
other images rather than splitting off by image.

---

## 7. Image layouts

| Image | Layout | Batch offset |
|-------|--------|--------------|
| tme_00, tme_01, tme_03 | standard | none |
| tme_02 | standard | ~0.80 |
| tme_04 | standard | ~1.20 |
| tme_05 | standard | ~0.85 |
| tme_06 | **immune-rich** (more T cells, extra follicles) | none |
| tme_07 | **immune-poor** (few T cells, **no** B-cell follicles) | none |

The exact seed, layout, per-type counts, and offset for each image are recorded
in that image's `_params.json`.

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
| `cell_type` | one of the 6 types |
| `region` | `nest` / `follicle` / `boundary` / `stroma` |
| `region_id` | nest/follicle instance id (0 for boundary/stroma) |
| `size_mode` | `small` / `medium` / `large` |
| `shape` | `round` / `elliptical` |
| `major_axis_um`, `minor_axis_um`, `equiv_diameter_um` | nucleus size |
| `area_px`, `area_um2` | nucleus area |
| `eccentricity`, `angle_deg` | nucleus shape / orientation |
| `clipped_at_edge` | whether the nucleus touches the image border (always false in the shipped data) |
| `<marker>_positive` | boolean, per marker (PanCK, Ki67, aSMA, CD3, CD8, CD20, CD68) |
| `<marker>_intensity` | designed positive intensity (0 if negative) |

To score a detected result, match each QuPath detection to the nearest
ground-truth centroid (within a few pixels) and compare its predicted
cluster/phenotype to the row's `cell_type`.

---

## 9. Notes and caveats

- **Synthetic, not realistic.** Proportions, morphology, and marker intensities
  are chosen to exercise analysis tools, not to reproduce any specific tumor or
  panel. Treat it as a test fixture.
- **ImageJ TIFF, not OME-TIFF.** The data ships as ImageJ hyperstacks because that
  format reads back reliably (channels + names + pixel size) across QuPath/Fiji;
  some readers mis-map multi-channel OME-TIFFs written by scientific-Python tools.
- **No background on markers** (except DAPI is also background-free). If your tool
  expects autofluorescence or a nonzero floor, this data will look unusually clean.

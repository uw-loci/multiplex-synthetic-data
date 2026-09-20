#!/usr/bin/env python3
"""Score QP-CAT clustering + a rule-based classification against the ground truth.

Reads the per-cell dump from 03_dump_cells.groovy (position, `Cluster N`
PathClass, and per-cell marker means) and the dataset's `all_groundtruth.csv`,
matches each detected cell to the nearest ground-truth cell, and reports:

  1. CLUSTERING (unsupervised, from QP-CAT's Leiden labels): cluster purity,
     adjusted Rand index, per-type capture, and a cluster -> dominant-type table.
  2. CLASSIFICATION (supervised, rule-based marker gating -- the same idea as
     QP-CAT's rule-based phenotyping): an Otsu threshold per marker, first-match
     gating rules, then accuracy / per-type precision-recall / confusion.
  3. NEIGHBORHOOD enrichment (optional): flattens the z-score matrix from a saved
     QP-CAT result JSON, mapping each cluster to its dominant ground-truth type.

Only numpy + scipy are required (no scikit-learn). Usage:

  python3 04_score.py --cells cells.csv --groundtruth all_groundtruth.csv \
      --result-json <project>/qpcat/cluster_results/yaml_tme_00.tif.json \
      --outdir logs/
"""
from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from math import comb
from pathlib import Path

import numpy as np
from scipy.spatial import cKDTree

# ==== USER-EDITABLE PARAMETERS ==========================================
# Ground-truth cell types (must match the `cell_type` column in all_groundtruth.csv).
TYPES = ["tumor", "fibroblast", "cd8_t", "helper_t", "b_cell", "macrophage"]
# Markers dumped by 03_dump_cells.groovy (a "Cell_<marker>" column each).
MARKERS = ["PanCK", "Ki67", "aSMA", "CD3", "CD8", "CD20", "CD68"]
# Max distance (pixels) to match a detected cell to its nearest ground-truth cell.
MATCH_RADIUS_PX = 6.0
# Compartments to gate classification on (a row is scored once per compartment).
# Cytoplasmic markers live in the CYTOPLASM (the nucleus is a dark hole and the
# 5 um "Cell" expansion also picks up neighbours), so Cytoplasm gates cleaner.
GATING_COMPARTMENTS = ["Cytoplasm", "Cell"]
# The gating rules live in classification_report() below; thresholds are computed
# per marker by Otsu (no hardcoded intensity cutoffs).
# ========================================================================


def load_matched(cells_csv, gt_csv):
    """Match each detected cell to the nearest ground-truth cell (per image)."""
    det = defaultdict(list)
    for r in csv.DictReader(open(cells_csv)):
        det[r["image"].replace(".tif", "")].append(r)
    gt = defaultdict(list)
    for r in csv.DictReader(open(gt_csv)):
        gt[r["image_name"]].append(r)
    rows = []
    for img, drows in det.items():
        grows = gt.get(img)
        if not grows:
            continue
        gxy = np.array([[float(g["centroid_x_px"]), float(g["centroid_y_px"])] for g in grows])
        tree = cKDTree(gxy)
        for d in drows:
            dist, gi = tree.query([float(d["x_px"]), float(d["y_px"])], distance_upper_bound=MATCH_RADIUS_PX)
            if not np.isfinite(dist):
                continue
            rows.append((img, d, grows[gi]["cell_type"]))
    return rows


def adjusted_rand(a, b):
    ua = {v: i for i, v in enumerate(sorted(set(a)))}
    ub = {v: i for i, v in enumerate(sorted(set(b)))}
    ct = np.zeros((len(ua), len(ub)), dtype=np.int64)
    for x, y in zip(a, b):
        ct[ua[x], ub[y]] += 1
    sij = sum(comb(int(v), 2) for v in ct.flat)
    sa = sum(comb(int(v), 2) for v in ct.sum(1))
    sb = sum(comb(int(v), 2) for v in ct.sum(0))
    n = comb(int(ct.sum()), 2)
    exp = sa * sb / n
    return (sij - exp) / ((sa + sb) / 2 - exp)


def otsu(vals):
    """Otsu's threshold on a 256-bin histogram (GT-agnostic)."""
    vals = np.asarray(vals, dtype=np.float64)
    hi = np.percentile(vals, 99.5) or 1.0
    hist, edges = np.histogram(np.clip(vals, 0, hi), bins=256, range=(0, hi))
    p = hist / hist.sum()
    w = np.cumsum(p)
    mu = np.cumsum(p * (np.arange(256) + 0.5))
    mt = mu[-1]
    denom = w * (1 - w)
    denom[denom == 0] = 1e-12
    sigma_b = (mt * w - mu) ** 2 / denom
    k = int(np.argmax(sigma_b))
    return edges[k + 1]


def clustering_report(rows, out):
    lab = [d["cluster"] for _, d, _ in rows if d["cluster"]]
    keep = [(img, d, t) for (img, d, t) in rows if d["cluster"]]
    ytype = [t for _, _, t in keep]
    # (image, cluster) is the unit -- Leiden labels are per-image
    grp = defaultdict(list)
    for img, d, t in keep:
        grp[(img, d["cluster"])].append(t)
    dom = {k: Counter(v).most_common(1)[0][0] for k, v in grp.items()}
    correct = sum(1 for img, d, t in keep if dom[(img, d["cluster"])] == t)
    purity = correct / len(keep)
    # Leiden makes many pure sub-clusters per type; raw ARI(sub-clusters, types)
    # is low purely from that granularity mismatch. The meaningful recovery
    # metric is ARI after merging each sub-cluster to its dominant type.
    mapped = [dom[(img, d["cluster"])] for img, d, t in keep]
    ari = adjusted_rand(mapped, ytype)
    with open(out, "w") as f:
        f.write("CLUSTERING vs ground truth (QP-CAT Leiden labels)\n")
        f.write("=" * 52 + "\n")
        f.write(f"cells scored:        {len(keep)}\n")
        f.write(f"distinct clusters:   {len(grp)} (per-image; Leiden over-clusters into pure sub-types)\n")
        f.write(f"cluster PURITY:      {purity:.4f}  (cell whose cluster's dominant GT type is its own)\n")
        f.write(f"adjusted Rand index: {ari:.4f}  (sub-clusters merged to their dominant type, vs GT type)\n\n")
        f.write("per-type capture (fraction landing in a cluster dominated by that type):\n")
        cap = defaultdict(lambda: [0, 0])
        for img, d, t in keep:
            cap[t][1] += 1
            if dom[(img, d["cluster"])] == t:
                cap[t][0] += 1
        for t in TYPES:
            if cap[t][1]:
                f.write(f"  {t:11s} {cap[t][0]/cap[t][1]:.3f}  (n={cap[t][1]})\n")
    return dom, purity, ari


def gate_predict(rows, comp):
    """Otsu single-marker gate: first-match rules (later rules override), on the
    given compartment. Returns (pred, y_true, thresholds). This is the exact same
    rule set 06_classify_for_confusion_matrix.groovy applies inside QuPath, so the
    confusion matrix here reproduces what the Confusion Matrix extension shows."""
    gm = ["PanCK", "aSMA", "CD3", "CD8", "CD20", "CD68"]
    X = {m: np.array([float(d.get(f"{comp}_{m}") or 0.0) for _, d, _ in rows]) for m in gm}
    yt = np.array([t for _, _, t in rows])
    thr = {m: otsu(X[m]) for m in gm}
    pos = {m: X[m] >= thr[m] for m in thr}
    pred = np.full(len(yt), "unknown", dtype=object)
    # first-match gating (distinct type markers; T cells split by CD8)
    pred[pos["aSMA"]] = "fibroblast"
    pred[pos["CD68"]] = "macrophage"
    pred[pos["CD20"]] = "b_cell"
    pred[pos["CD3"] & pos["CD8"]] = "cd8_t"
    pred[pos["CD3"] & ~pos["CD8"]] = "helper_t"
    pred[pos["PanCK"]] = "tumor"
    return pred, yt, thr


def classification_report(rows, out, comp, mode):
    pred, yt, thr = gate_predict(rows, comp)
    acc = float((pred == yt).mean())
    with open(out, mode) as f:
        f.write(f"CLASSIFICATION vs ground truth (Otsu-gated marker rules) -- {comp} compartment\n")
        f.write("=" * 62 + "\n")
        f.write(f"Otsu thresholds ({comp} mean): " + ", ".join(f"{m}={thr[m]:.1f}" for m in thr) + "\n")
        f.write("gating rules (first match): tumor=PanCK+; b_cell=CD20+; macrophage=CD68+;\n")
        f.write("  cd8_t=CD3+&CD8+; helper_t=CD3+&CD8-; fibroblast=aSMA+\n\n")
        f.write(f"cells scored:      {len(yt)}\n")
        f.write(f"overall accuracy:  {acc:.4f}\n")
        f.write(f"unknown (no gate): {(pred=='unknown').sum()}\n\n")
        f.write("per-type precision / recall / F1:\n")
        for t in TYPES:
            tp = int(((pred == t) & (yt == t)).sum())
            fp = int(((pred == t) & (yt != t)).sum())
            fn = int(((pred != t) & (yt == t)).sum())
            prec = tp / (tp + fp) if tp + fp else 0.0
            rec = tp / (tp + fn) if tp + fn else 0.0
            f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
            f.write(f"  {t:11s} P={prec:.3f} R={rec:.3f} F1={f1:.3f}  (n={int((yt==t).sum())})\n")
        f.write("\n")
    return acc


def multivariate_report(rows, out, comp="Cell", seed=0):
    """A proper (multivariate) classifier -- nearest class-centroid on the full
    marker vector, 50/50 train/test. This is what training a real classifier from
    the labelled points looks like: using all markers jointly is robust to the
    single-marker spillover that limits Otsu gating, so it recovers the clean data
    much more closely."""
    gm = ["PanCK", "aSMA", "CD3", "CD8", "CD20", "CD68"]
    X = np.array([[float(d.get(f"{comp}_{m}") or 0.0) for m in gm] for _, d, _ in rows])
    yt = np.array([t for _, _, t in rows])
    Xz = (X - X.mean(0)) / (X.std(0) + 1e-9)
    rng = np.random.default_rng(seed)
    idx = rng.permutation(len(yt))
    tr, te = idx[:len(yt) // 2], idx[len(yt) // 2:]
    types = sorted(set(yt))
    C = np.array([Xz[tr][yt[tr] == t].mean(0) for t in types])
    pred = np.array([types[i] for i in np.argmin(((Xz[te][:, None] - C[None]) ** 2).sum(2), 1)])
    acc = float((pred == yt[te]).mean())
    with open(out, "a") as f:
        f.write(f"MULTIVARIATE classifier (nearest class-centroid, {len(gm)} {comp} markers, 50/50 train/test)\n")
        f.write("=" * 62 + "\n")
        f.write(f"test cells:    {len(te)}\n")
        f.write(f"test accuracy: {acc:.4f}\n\n")
        f.write("per-type recall (test half):\n")
        for t in types:
            m = yt[te] == t
            f.write(f"  {t:11s} {(pred[m] == t).mean():.3f} (n={int(m.sum())})\n")
        f.write("\n")
    return acc


def confusion_report(rows, out, comps):
    """Write the confusion matrix (actual x predicted) for the Otsu gate, one block
    per compartment. This is the same matrix the QuPath Confusion Matrix extension
    renders when you run 06_classify_for_confusion_matrix.groovy then compare the
    predicted PathClasses to the imported ground-truth points."""
    cols = TYPES + ["unknown"]
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        for ci, comp in enumerate(comps):
            pred, yt, _ = gate_predict(rows, comp)
            acc = float((pred == yt).mean())
            if ci:
                w.writerow([])
            w.writerow([f"# Otsu-gate confusion matrix -- {comp} compartment "
                        f"(accuracy {acc:.4f}, n={len(yt)})"])
            w.writerow(["actual\\predicted"] + cols + ["total"])
            for a in TYPES:
                row = [int(((yt == a) & (pred == p)).sum()) for p in cols]
                w.writerow([a] + row + [int((yt == a).sum())])
            w.writerow(["total"] + [int((pred == p).sum()) for p in cols] + [len(yt)])


def neighborhood_report(result_json, dom, img_name, out):
    if not result_json or not Path(result_json).exists():
        return False
    res = json.load(open(result_json))
    ne = res.get("nhoodEnrichment")
    names = res.get("nhoodClusterNames")
    if not ne or not names:
        return False
    ne = np.array(ne, dtype=float)
    cl2type = {cl.replace("Cluster ", ""): d for (img, cl), d in dom.items() if img == img_name}
    lab = [cl2type.get(str(n), "?") for n in names]
    cell = defaultdict(list)
    for i in range(len(names)):
        for j in range(len(names)):
            if lab[i] in TYPES and lab[j] in TYPES and np.isfinite(ne[i, j]):
                cell[(lab[i], lab[j])].append(ne[i, j])
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["type_a", "type_b", "mean_zscore"])
        for a in TYPES:
            for b in TYPES:
                if cell[(a, b)]:
                    w.writerow([a, b, f"{np.mean(cell[(a,b)]):.2f}"])
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cells", required=True)
    ap.add_argument("--groundtruth", required=True)
    ap.add_argument("--result-json", default=None)
    ap.add_argument("--result-image", default="tme_00", help="image stem for the neighborhood matrix")
    ap.add_argument("--outdir", default="logs")
    a = ap.parse_args()
    outdir = Path(a.outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    rows = load_matched(a.cells, a.groundtruth)
    print(f"matched {len(rows)} detected cells to ground truth")
    dom, purity, ari = clustering_report(rows, outdir / "clustering.log")
    accs = {}
    for i, comp in enumerate(GATING_COMPARTMENTS):
        accs[comp] = classification_report(rows, outdir / "classification.log", comp, "w" if i == 0 else "a")
    mv = multivariate_report(rows, outdir / "classification.log", comp="Cell")
    confusion_report(rows, outdir / "confusion_matrix.csv", GATING_COMPARTMENTS)
    nb = neighborhood_report(a.result_json, dom, a.result_image, outdir / "neighborhood_enrichment.csv")
    print(f"clustering: purity={purity:.4f} ARI={ari:.4f}")
    print("classification (Otsu gate): " + ", ".join(f"{c}={accs[c]:.4f}" for c in accs))
    print(f"classification (multivariate): {mv:.4f}")
    print(f"confusion matrix written: {outdir / 'confusion_matrix.csv'}")
    print(f"neighborhood matrix written: {nb}")
    print(f"logs -> {outdir}")


if __name__ == "__main__":
    main()

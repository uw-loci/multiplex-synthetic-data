#!/usr/bin/env python3
"""Emit a QuPath-importable GeoJSON of classified point objects from the ground
truth: one point per cell, classified by its true cell_type (pixel coords).
  python3 make_points_geojson.py output   # writes output/tme_NN_points.geojson
"""
import csv, json, sys
from pathlib import Path
from collections import defaultdict

# ==== USER-EDITABLE PARAMETERS ==========================================
# Classification display colors (R, G, B, 0-255) per cell type.
TYPE_COLORS = {
    "tumor": [220, 20, 60], "fibroblast": [160, 110, 40], "cd8_t": [255, 0, 255],
    "helper_t": [0, 180, 0], "b_cell": [60, 130, 255], "macrophage": [255, 140, 0],
}
# ========================================================================

def emit(csv_path, out_path):
    pts = defaultdict(list)
    for r in csv.DictReader(open(csv_path)):
        pts[r["cell_type"]].append([round(float(r["centroid_x_px"]), 1),
                                    round(float(r["centroid_y_px"]), 1)])
    feats = []
    for ctype, coords in pts.items():
        feats.append({
            "type": "Feature",
            "geometry": {"type": "MultiPoint", "coordinates": coords},
            "properties": {
                "objectType": "annotation",
                "classification": {"name": ctype,
                                   "color": TYPE_COLORS.get(ctype, [200, 200, 200])},
            },
        })
    json.dump({"type": "FeatureCollection", "features": feats}, open(out_path, "w"))
    return sum(len(c) for c in pts.values()), len(feats)

def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "output")
    for csvf in sorted(out.glob("tme_*_groundtruth.csv")):
        stem = csvf.name.replace("_groundtruth.csv", "")
        n, k = emit(csvf, out / f"{stem}_points.geojson")
        print(f"{stem}_points.geojson: {n} points, {k} classes")

if __name__ == "__main__":
    main()

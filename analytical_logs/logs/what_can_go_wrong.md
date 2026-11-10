ground truth 11313 cells (36 necrotic); matched 11294 of 11316 detections; necrotic matched 28
| Setup | Cells right | Wrong | ARI | Main errors (truth -> called) | Worst images |
|---|---|---|---|---|---|
| KMeans k=7 on all 24 means (includes Ki67, DAPI) | 87.8% | 1383 | 0.795 | helper_t -> fibroblast 1286; helper_t -> cd8_t 42; necrotic -> fibroblast 21 | tme_06 278, tme_05 213 |
| same, k=8 | 99.4% | 73 | 0.986 | necrotic -> fibroblast 24; b_cell -> fibroblast 13; cd8_t -> helper_t 13 | tme_02 27, tme_05 19 |
| k=7 on the 18 lineage-marker means (no Ki67, no DAPI) | 99.2% | 86 | 0.983 | necrotic -> fibroblast 25; helper_t -> fibroblast 21; b_cell -> fibroblast 16 | tme_02 34, tme_05 24 |
| same, each image z-scored on its own | 99.2% | 94 | 0.982 | helper_t -> fibroblast 26; necrotic -> fibroblast 24; cd8_t -> helper_t 21 | tme_06 52, tme_05 10 |
| same, with each image's recorded intensity scale undone | 99.7% | 39 | 0.993 | necrotic -> fibroblast 26; helper_t -> fibroblast 6; b_cell -> fibroblast 3 | tme_00 8, tme_05 8 |
| supervised (random forest, 5-fold), 7 markers | 99.7% | 34 | 0.995 | necrotic -> tumor 13; tumor -> necrotic 12; necrotic -> fibroblast 4 | tme_07 8, tme_00 6 |
| same + nucleus area, DAPI, circularity | 99.9% | 6 | 0.999 | necrotic -> tumor 3; helper_t -> b_cell 1; macrophage -> necrotic 1 | tme_01 1, tme_02 1 |

ground truth 11424 cells (42 necrotic); matched 11412 of 11423 detections; necrotic matched 36
| Setup | Cells right | Wrong | ARI | Main errors (truth -> called) | Worst images |
|---|---|---|---|---|---|
| KMeans k=7 on all 24 means (includes Ki67, DAPI) | 87.2% | 1456 | 0.788 | helper_t -> fibroblast 1343; helper_t -> cd8_t 38; necrotic -> fibroblast 26 | tme_06 325, tme_05 219 |
| same, k=8 | 99.0% | 114 | 0.979 | necrotic -> fibroblast 30; cd8_t -> helper_t 28; b_cell -> fibroblast 18 | tme_02 32, tme_05 25 |
| k=7 on the 18 lineage-marker means (no Ki67, no DAPI) | 98.9% | 128 | 0.976 | necrotic -> fibroblast 30; cd8_t -> helper_t 27; b_cell -> fibroblast 21 | tme_02 43, tme_05 27 |
| same, each image z-scored on its own | 98.7% | 149 | 0.972 | helper_t -> fibroblast 42; necrotic -> fibroblast 33; cd8_t -> helper_t 33 | tme_06 76, tme_03 15 |
| same, with each image's recorded intensity scale undone | 99.3% | 83 | 0.985 | necrotic -> fibroblast 32; cd8_t -> helper_t 19; helper_t -> fibroblast 13 | tme_06 17, tme_04 14 |
| supervised (random forest, 5-fold), 7 markers | 99.6% | 47 | 0.994 | necrotic -> tumor 20; tumor -> necrotic 19; fibroblast -> necrotic 2 | tme_02 8, tme_05 8 |
| same + nucleus area, DAPI, circularity | 99.9% | 8 | 0.999 | necrotic -> tumor 4; helper_t -> cd8_t 2; cd8_t -> helper_t 2 | tme_01 2, tme_06 2 |

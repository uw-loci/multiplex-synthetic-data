#!/usr/bin/env bash
# build_demo_project.sh -- build the demo-project release asset from an unzipped
# dataset release.
#
# Usage:
#   build_demo_project.sh <release_dir> <out_dir> [qupath_launcher]
#
#   release_dir   unzipped dataset release (holds images/, ground_truth/, params/)
#   out_dir       the demo project is built in <out_dir>/multiplex-synthetic-data-demo-project
#                 (copy release_dir under a neutral path first, e.g. /tmp/msd-build/release:
#                 the binary .qpdata files keep the image path it was built from)
#   qupath_launcher  QuPath 0.7 launcher (default: $QP, else `QuPath` on PATH)
#
# Steps: detect cells + copy the synth.* variation metadata into each entry
# (01_build_project.groovy), import the ground-truth points and clear classes
# (prepare_demo_project.groovy), train the RTrees classifier
# (07_train_object_classifier.groovy), add the project scripts and README, then
# rewrite the image URIs to placeholders (file:/images/<name>) that
# scripts/fix_image_paths.groovy repoints on first open -- QuPath has no
# relative-URI support. ASCII only.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REL="$(cd "$1" && pwd)"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
QP_BIN="${3:-${QP:-QuPath}}"
PROJ="$OUT/multiplex-synthetic-data-demo-project"
SCRIPTS="$HERE/../analytical_logs/scripts"

IMAGES=("$REL"/images/tme_*.tif)
[ -f "${IMAGES[0]}" ] || { echo "no images in $REL/images" >&2; exit 1; }

ARGS=(-a "$PROJ")
for im in "${IMAGES[@]}"; do ARGS+=(-a "$im"); done
"$QP_BIN" script "${ARGS[@]}" "$SCRIPTS/01_build_project.groovy" | grep -E "^(IMG|PROJECT_SAVED|ERROR)"
"$QP_BIN" script -a "$PROJ" -a "$REL/ground_truth" "$HERE/prepare_demo_project.groovy" | grep -E "^(PREP|PREPARED|ERROR)"
"$QP_BIN" script -a "$PROJ" -a "$PROJ/classifiers/object_classifiers/cell_type_classifier.json" \
    "$SCRIPTS/07_train_object_classifier.groovy" | grep -E "^(training rows|WROTE)"

mkdir -p "$PROJ/images" "$PROJ/scripts"
cp "${IMAGES[@]}" "$PROJ/images/"
cp "$HERE"/scripts/*.groovy "$PROJ/scripts/"
cp "$HERE/DEMO_README.md" "$PROJ/"

# Placeholder absolute URIs (no build-machine path ships in the project).
python3 - "$PROJ" <<'EOF'
import json, re, sys
from pathlib import Path
proj = Path(sys.argv[1])
pat = re.compile(r'"uri":\s*"file:[^"]*/(tme_\d+\.tif)"')
for f in [proj / "project.qpproj", *proj.glob("data/*/server.json")]:
    s = f.read_text()
    s2, n = pat.subn(lambda m: f'"uri": "file:/images/{m.group(1)}"', s)
    s2 = re.sub(r'"uri":\s*"file:[^"]*/project\.qpproj"', '"uri": "project.qpproj"', s2)
    f.write_text(s2)
    print(f"URI {f.relative_to(proj)}: {n} rewritten")
leftover = [str(f) for f in [proj / "project.qpproj", *proj.rglob("*.json")]
            if "/home/" in f.read_text()]
if leftover:
    sys.exit(f"build-machine path left in: {leftover}")
EOF
# The binary data.qpdata files embed the build-time image path, which no rewrite
# can reach -- so build from a path that names nobody (e.g. /tmp/msd-build).
rm -f "$PROJ/project.qpproj.backup"
if grep -rlaq "/home/\|/Users/\|C:\\\\Users" "$PROJ"; then
    echo "WARNING: a home-directory path is embedded in:" >&2
    grep -rla "/home/\|/Users/\|C:\\\\Users" "$PROJ" >&2
    echo "         rebuild from a neutral path such as /tmp/msd-build" >&2
fi
echo "DEMO_PROJECT $PROJ"

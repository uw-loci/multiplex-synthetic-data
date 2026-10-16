/*
 * fix_image_paths.groovy -- point this project's images at the bundled images/
 * folder, wherever you unzipped it. A QuPath project stores absolute image paths,
 * so after unzipping on a new machine the images read as "missing". Run this once
 * (Automate > Project scripts > fix_image_paths, then Run) and reopen the project;
 * every image will resolve to images/<name> next to project.qpproj.
 */
import static qupath.lib.gui.scripting.QPEx.*

def project = getProject()
if (project == null) { println "Open this project first (File > Open Project)."; return }

def p = project.getPath()   // path to project.qpproj (or the project dir)
def projDir = p.getFileName().toString().toLowerCase().endsWith(".qpproj") ? p.getParent() : p
def imagesDir = projDir.resolve("images")
if (!imagesDir.toFile().isDirectory()) { println "No images/ folder next to the project at ${imagesDir}"; return }

int fixed = 0, missing = 0
for (entry in project.getImageList()) {
    def map = [:]
    for (u in entry.getURIs()) {
        def fname = new File(u.getPath() ?: u.toString()).getName()
        def f = imagesDir.resolve(fname).toFile()
        if (f.exists()) map[u] = f.toURI() else missing++
    }
    if (!map.isEmpty()) { entry.updateURIs(map); fixed++ }
}
project.syncChanges()
println "Fixed image paths for ${fixed} images -> ${imagesDir}" + (missing ? " (${missing} not found)" : "")
println "Now reopen the project (or double-click an image) -- the images will load."

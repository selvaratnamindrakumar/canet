import java.util.zip.ZipFile
import java.nio.file.Files

// ============================================================
//  Maven Nexus Offline Repository Deployer
//  Extracts a repository.zip and uploads every artifact to a
//  Nexus hosted Maven repository via HTTP PUT.
//
//  Parameters (pass as -D flags or set in pom.xml properties):
//    repo.zip.path          – path to repository.zip (required)
//    nexus.url              – base repo URL, e.g.
//                             http://host:8081/repository/maven-releases
//    nexus.username         – Nexus username
//    nexus.password         – Nexus password
//    nexus.upload.checksums – also upload .md5/.sha1 files (default: false)
// ============================================================

// ── Helpers ──────────────────────────────────────────────────

def prop = { String key, String defaultVal ->
    project?.properties?.getProperty(key) ?: System.getProperty(key) ?: defaultVal
}

def repoZipPath     = prop('repo.zip.path',          'repository.zip')
def nexusUrl        = prop('nexus.url',               'http://localhost:8081/repository/maven-releases')
def username        = prop('nexus.username',          '')
def password        = prop('nexus.password',          '')
def uploadChecksums = prop('nexus.upload.checksums',  'false').toBoolean()

// Strip trailing slashes from Nexus URL
nexusUrl = nexusUrl.replaceAll(/\/+$/, '')

// ── Validate inputs ──────────────────────────────────────────

def repoZip = new File(repoZipPath)
if (!repoZip.isAbsolute()) {
    repoZip = new File(project.basedir, repoZipPath)
}
if (!repoZip.exists()) {
    throw new RuntimeException("repository.zip not found: ${repoZip.absolutePath}")
}

log.info '=== Nexus Offline Deployer ==='
log.info "ZIP    : ${repoZip.absolutePath}"
log.info "Nexus  : ${nexusUrl}"
log.info "User   : ${username ?: '(anonymous)'}"

// ── File-skip rules ──────────────────────────────────────────
// These files are skipped BEFORE the regex so they never produce
// "Failed regex match" noise in the log.

// Always skip these filename patterns (Maven bookkeeping files).
def SKIP_NAMES = [
    'maven-metadata.xml',
    'maven-metadata-local.xml',
    'resolver-status.properties',
] as Set

// Always skip these suffixes.
def SKIP_SUFFIXES = [
    '.lastUpdated',
    '_remote.repositories',
] as Set

// Checksum suffixes – conditionally skipped.
def CHECKSUM_SUFFIXES = ['.md5', '.sha1', '.sha256', '.sha512', '.asc'] as Set

def shouldSkip = { File f ->
    def name = f.name
    if (name.startsWith('.'))                      return true  // hidden files
    if (SKIP_NAMES.contains(name))                 return true
    if (SKIP_SUFFIXES.any { name.endsWith(it) })   return true
    if (!uploadChecksums &&
        CHECKSUM_SUFFIXES.any { name.endsWith(it) }) return true
    return false
}

// ── HTTP PUT to Nexus ─────────────────────────────────────────

def uploadFile = { File file, String remotePath ->
    def uploadUrl = "${nexusUrl}/${remotePath}"
    HttpURLConnection conn = null
    try {
        conn = (HttpURLConnection) new URL(uploadUrl).openConnection()
        conn.setRequestMethod('PUT')
        conn.setDoOutput(true)
        conn.setRequestProperty('Content-Type', 'application/octet-stream')
        conn.setRequestProperty('Content-Length', String.valueOf(file.length()))
        if (username) {
            def creds = "${username}:${password}".bytes.encodeBase64().toString()
            conn.setRequestProperty('Authorization', "Basic ${creds}")
        }
        conn.outputStream.withStream { os ->
            file.withInputStream { is -> os << is }
        }
        int status = conn.responseCode
        if (status in [200, 201, 204]) {
            log.info "  UPLOADED [${status}] ${remotePath}"
            return true
        } else {
            def body = conn.errorStream?.text?.trim() ?: '(no body)'
            log.error "  FAILED   [${status}] ${remotePath} — ${body}"
            return false
        }
    } catch (Exception e) {
        log.error "  ERROR    ${remotePath} — ${e.message}"
        return false
    } finally {
        conn?.disconnect()
    }
}

// ── Main logic ────────────────────────────────────────────────

def tempDir = Files.createTempDirectory('nexus-deploy-').toFile()
log.info "Temp dir: ${tempDir}"

try {
    // 1. Extract repository.zip
    new ZipFile(repoZip).withCloseable { zip ->
        zip.entries().each { entry ->
            def out = new File(tempDir, entry.name)
            if (entry.directory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                out.withOutputStream { os -> os << zip.getInputStream(entry) }
            }
        }
    }
    log.info "Extraction complete."

    // 2. Walk files and deploy
    int deployed = 0, failed = 0, skipped = 0

    def baseFolder = tempDir

    baseFolder.eachFileRecurse { File file ->
        if (file.directory) return

        // ── Pre-filter: skip non-artifact files silently ──────────
        if (shouldSkip(file)) {
            log.debug "Skipping (filter): ${file.name}"
            skipped++
            return
        }

        // ── Normalise relative path to forward slashes ────────────
        // Replace OS separator so the regex works on both Linux and Windows.
        def relativePath = file.absolutePath
            .replace(baseFolder.absolutePath, '')
            .replace('\\', '/')

        // ── Regex: expects /<groupId-path>/<artifactId>/<version>/<filename>
        //
        // Fix vs original code:
        //   • All four capture groups now use (.+) instead of (.*)
        //     so empty segments never silently match.
        //   • Checksum / metadata files are already filtered above,
        //     so this log level is DEBUG (not WARN).
        //
        def matcher = (relativePath =~ /\/?(.+)\/(.+)\/(.+)\/(.+)/)
        if (!matcher) {
            log.debug "Skipping (no regex match – not an artifact path): ${relativePath}"
            skipped++
            return
        }

        def groupIdPath = matcher[0][1]  // e.g. "com/example/mygroup"
        def artifactId  = matcher[0][2]  // e.g. "my-lib"
        def version     = matcher[0][3]  // e.g. "1.2.3-SNAPSHOT"
        def fileName    = matcher[0][4]  // e.g. "my-lib-1.2.3.jar"

        // Sanity-check: filename must start with the artifactId
        // (catches stray files that somehow landed in a version directory).
        if (!fileName.startsWith(artifactId)) {
            log.debug "Skipping (filename '${fileName}' does not start with artifactId '${artifactId}'): ${relativePath}"
            skipped++
            return
        }

        // The remote path mirrors the Maven repository layout exactly.
        def remotePath = "${groupIdPath}/${artifactId}/${version}/${fileName}"

        if (uploadFile(file, remotePath)) deployed++ else failed++
    }

    log.info "=== Summary: ${deployed} uploaded | ${failed} failed | ${skipped} skipped ==="
    if (failed > 0) {
        throw new RuntimeException("${failed} artifact(s) failed to upload — see log above.")
    }

} finally {
    if (tempDir.exists()) {
        tempDir.deleteDir()
        log.debug "Cleaned temp dir: ${tempDir}"
    }
}

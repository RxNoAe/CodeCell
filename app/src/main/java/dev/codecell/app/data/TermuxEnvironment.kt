package dev.codecell.app.data

import android.content.Context
import android.system.Os
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.zip.ZipFile

object TermuxEnvironment {

    private const val BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/" +
            "bootstrap-2026.10.25-r1%2Bapt.android-7/bootstrap-aarch64.zip"

    private const val BOOTSTRAP_SHA256 =
        "964e0473a9fa881600927c0151b99896089fb595b81afdcd74b1a6743b8b5889"

    private const val BOOTSTRAP_BYTES = 33_606_000L
    private const val PREFIX = "data/data/com.termux/files/usr"
    private const val GUEST_PREFIX = "/data/data/com.termux/files/usr"
    private const val GUEST_HOME = "/data/data/com.termux/files/home"
    private const val GUEST_BASH = "$GUEST_PREFIX/bin/bash"

    fun root(context: Context): File = File(context.filesDir, "termux")

    fun isInstalled(context: Context): Boolean =
        File(root(context), "$PREFIX/bin/bash").exists() &&
            File(context.filesDir, "termux-shell.sh").isFile

    fun shell(context: Context): File? {
        if (!isInstalled(context)) return null
        val shell = File(context.filesDir, "termux-bin/bash")
        return shell.takeIf { it.exists() }
    }

    fun install(context: Context, onProgress: (Float) -> Unit = {}) {
        val zip = download(context) { onProgress(it * 0.7f) }
        val staging = File(context.filesDir, "termux.new")
        deleteTree(staging)
        extract(context, zip, staging) { onProgress(0.7f + it * 0.28f) }
        val previous = File(context.filesDir, "termux.old")
        val current = root(context)
        if (!current.exists() && previous.exists()) previous.renameTo(current)
        deleteTree(previous)
        if (current.exists() && !current.renameTo(previous)) {
            throw IllegalStateException("Could not replace the current environment")
        }
        if (!staging.renameTo(current)) {
            previous.renameTo(current)
            throw IllegalStateException("Could not finish the environment install")
        }
        deleteTree(previous)
        writeLauncher(context)
        writePackageConfig(context)
        onProgress(1f)
    }

    fun ensureRuntimeConfig(context: Context) {
        if (!isInstalled(context)) return
        writePackageConfig(context)
        writeLauncher(context)
    }

    fun uninstall(context: Context) {
        deleteTree(root(context))
        deleteTree(File(context.filesDir, "termux-libs"))
        deleteTree(File(context.filesDir, "termux-bin"))
        deleteTree(File(context.filesDir, "termux-tmp"))
        File(context.filesDir, "termux-shell.sh").delete()
    }

    private fun download(context: Context, onProgress: (Float) -> Unit): File {
        val target = File(context.cacheDir, "termux-bootstrap.zip")
        if (target.isFile && sha256(target) == BOOTSTRAP_SHA256) {
            onProgress(1f)
            return target
        }
        var current = URL(BOOTSTRAP_URL)
        var connection: HttpURLConnection? = null
        var redirects = 0
        while (redirects < 6) {
            val next = current.openConnection() as HttpURLConnection
            next.instanceFollowRedirects = false
            next.connectTimeout = 20_000
            next.readTimeout = 60_000
            next.setRequestProperty("User-Agent", "CodeCell")
            val code = next.responseCode
            if (code in 300..399) {
                val location = next.getHeaderField("Location")
                next.disconnect()
                if (location == null) break
                current = URL(current, location)
                redirects++
                continue
            }
            connection = next
            break
        }
        val http = connection ?: throw IllegalStateException("Could not open the download")
        if (http.responseCode !in 200..299) {
            throw IllegalStateException("Download failed (HTTP ${http.responseCode})")
        }
        val total = http.contentLengthLong.takeIf { it > 0 } ?: BOOTSTRAP_BYTES
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        target.outputStream().use { output ->
            http.inputStream.use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    read += count
                    onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
        http.disconnect()
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != BOOTSTRAP_SHA256) {
            target.delete()
            throw IllegalStateException("Download checksum failed")
        }
        return target
    }

    private fun extract(
        context: Context,
        zip: File,
        staging: File,
        onProgress: (Float) -> Unit = {},
    ) {
        val prefix = File(staging, PREFIX)
        prefix.mkdirs()
        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            var done = 0
            val total = archive.size().coerceAtLeast(1)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                done++
                val destination = File(prefix, entry.name).canonicalFile
                if (!destination.path.startsWith(prefix.canonicalPath + File.separator)) {
                    continue
                }
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (done % 40 == 0) onProgress(done.toFloat() / total.toFloat())
            }
        }

        val symlinks = File(prefix, "SYMLINKS.txt")
        if (symlinks.isFile) {
            symlinks.forEachLine { line ->
                val split = line.split('←', limit = 2)
                if (split.size != 2) return@forEachLine
                val target = split[0]
                val link = File(prefix, split[1]).canonicalFile
                if (!link.path.startsWith(prefix.canonicalPath + File.separator)) {
                    return@forEachLine
                }
                link.parentFile?.mkdirs()
                runCatching {
                    if (link.exists() || link.isDirectory) link.delete()
                    Os.symlink(target, link.absolutePath)
                }
            }
            symlinks.delete()
        }

        val root = staging
        listOf(
            "data/data/com.termux/files/home",
            "data/data/com.termux/cache/apt/archives/partial",
            "storage/emulated/0",
            "tmp",
            "linkerconfig",
            "dev",
            "proc",
            "sys",
            "system",
            "apex",
            "vendor",
            "sdcard",
        ).forEach { File(root, it).mkdirs() }

        Files.walk(prefix.toPath()).use { paths ->
            paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .forEach { it.toFile().setExecutable(true, false) }
        }
        if (!File(prefix, "bin/bash").canExecute()) {
            throw IllegalStateException("Could not prepare the Linux binaries")
        }
        File(prefix, "etc").mkdirs()
        File(prefix, "etc/resolv.conf").writeText(
            "nameserver 8.8.8.8\nnameserver 1.1.1.1\n",
        )

        val libs = File(context.filesDir, "termux-libs").apply { mkdirs() }
        val talloc = File(context.applicationInfo.nativeLibraryDir, "libtalloc.so")
        if (talloc.isFile) {
            talloc.copyTo(File(libs, "libtalloc.so.2"), overwrite = true)
        }
        File(context.filesDir, "termux-tmp").mkdirs()
        File(root, "tmp").setWritable(true, false)
        File(prefix, "tmp").mkdirs()
    }

    private fun writeLauncher(context: Context) {
        val files = context.filesDir.absolutePath
        val native = context.applicationInfo.nativeLibraryDir
        val script = """
            #!/system/bin/sh
            FILES="$files"
            ROOT="${'$'}FILES/termux"
            NATIVE="$native"
            unset LD_PRELOAD
            unset LD_DEBUG
            export PROOT_LOADER="${'$'}NATIVE/libprootloader.so"
            export PROOT_TMP_DIR="${'$'}FILES/termux-tmp"
            export LD_LIBRARY_PATH="${'$'}FILES/termux-libs:${'$'}NATIVE"
            CWD="${'$'}(pwd)"
            mkdir -p "${'$'}ROOT${'$'}CWD" 2>/dev/null
            LINKER_BIND=""
            if [ -r /linkerconfig/ld.config.txt ] && mkdir -p "${'$'}ROOT/linkerconfig" 2>/dev/null; then
              : > "${'$'}ROOT/linkerconfig/ld.config.txt"
              LINKER_BIND="-b /linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt"
            fi
            exec "${'$'}NATIVE/libproot.so" --link2symlink -i 10287 -r "${'$'}ROOT" \
              -b /dev -b /proc -b /sys -b /apex -b /system -b /vendor \
              -b /storage -b /sdcard \
              ${'$'}LINKER_BIND \
              -w "${'$'}CWD" \
              $GUEST_PREFIX/bin/env -i \
                HOME=$GUEST_HOME \
                PREFIX=$GUEST_PREFIX \
                TERMUX__PREFIX=$GUEST_PREFIX \
                PATH=$GUEST_PREFIX/bin \
                TMPDIR=$GUEST_PREFIX/tmp \
                LANG=C.UTF-8 \
                TERM=xterm-256color \
                DEBIAN_FRONTEND=noninteractive \
                APT_LISTCHANGES_FRONTEND=none \
                NEEDRESTART_MODE=a \
                GIT_TERMINAL_PROMPT=0 \
                PIP_NO_INPUT=1 \
                CI=1 \
                ANDROID_ROOT=/system \
                ANDROID_DATA=/data \
                EXTERNAL_STORAGE=/sdcard \
                LD_LIBRARY_PATH=$GUEST_PREFIX/lib \
                $GUEST_BASH "${'$'}@"
        """.trimIndent() + "\n"

        val shell = File(context.filesDir, "termux-shell.sh")
        shell.writeText(script)
        shell.setExecutable(true, false)

        val bin = File(context.filesDir, "termux-bin").apply { mkdirs() }
        val bash = File(bin, "bash")
        bash.delete()
        Os.symlink("../termux-shell.sh", bash.absolutePath)
    }

    private fun writePackageConfig(context: Context) {
        val root = root(context)
        val config = File(root, "$PREFIX/etc/apt/apt.conf.d/99codecell")
        config.parentFile?.mkdirs()
        config.writeText(
            """
            APT::Get::Assume-Yes "true";
            APT::Get::Assume-No "false";
            APT::Get::allowUnauthenticated "false";
            Dpkg::Options {
              "--force-confdef";
              "--force-confold";
            };
            Dpkg::Use-Pty "false";
            """.trimIndent() + "\n",
        )
        File(root, "data/data/com.termux/cache/apt/archives/partial").mkdirs()
    }

    private fun deleteTree(file: File) {
        if (Files.isSymbolicLink(file.toPath())) {
            file.delete()
            return
        }
        file.listFiles()?.forEach { deleteTree(it) }
        file.delete()
    }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

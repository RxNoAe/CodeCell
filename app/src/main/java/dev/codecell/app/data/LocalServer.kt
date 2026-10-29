package dev.codecell.app.data

import android.content.Context
import java.io.File

/**
 * Owns the embedded opencode process. The binary ships as
 * jniLibs/arm64-v8a/libopencode.so so Android extracts it to the read-only
 * native library directory, which is the only place an app can execute a
 * bundled binary on modern Android.
 */
object LocalServer {

    enum class State { Stopped, Starting, Running, Failed }

    @Volatile
    var state: State = State.Stopped
        private set

    @Volatile
    var message: String = ""
        private set

    @Volatile
    var workspacePath: String = ""
        private set

    private var process: Process? = null
    private val lock = Any()

    fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libopencode.so")

    fun logFile(context: Context): File = File(context.filesDir, "opencode.log")

    fun homeDir(context: Context): File =
        File(context.filesDir, "opencode").apply { mkdirs() }

    fun workspaceDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "workspace")
            .apply { mkdirs() }

    fun isRunning(): Boolean = process?.isAlive == true

    fun start(
        context: Context,
        workspace: File,
        password: String,
        port: Int,
    ): Boolean = synchronized(lock) {
        if (isRunning()) return true

        val binary = binary(context)
        if (!binary.exists()) {
            state = State.Failed
            message = "The opencode runtime is missing from this build."
            return false
        }
        if (!binary.canExecute()) binary.setExecutable(true)

        val home = homeDir(context)
        val config = File(home, ".config").apply { mkdirs() }
        val data = File(home, ".local/share").apply { mkdirs() }
        val stateDir = File(home, ".local/state").apply { mkdirs() }
        val prefix = File(home, "usr").apply { mkdirs() }
        File(prefix, "bin").mkdirs()
        val cache = File(context.cacheDir, "opencode").apply { mkdirs() }
        val tmp = File(cache, "tmp").apply { mkdirs() }
        workspace.mkdirs()

        val builder = ProcessBuilder(
            binary.absolutePath,
            "serve",
            "--hostname", "127.0.0.1",
            "--port", port.toString(),
            "--pure",
        )
        builder.directory(workspace)
        builder.redirectErrorStream(true)
        TermuxEnvironment.ensureRuntimeConfig(context)
        val termuxShell = TermuxEnvironment.shell(context)
        val path = listOfNotNull(
            termuxShell?.parentFile?.absolutePath,
            context.applicationInfo.nativeLibraryDir,
            "/system/bin",
            "/system/xbin",
        ).joinToString(":")
        builder.environment().apply {
            put("HOME", home.absolutePath)
            put("XDG_CONFIG_HOME", config.absolutePath)
            put("XDG_DATA_HOME", data.absolutePath)
            put("XDG_STATE_HOME", stateDir.absolutePath)
            put("XDG_CACHE_HOME", cache.absolutePath)
            put("PREFIX", prefix.absolutePath)
            put("TMPDIR", tmp.absolutePath)
            put("PATH", path)
            put("SHELL", termuxShell?.absolutePath ?: "/system/bin/sh")
            put("OPENCODE_SERVER_USERNAME", "opencode")
            put("OPENCODE_EXPERIMENTAL_BASH_DEFAULT_TIMEOUT_MS", "900000")
            if (password.isNotBlank()) put("OPENCODE_SERVER_PASSWORD", password)
        }

        workspacePath = workspace.absolutePath
        return try {
            val started = builder.start()
            process = started
            state = State.Starting
            message = "Starting opencode..."
            Thread {
                runCatching {
                    logFile(context).outputStream().bufferedWriter().use { writer ->
                        started.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                writer.appendLine(line)
                                writer.flush()
                            }
                        }
                    }
                }
                if (!started.isAlive) {
                    state = State.Failed
                    message = "opencode stopped unexpectedly."
                }
            }.start()
            true
        } catch (e: Exception) {
            state = State.Failed
            message = e.message ?: "Could not start opencode."
            false
        }
    }

    fun stop() = synchronized(lock) {
        process?.destroy()
        process = null
        workspacePath = ""
        state = State.Stopped
        message = "Stopped"
    }

    fun logTail(context: Context, maxLines: Int = 10): String = runCatching {
        val file = logFile(context)
        if (!file.exists()) "" else file.readLines().takeLast(maxLines).joinToString("\n")
    }.getOrDefault("")
}

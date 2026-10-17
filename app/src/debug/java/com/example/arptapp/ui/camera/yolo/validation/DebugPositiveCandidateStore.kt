package com.example.arptapp.ui.camera.yolo.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * DEBUG-only bounded private storage. Only an explicitly supplied camera copy reaches save().
 * No public/cache/media path, model copy, networking, database, or automatic frame retention.
 * This worker-confined store never follows a symlink and never recursively deletes a directory.
 */
internal class DebugPositiveCandidateStore(private val noBackupDirectory: File) {
    companion object {
        const val DIRECTORY_NAME = "exp02_positive"
        const val MAX_CANDIDATES = 8
        const val MAX_PIXELS = 4_194_304
        const val MAX_SIDE = 4096
        const val MAX_PNG_BYTES = 8L * 1024 * 1024
        const val MAX_RESULT_BYTES = 262_144L
        private val ID = Regex("positive_candidate_[0-9]{8}_[0-9]{6}_[0-9]{3}_[a-f0-9]{8}")
        private val OWNED = Regex("positive_candidate_[0-9]{8}_[0-9]{6}_[0-9]{3}_[a-f0-9]{8}\\.(png|android\\.json)(\\.pending)?")
        fun requireDimensions(width: Int, height: Int) {
            require(width in 1..MAX_SIDE && height in 1..MAX_SIDE && width.toLong() * height <= MAX_PIXELS)
        }
    }

    private fun directory(create: Boolean): File {
        val parent = noBackupDirectory.canonicalFile
        require(parent.isDirectory) { "PRIVATE_ROOT_UNAVAILABLE" }
        val result = File(parent, DIRECTORY_NAME)
        if (create && attributes(result) == null) require(result.mkdir()) { "PRIVATE_DIRECTORY_CREATE_FAILED" }
        val stat = attributes(result)
        if (stat != null) {
            require(OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) { "UNSAFE_PRIVATE_DIRECTORY" }
            require(result.canonicalFile == result.absoluteFile && result.canonicalFile.parentFile == parent) { "PRIVATE_PATH_ESCAPE" }
            Os.chmod(result.absolutePath, 448) // 0700; no shared-storage intermediate.
        }
        return result
    }

    private fun attributes(file: File) = try { Os.lstat(file.absolutePath) } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) null else throw error
    }

    private fun ownedFile(directory: File, name: String, requireExisting: Boolean): File {
        require(OWNED.matches(name)) { "UNOWNED_FILE" }
        val file = File(directory, name)
        require(file.canonicalFile == file.absoluteFile && file.canonicalFile.parentFile == directory.canonicalFile) { "PRIVATE_PATH_ESCAPE" }
        val stat = attributes(file)
        require(!requireExisting || stat != null) { "CANDIDATE_MISSING" }
        require(stat == null || (OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode))) { "UNSAFE_CANDIDATE_FILE" }
        return file
    }

    fun inventory(): List<PositiveCandidate> {
        val directory = directory(false)
        if (!directory.exists()) return emptyList()
        val names = requireNotNull(directory.list()).sorted()
        // Validate all owned files before any read/deletion; do not touch unknown user files.
        val owned = names.filter { OWNED.matches(it) }.map { ownedFile(directory, it, true) }
        require(owned.size <= MAX_CANDIDATES * 4) { "CANDIDATE_FILE_LIMIT_EXCEEDED" }
        owned.forEach {
            require(it.length() <= if (it.name.contains(".android.json")) MAX_RESULT_BYTES else MAX_PNG_BYTES)
        }
        val images = names.filter { it.endsWith(".png") && ID.matches(it.removeSuffix(".png")) }
        require(images.size <= MAX_CANDIDATES) { "CANDIDATE_LIMIT_EXCEEDED" }
        return images.map { metadata(ownedFile(directory, it, true)) }
    }

    fun save(bitmap: Bitmap): PositiveCandidate {
        requireDimensions(bitmap.width, bitmap.height)
        check(!bitmap.isRecycled)
        removeInterruptedTemporaryFiles()
        require(inventory().size < MAX_CANDIDATES) { "CANDIDATE_LIMIT_REACHED" }
        val directory = directory(true)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val id = "positive_candidate_${timestamp}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val target = ownedFile(directory, "$id.png", false)
        val temporary = ownedFile(directory, "$id.png.pending", false)
        require(!target.exists() && temporary.createNewFile()) { "CANDIDATE_COLLISION" }
        try {
            Os.chmod(temporary.absolutePath, 384) // 0600
            FileOutputStream(temporary, false).use { file ->
                val bounded = LimitedOutput(file, MAX_PNG_BYTES)
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bounded)) { "PNG_ENCODING_FAILED" }
                bounded.flush()
                file.fd.sync()
            }
            require(temporary.length() in 1..MAX_PNG_BYTES)
            require(temporary.renameTo(target)) { "CANDIDATE_RENAME_FAILED" }
            return metadata(target)
        } finally {
            if (attributes(temporary) != null) ownedFile(directory, temporary.name, true).delete()
        }
    }

    fun decode(candidate: PositiveCandidate): Bitmap {
        require(ID.matches(candidate.id))
        val file = ownedFile(directory(false), "${candidate.id}.png", true)
        require(metadata(file) == candidate) { "CANDIDATE_CHANGED" }
        // PNG is lossless. Android and Python both consume this saved opaque RGB raster, NOT a
        // preview screenshot and NOT a separately resized/rotated camera image.
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        })).also { requireDimensions(it.width, it.height) }
    }

    fun writeResult(candidate: PositiveCandidate, json: String) {
        require(ID.matches(candidate.id))
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() in 1..MAX_RESULT_BYTES)
        val directory = directory(false)
        require(metadata(ownedFile(directory, "${candidate.id}.png", true)) == candidate)
        val result = ownedFile(directory, "${candidate.id}.android.json", false)
        val temporary = ownedFile(directory, "${candidate.id}.android.json.pending", false)
        require(!result.exists() && temporary.createNewFile()) { "RESULT_ALREADY_EXISTS" }
        try {
            Os.chmod(temporary.absolutePath, 384)
            FileOutputStream(temporary, false).use { it.write(bytes); it.fd.sync() }
            require(temporary.renameTo(result)) { "RESULT_RENAME_FAILED" }
        } finally {
            if (attributes(temporary) != null) ownedFile(directory, temporary.name, true).delete()
        }
    }

    fun deleteOwned(): Int {
        val directory = directory(false)
        if (!directory.exists()) return 0
        val files = requireNotNull(directory.list()).filter { OWNED.matches(it) }.map { ownedFile(directory, it, true) }
        require(files.size <= MAX_CANDIDATES * 4) { "CANDIDATE_FILE_LIMIT_EXCEEDED" }
        // Preflight all paths first. Unknown files and all sibling directories stay untouched.
        files.forEach { require(it.length() <= if (it.name.contains(".android.json")) MAX_RESULT_BYTES else MAX_PNG_BYTES) }
        files.forEach { require(it.delete()) { "CANDIDATE_DELETE_FAILED" } }
        return files.size
    }

    /** Only interrupted atomic-write temporaries, never a saved PNG/result. Prevents crash loops
     * from accumulating partial images indefinitely. All targets are preflighted before deletion. */
    private fun removeInterruptedTemporaryFiles() {
        val directory = directory(false)
        if (!directory.exists()) return
        val files = requireNotNull(directory.list()).filter { OWNED.matches(it) && it.endsWith(".pending") }
            .map { ownedFile(directory, it, true) }
        require(files.size <= MAX_CANDIDATES * 2) { "CANDIDATE_FILE_LIMIT_EXCEEDED" }
        files.forEach { require(it.length() <= if (it.name.contains(".android.json")) MAX_RESULT_BYTES else MAX_PNG_BYTES) }
        files.forEach { require(it.delete()) { "TEMPORARY_DELETE_FAILED" } }
    }

    private fun metadata(file: File): PositiveCandidate {
        require(file.length() in 1..MAX_PNG_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outMimeType == "image/png") { "INVALID_CANDIDATE_PNG" }
        requireDimensions(bounds.outWidth, bounds.outHeight)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_PNG_BYTES)
                digest.update(buffer, 0, read)
            }
        }
        return PositiveCandidate(file.name.removeSuffix(".png"), file.absolutePath, bounds.outWidth, bounds.outHeight,
            file.length(), digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
    }

    private class LimitedOutput(private val delegate: OutputStream, private val maximum: Long) : OutputStream() {
        private var written = 0L
        override fun write(value: Int) { check(++written <= maximum) { "PNG_SIZE_LIMIT" }; delegate.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(length >= 0 && written + length <= maximum) { "PNG_SIZE_LIMIT" }
            written += length
            delegate.write(bytes, offset, length)
        }
        override fun flush() = delegate.flush()
    }
}

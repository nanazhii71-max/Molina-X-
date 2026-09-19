package com.molinax.core

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * High-performance file exploration and navigation helper for Molina-X.
 *
 * Provides comprehensive file metadata extraction, categorization, filtering,
 * breadcrumb navigation, and safe I/O operations across Android storage and the
 * Linux subsystem runtime.
 */
object FileExplorerHelper {

    enum class FileCategory(val label: String) {
        DIRECTORY("Folder"),
        VIDEO("Video"),
        AUDIO("Audio"),
        CODE("Code / Script"),
        DOCUMENT("Document"),
        ARCHIVE("Archive"),
        IMAGE("Image"),
        BINARY("Binary / Executable"),
        OTHER("File")
    }

    enum class SortCriteria {
        NAME,
        SIZE,
        DATE_MODIFIED,
        EXTENSION
    }

    enum class SortDirection {
        ASCENDING,
        DESCENDING
    }

    data class ExplorerItem(
        val file: File,
        val name: String = file.name,
        val path: String = file.absolutePath,
        val isDirectory: Boolean = file.isDirectory,
        val size: Long = if (file.isDirectory) 0L else file.length(),
        val formattedSize: String = if (file.isDirectory) "--" else FileUtils.formatFileSize(file.length()),
        val lastModified: Long = file.lastModified(),
        val formattedDate: String = FileUtils.formatDate(file.lastModified()),
        val extension: String = file.extension.lowercase(Locale.ROOT),
        val category: FileCategory = determineCategory(file),
        val isReadable: Boolean = file.canRead(),
        val isWritable: Boolean = file.canWrite(),
        val isExecutable: Boolean = file.canExecute(),
        val isHidden: Boolean = file.isHidden || file.name.startsWith("."),
        val mimeType: String = determineMimeType(file)
    )

    data class ExplorerFilter(
        val showHidden: Boolean = false,
        val searchFilter: String = "",
        val categoryFilter: FileCategory? = null,
        val sortBy: SortCriteria = SortCriteria.NAME,
        val sortDirection: SortDirection = SortDirection.ASCENDING,
        val directoriesFirst: Boolean = true
    )

    data class Breadcrumb(
        val name: String,
        val path: String,
        val file: File
    )

    data class DirectorySummary(
        val fileCount: Int,
        val directoryCount: Int,
        val totalBytes: Long,
        val formattedTotalBytes: String
    )

    val ARCHIVE_EXTENSIONS = setOf(
        "zip", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "7z", "rar",
        "deb", "apk", "zst", "iso"
    )

    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "ico"
    )

    val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "epub", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "odt", "ods", "odp", "rtf", "csv", "tsv"
    )

    /**
     * Determines the functional [FileCategory] based on file properties and extension.
     */
    fun determineCategory(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.DIRECTORY

        val ext = file.extension.lowercase(Locale.ROOT)
        return when {
            ext in FileUtils.VIDEO_EXTENSIONS -> FileCategory.VIDEO
            ext in FileUtils.AUDIO_EXTENSIONS -> FileCategory.AUDIO
            ext in FileUtils.CODE_EXTENSIONS -> FileCategory.CODE
            ext in ARCHIVE_EXTENSIONS -> FileCategory.ARCHIVE
            ext in IMAGE_EXTENSIONS -> FileCategory.IMAGE
            ext in DOCUMENT_EXTENSIONS -> FileCategory.DOCUMENT
            file.canExecute() && ext.isEmpty() -> FileCategory.BINARY
            else -> FileCategory.OTHER
        }
    }

    /**
     * Resolves the MIME type for a given file.
     */
    fun determineMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            val mime = runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }.getOrNull()
            if (mime != null) return mime
        }
        return when (determineCategory(file)) {
            FileCategory.VIDEO -> "video/*"
            FileCategory.AUDIO -> "audio/*"
            FileCategory.IMAGE -> "image/*"
            FileCategory.CODE -> "text/plain"
            FileCategory.DOCUMENT -> "application/pdf"
            FileCategory.ARCHIVE -> "application/zip"
            FileCategory.BINARY -> "application/octet-stream"
            else -> "*/*"
        }
    }

    /**
     * Converts a raw [File] into an [ExplorerItem].
     */
    fun toExplorerItem(file: File): ExplorerItem {
        return ExplorerItem(file = file)
    }

    /**
     * Asynchronously lists and filters files in [dir] based on [filter].
     */
    suspend fun listDirectory(
        dir: File,
        filter: ExplorerFilter = ExplorerFilter()
    ): List<ExplorerItem> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val rawFiles = dir.listFiles() ?: return@withContext emptyList()

        var items = rawFiles.asSequence()
            .map { toExplorerItem(it) }

        if (!filter.showHidden) {
            items = items.filter { !it.isHidden }
        }

        if (filter.searchFilter.isNotBlank()) {
            val query = filter.searchFilter.trim().lowercase(Locale.ROOT)
            items = items.filter { it.name.lowercase(Locale.ROOT).contains(query) }
        }

        if (filter.categoryFilter != null) {
            items = items.filter { it.category == filter.categoryFilter }
        }

        val list = items.toList()
        sortItems(list, filter)
    }

    /**
     * Sorts a list of [ExplorerItem] according to the configured criteria.
     */
    fun sortItems(
        items: List<ExplorerItem>,
        filter: ExplorerFilter
    ): List<ExplorerItem> {
        val comparator = Comparator<ExplorerItem> { a, b ->
            if (filter.directoriesFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }

            val result = when (filter.sortBy) {
                SortCriteria.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortCriteria.SIZE -> a.size.compareTo(b.size)
                SortCriteria.DATE_MODIFIED -> a.lastModified.compareTo(b.lastModified)
                SortCriteria.EXTENSION -> a.extension.compareTo(b.extension, ignoreCase = true)
            }

            if (filter.sortDirection == SortDirection.ASCENDING) result else -result
        }

        return items.sortedWith(comparator)
    }

    /**
     * Constructs a sequential hierarchy of breadcrumbs from root down to [currentDir].
     */
    fun buildBreadcrumbs(currentDir: File, rootBoundary: File? = null): List<Breadcrumb> {
        val breadcrumbs = mutableListOf<Breadcrumb>()
        var curr: File? = currentDir.canonicalFile

        val boundaryPath = rootBoundary?.canonicalPath

        while (curr != null) {
            val name = if (curr.parent == null || curr.name.isEmpty()) "/" else curr.name
            breadcrumbs.add(0, Breadcrumb(name = name, path = curr.absolutePath, file = curr))

            if (boundaryPath != null && curr.canonicalPath == boundaryPath) {
                break
            }
            curr = curr.parentFile
        }

        return breadcrumbs
    }

    /**
     * Computes a summary of child file and directory counts and aggregate byte sizes.
     */
    suspend fun computeDirectorySummary(dir: File): DirectorySummary = withContext(Dispatchers.IO) {
        var fileCount = 0
        var dirCount = 0
        var totalBytes = 0L

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                dirCount++
            } else {
                fileCount++
                totalBytes += file.length()
            }
        }

        DirectorySummary(
            fileCount = fileCount,
            directoryCount = dirCount,
            totalBytes = totalBytes,
            formattedTotalBytes = FileUtils.formatFileSize(totalBytes)
        )
    }

    /**
     * Creates a new directory inside [parent].
     */
    suspend fun createDirectory(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(parent, name)
            if (target.exists()) {
                throw IllegalStateException("A file or directory named '$name' already exists.")
            }
            if (!target.mkdirs()) {
                throw IllegalStateException("Failed to create directory at ${target.absolutePath}")
            }
            target
        }
    }

    /**
     * Creates a new empty file inside [parent].
     */
    suspend fun createFile(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(parent, name)
            if (target.exists()) {
                throw IllegalStateException("A file named '$name' already exists.")
            }
            if (!target.createNewFile()) {
                throw IllegalStateException("Failed to create file at ${target.absolutePath}")
            }
            target
        }
    }

    /**
     * Renames [file] to [newName].
     */
    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = file.parentFile ?: throw IllegalStateException("Cannot rename a root directory.")
            val dest = File(parent, newName)
            if (dest.exists()) {
                throw IllegalStateException("Destination '$newName' already exists.")
            }
            if (!file.renameTo(dest)) {
                throw IllegalStateException("Failed to rename ${file.name} to $newName")
            }
            dest
        }
    }

    /**
     * Deletes a file or directory (recursively if directory).
     */
    suspend fun delete(file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    /**
     * Copies [source] into [targetDir].
     */
    suspend fun copy(
        source: File,
        targetDir: File,
        overwrite: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val destination = File(targetDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = overwrite)
            } else {
                source.copyTo(destination, overwrite = overwrite)
            }
            destination
        }
    }

    /**
     * Moves [source] into [targetDir].
     */
    suspend fun move(
        source: File,
        targetDir: File,
        overwrite: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val destination = File(targetDir, source.name)
            if (destination.exists() && overwrite) {
                if (destination.isDirectory) destination.deleteRecursively() else destination.delete()
            }
            if (!source.renameTo(destination)) {
                // Fallback copy and delete across filesystem boundaries
                if (source.isDirectory) {
                    source.copyRecursively(destination, overwrite = overwrite)
                    source.deleteRecursively()
                } else {
                    source.copyTo(destination, overwrite = overwrite)
                    source.delete()
                }
            }
            destination
        }
    }

    /**
     * Adjusts the executable permission of [file] (POSIX chmod +x / -x).
     */
    suspend fun setExecutable(file: File, executable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            file.setExecutable(executable, false)
        }
    }

    /**
     * Provides a curated list of system and user-accessible storage locations.
     */
    fun getStandardDirectories(context: Context): List<Pair<String, File>> {
        val list = mutableListOf<Pair<String, File>>()

        // Primary External Storage
        val extStorage = Environment.getExternalStorageDirectory()
        if (extStorage != null && extStorage.exists()) {
            list.add("Internal Storage" to extStorage)
        }

        // Standard user folders
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null && downloads.exists()) {
            list.add("Downloads" to downloads)
        }

        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documents != null && documents.exists()) {
            list.add("Documents" to documents)
        }

        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        if (movies != null && movies.exists()) {
            list.add("Movies" to movies)
        }

        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (music != null && music.exists()) {
            list.add("Music" to music)
        }

        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (pictures != null && pictures.exists()) {
            list.add("Pictures" to pictures)
        }

        // App-specific internal storage
        val appFiles = context.filesDir
        if (appFiles.exists()) {
            list.add("App Files" to appFiles)
        }

        // Linux Subsystem Home & Usr
        val linuxHome = File(appFiles, "home")
        if (linuxHome.exists() || linuxHome.mkdirs()) {
            list.add("Linux Home (~)" to linuxHome)
        }

        val linuxUsr = File(appFiles, "usr")
        if (linuxUsr.exists() || linuxUsr.mkdirs()) {
            list.add("Linux Prefix (/usr)" to linuxUsr)
        }

        return list
    }
}

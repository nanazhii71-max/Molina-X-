package com.molinax.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveManager {

    suspend fun createZip(sourceFiles: List<File>, destinationZip: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                destinationZip.parentFile?.mkdirs()
                ZipOutputStream(FileOutputStream(destinationZip)).use { zos ->
                    sourceFiles.forEach { file ->
                        addToZip(file, file.name, zos)
                    }
                }
                Result.success(destinationZip)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun addToZip(file: File, path: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                addToZip(child, "$path/${child.name}", zos)
            }
        } else {
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                zos.putNextEntry(ZipEntry(path))
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            }
        }
    }

    suspend fun extractZip(zipFile: File, destinationDir: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                destinationDir.mkdirs()
                ZipInputStream(FileInputStream(zipFile)).use { zis ->
                    var entry = zis.nextEntry
                    val buffer = ByteArray(8192)
                    while (entry != null) {
                        val newFile = File(destinationDir, entry.name)
                        // Security check against Zip Slip
                        if (!newFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                            throw SecurityException("Zip entry is outside of target directory: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                Result.success(destinationDir)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

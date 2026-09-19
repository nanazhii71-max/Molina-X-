package com.molinax.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileExplorerHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileCategorization() {
        val videoFile = File("movie.mp4")
        val audioFile = File("song.flac")
        val codeFile = File("script.sh")
        val archiveFile = File("backup.tar.gz")
        val imageFile = File("photo.png")
        val docFile = File("notes.pdf")
        val unknownFile = File("blob.dat")

        assertEquals(FileExplorerHelper.FileCategory.VIDEO, FileExplorerHelper.determineCategory(videoFile))
        assertEquals(FileExplorerHelper.FileCategory.AUDIO, FileExplorerHelper.determineCategory(audioFile))
        assertEquals(FileExplorerHelper.FileCategory.CODE, FileExplorerHelper.determineCategory(codeFile))
        assertEquals(FileExplorerHelper.FileCategory.ARCHIVE, FileExplorerHelper.determineCategory(archiveFile))
        assertEquals(FileExplorerHelper.FileCategory.IMAGE, FileExplorerHelper.determineCategory(imageFile))
        assertEquals(FileExplorerHelper.FileCategory.DOCUMENT, FileExplorerHelper.determineCategory(docFile))
        assertEquals(FileExplorerHelper.FileCategory.OTHER, FileExplorerHelper.determineCategory(unknownFile))
    }

    @Test
    fun testBreadcrumbConstruction() {
        val root = tempFolder.newFolder("breadcrumb_test")
        val sub1 = File(root, "level1").apply { mkdir() }
        val sub2 = File(sub1, "level2").apply { mkdir() }

        val breadcrumbs = FileExplorerHelper.buildBreadcrumbs(sub2, rootBoundary = root)
        assertTrue(breadcrumbs.size >= 2)
        assertEquals("level2", breadcrumbs.last().name)
    }

    @Test
    fun testDirectoryListingAndFiltering() = runTest {
        val root = tempFolder.newFolder("listing_test")

        // Create sample files and folders
        val folderA = File(root, "folder_a").apply { mkdir() }
        val fileZ = File(root, "z_doc.pdf").apply { writeText("Hello Z") }
        val fileA = File(root, "a_code.kt").apply { writeText("fun main() {}") }
        val fileHidden = File(root, ".hidden").apply { writeText("secret") }

        // Default listing (hidden excluded, directories first, alphabetical)
        val defaultList = FileExplorerHelper.listDirectory(root)
        assertEquals(3, defaultList.size)
        assertEquals("folder_a", defaultList[0].name)
        assertTrue(defaultList[0].isDirectory)
        assertEquals("a_code.kt", defaultList[1].name)
        assertEquals("z_doc.pdf", defaultList[2].name)

        // Show hidden filter
        val listWithHidden = FileExplorerHelper.listDirectory(
            root,
            FileExplorerHelper.ExplorerFilter(showHidden = true)
        )
        assertEquals(4, listWithHidden.size)

        // Search filter
        val searchList = FileExplorerHelper.listDirectory(
            root,
            FileExplorerHelper.ExplorerFilter(searchFilter = "code")
        )
        assertEquals(1, searchList.size)
        assertEquals("a_code.kt", searchList[0].name)

        // Category filter (Code)
        val codeCategoryList = FileExplorerHelper.listDirectory(
            root,
            FileExplorerHelper.ExplorerFilter(
                categoryFilter = FileExplorerHelper.FileCategory.CODE
            )
        )
        assertEquals(1, codeCategoryList.size)
        assertEquals("a_code.kt", codeCategoryList[0].name)

        // Category filter (Document)
        val docCategoryList = FileExplorerHelper.listDirectory(
            root,
            FileExplorerHelper.ExplorerFilter(
                categoryFilter = FileExplorerHelper.FileCategory.DOCUMENT
            )
        )
        assertEquals(1, docCategoryList.size)
        assertEquals("z_doc.pdf", docCategoryList[0].name)
    }

    @Test
    fun testFileOperations() = runTest {
        val root = tempFolder.newFolder("operations_test")

        // Create directory
        val newDirResult = FileExplorerHelper.createDirectory(root, "test_dir")
        assertTrue(newDirResult.isSuccess)
        val newDir = newDirResult.getOrThrow()
        assertTrue(newDir.exists() && newDir.isDirectory)

        // Create file
        val newFileResult = FileExplorerHelper.createFile(newDir, "test.txt")
        assertTrue(newFileResult.isSuccess)
        val newFile = newFileResult.getOrThrow()
        assertTrue(newFile.exists() && newFile.isFile)
        newFile.writeText("Test Content")

        // Directory summary
        val summary = FileExplorerHelper.computeDirectorySummary(newDir)
        assertEquals(1, summary.fileCount)
        assertEquals(0, summary.directoryCount)
        assertTrue(summary.totalBytes > 0)

        // Rename file
        val renameResult = FileExplorerHelper.rename(newFile, "renamed.txt")
        assertTrue(renameResult.isSuccess)
        val renamedFile = renameResult.getOrThrow()
        assertEquals("renamed.txt", renamedFile.name)
        assertTrue(renamedFile.exists())
        assertFalse(newFile.exists())

        // Copy file
        val copyDestDir = File(root, "copy_dest").apply { mkdir() }
        val copyResult = FileExplorerHelper.copy(renamedFile, copyDestDir)
        assertTrue(copyResult.isSuccess)
        val copiedFile = copyResult.getOrThrow()
        assertTrue(copiedFile.exists())
        assertTrue(renamedFile.exists())

        // Move file
        val moveDestDir = File(root, "move_dest").apply { mkdir() }
        val moveResult = FileExplorerHelper.move(renamedFile, moveDestDir)
        assertTrue(moveResult.isSuccess)
        val movedFile = moveResult.getOrThrow()
        assertTrue(movedFile.exists())
        assertFalse(renamedFile.exists())

        // Delete file and directory
        val deleteFileResult = FileExplorerHelper.delete(movedFile)
        assertTrue(deleteFileResult.isSuccess)
        assertFalse(movedFile.exists())

        val deleteDirResult = FileExplorerHelper.delete(copyDestDir)
        assertTrue(deleteDirResult.isSuccess)
        assertFalse(copyDestDir.exists())
    }
}

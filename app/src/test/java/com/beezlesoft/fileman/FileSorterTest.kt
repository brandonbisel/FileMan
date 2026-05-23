/*
 * Copyright 2026 Brandon Bisel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beezlesoft.fileman

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSorterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSortFilesByName() {
        // Create some files and directories
        val dirB = tempFolder.newFolder("dirB")
        val dirA = tempFolder.newFolder("dirA")
        val fileZ = tempFolder.newFile("fileZ.txt")
        val fileM = tempFolder.newFile("fileM.txt")

        val files = listOf(fileZ, dirB, fileM, dirA)
        val sortedFiles = FileSorter.sortFiles(files, FileSorter.SortType.NAME)

        // Expected order: dirA, dirB, fileM.txt, fileZ.txt
        assertEquals(4, sortedFiles.size)
        assertEquals("dirA", sortedFiles[0].name)
        assertEquals("dirB", sortedFiles[1].name)
        assertEquals("fileM.txt", sortedFiles[2].name)
        assertEquals("fileZ.txt", sortedFiles[3].name)
    }

    @Test
    fun testSortFilesBySize() {
        val fileLarge = tempFolder.newFile("large.txt")
        fileLarge.writeText("This is a large file content")
        
        val fileSmall = tempFolder.newFile("small.txt")
        fileSmall.writeText("Small")

        val files = listOf(fileSmall, fileLarge)
        val sortedFiles = FileSorter.sortFiles(files, FileSorter.SortType.SIZE)

        assertEquals("small.txt", sortedFiles[0].name)
        assertEquals("large.txt", sortedFiles[1].name)
    }

    @Test
    fun testSortFilesByDate() {
        val fileOld = tempFolder.newFile("old.txt")
        fileOld.setLastModified(1000000L)
        
        val fileNew = tempFolder.newFile("new.txt")
        fileNew.setLastModified(5000000L)

        val files = listOf(fileNew, fileOld)
        val sortedFiles = FileSorter.sortFiles(files, FileSorter.SortType.DATE)

        assertEquals("old.txt", sortedFiles[0].name)
        assertEquals("new.txt", sortedFiles[1].name)
    }

    @Test
    fun testSortFilesCaseInsensitive() {
        val fileb = tempFolder.newFile("b.txt")
        val fileA = tempFolder.newFile("A.txt")

        val files = listOf(fileb, fileA)
        val sortedFiles = FileSorter.sortFiles(files, FileSorter.SortType.NAME)

        assertEquals("A.txt", sortedFiles[0].name)
        assertEquals("b.txt", sortedFiles[1].name)
    }
}
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
import java.io.File

class FileFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFilterHiddenFiles() {
        val visibleFile = tempFolder.newFile("visible.txt")
        val hiddenFile = tempFolder.newFile(".hidden.txt")
        val files = listOf(visibleFile, hiddenFile)

        // showHidden = false
        val filteredHidden = FileFilter.filterFiles(files, showHidden = false)
        assertEquals(1, filteredHidden.size)
        assertEquals("visible.txt", filteredHidden[0].name)

        // showHidden = true
        val filteredVisible = FileFilter.filterFiles(files, showHidden = true)
        assertEquals(2, filteredVisible.size)
    }

    @Test
    fun testFilterByQuery() {
        val file1 = tempFolder.newFile("apple.txt")
        val file2 = tempFolder.newFile("banana.txt")
        val file3 = tempFolder.newFile("Pineapple.txt")
        val files = listOf(file1, file2, file3)

        // Query "apple"
        val filtered = FileFilter.filterFiles(files, showHidden = true, query = "apple")
        assertEquals(2, filtered.size)
        assertEquals("apple.txt", filtered[0].name)
        assertEquals("Pineapple.txt", filtered[1].name)
    }

    @Test
    fun testFilterQueryAndHidden() {
        val file1 = tempFolder.newFile("apple.txt")
        val file2 = tempFolder.newFile(".apple_hidden.txt")
        val files = listOf(file1, file2)

        // Query "apple", showHidden = false
        val filtered = FileFilter.filterFiles(files, showHidden = false, query = "apple")
        assertEquals(1, filtered.size)
        assertEquals("apple.txt", filtered[0].name)
    }
}


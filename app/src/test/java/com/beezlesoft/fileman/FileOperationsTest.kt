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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlinx.coroutines.runBlocking

class FileOperationsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCopyRecursive() = runBlocking {
        val sourceDir = tempFolder.newFolder("source")
        val subDir = File(sourceDir, "sub")
        subDir.mkdir()
        val file = File(subDir, "test.txt")
        file.writeText("Hello World")

        val destDir = File(tempFolder.root, "dest")
        
        FileOperations.copyRecursive(sourceDir, destDir)

        assertTrue(destDir.exists())
        val copiedFile = File(destDir, "sub/test.txt")
        assertTrue(copiedFile.exists())
        assertEquals("Hello World", copiedFile.readText())
    }

    @Test
    fun testDeleteRecursive() = runBlocking {
        val dir = tempFolder.newFolder("toDelete")
        val subDir = File(dir, "sub")
        subDir.mkdir()
        val file = File(subDir, "test.txt")
        file.writeText("Delete me")

        assertTrue(dir.exists())
        
        val result = FileOperations.deleteRecursive(dir)
        
        assertTrue(result)
        assertFalse(dir.exists())
    }

    @Test
    fun testGetOrphanedPrefKeys() {
        val parentDir = tempFolder.newFolder("parent")
        val existingChild = File(parentDir, "exists")
        existingChild.mkdir()
        
        val deletedPath = File(parentDir, "deleted").absolutePath
        val existingPath = existingChild.absolutePath
        val unrelatedPath = "/some/other/path"
        
        val keys = setOf(deletedPath, existingPath, unrelatedPath)
        
        val orphaned = FileOperations.getOrphanedPrefKeys(parentDir, keys)
        
        assertEquals(1, orphaned.size)
        assertEquals(deletedPath, orphaned[0])
    }
}


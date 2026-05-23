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

import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.withContext

/**
 * Utility object for performing recursive file and directory operations.
 */
object FileOperations {

    /**
     * Recursively copies a file or directory from the source to the destination.
     * 
     * @param source The source file or directory.
     * @param destination The target destination path.
     * @param onProgress Optional callback for reporting progress (copied items count).
     */
    suspend fun copyRecursive(source: File, destination: File, onProgress: ((Int) -> Unit)? = null) {
        var totalCopied = 0
        copyRecursiveInternal(source, destination) {
            totalCopied++
            onProgress?.invoke(totalCopied)
        }
    }

    private suspend fun copyRecursiveInternal(source: File, destination: File, onItemCopied: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            if (source.isDirectory) {
                if (!destination.exists()) destination.mkdirs()
                source.list()?.forEach { child ->
                    copyRecursiveInternal(File(source, child), File(destination, child), onItemCopied)
                }
            } else {
                FileInputStream(source).use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            onItemCopied()
        }
    }

    /**
     * Recursively deletes a file or directory.
     * 
     * @param file The file or directory to delete.
     * @return True if the operation was successful, false otherwise.
     */
    suspend fun deleteRecursive(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }

    /**
     * Identifies which keys in the given set represent non-existent subdirectories of [parentDir].
     * This is used to clean up orphaned preferences for deleted folders.
     * 
     * @param parentDir The directory whose subdirectories should be checked.
     * @param allKeys All currently saved preference keys (paths).
     * @return A list of keys that no longer exist on disk.
     */
    fun getOrphanedPrefKeys(parentDir: File, allKeys: Set<String>): List<String> {
        val parentPath = parentDir.absolutePath
        val prefix = if (parentPath.endsWith(File.separator)) parentPath else parentPath + File.separator
        return allKeys.filter { path ->
            path.startsWith(prefix) && !File(path).exists()
        }
    }
}
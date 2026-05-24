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

import java.io.File

/**
 * Utility object for filtering lists of [File] objects.
 */
object FileFilter {
    /**
     * Filters a list of files based on whether they are hidden and an optional search query.
     * 
     * @param files The original list of files.
     * @param showHidden Whether to include hidden files (starting with a dot).
     * @param query An optional search string to filter files by name (case-insensitive).
     * @return The filtered list of files.
     */
    fun filterFiles(files: List<File>, showHidden: Boolean, query: String = ""): List<File> {
        return files.filter { file ->
            val isHiddenValid = showHidden || !file.name.startsWith(".")
            val isQueryValid = query.isEmpty() || file.name.contains(query, ignoreCase = true)
            isHiddenValid && isQueryValid
        }
    }
}


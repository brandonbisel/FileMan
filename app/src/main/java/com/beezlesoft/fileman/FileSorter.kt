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
 * Utility object for sorting lists of [File] objects based on different criteria.
 */
object FileSorter {
    /**
     * Supported sorting strategies.
     */
    enum class SortType {
        /** Sort alphabetically by name. */
        NAME, 
        /** Sort by file size (on disk). */
        SIZE, 
        /** Sort by last modification timestamp. */
        DATE
    }

    /**
     * Sorts a list of files, always placing directories before files, 
     * and then applying the secondary sorting criteria.
     * 
     * @param files The list of files to sort.
     * @param sortType The primary sorting strategy to apply to the files.
     * @return A sorted list of files.
     */
    fun sortFiles(files: List<File>, sortType: SortType = SortType.NAME): List<File> {
        val comparator = when (sortType) {
            SortType.NAME -> compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })
            SortType.SIZE -> compareBy<File>({ !it.isDirectory }, { it.length() }, { it.name.lowercase() })
            SortType.DATE -> compareBy<File>({ !it.isDirectory }, { it.lastModified() }, { it.name.lowercase() })
        }
        return files.sortedWith(comparator)
    }
}


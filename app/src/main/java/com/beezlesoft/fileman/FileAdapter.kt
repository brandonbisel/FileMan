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

import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for the RecyclerView to display file and directory entries.
 *
 * @property files The list of files to display.
 * @property onItemClick Callback invoked when a file or directory is clicked.
 * @property onItemLongClick Callback invoked when a file or directory is long-clicked (or right-clicked).
 */
class FileAdapter(
    private var files: List<File>,
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private val selectedItems = mutableSetOf<File>()
    private var multiSelectMode = false

    /**
     * ViewHolder class for file list items.
     */
    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.fileIcon)
        val name: TextView = view.findViewById(R.id.fileName)
        val info: TextView = view.findViewById(R.id.fileInfo)
        val container: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = if (file.name.isEmpty()) file.absolutePath else file.name
        
        val context = holder.itemView.context
        val size = if (file.isDirectory) "" else Formatter.formatShortFileSize(context, file.length())
        val date = dateFormat.format(Date(file.lastModified()))
        
        // Show selection state
        holder.container.isActivated = selectedItems.contains(file)

        if (file.name == "..") {
            holder.name.text = ".."
            holder.info.text = context.getString(R.string.file_info_parent_dir)
            holder.icon.setImageResource(R.drawable.ic_folder)
            holder.icon.setColorFilter(context.getColorFromAttr(com.google.android.material.R.attr.colorOutline))
        } else {
            // Use a template for the info text if size is available
            holder.info.text = if (size.isEmpty()) {
                date
            } else {
                context.getString(R.string.file_info_placeholder)
                    .replace("Size", size)
                    .replace("Date", date)
            }

            if (file.isDirectory) {
                holder.icon.setImageResource(R.drawable.ic_folder)
                holder.icon.setColorFilter(context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary))
            } else {
                holder.icon.setImageResource(R.drawable.ic_file)
                holder.icon.setColorFilter(context.getColorFromAttr(com.google.android.material.R.attr.colorOutline))
            }
        }
        
        holder.itemView.setOnClickListener {
            if (multiSelectMode && file.name != "..") {
                toggleSelection(file)
            } else {
                onItemClick(file)
            }
        }
        holder.itemView.setOnLongClickListener { 
            if (file.name != "..") {
                if (!multiSelectMode) {
                    setMultiSelectMode(true)
                    toggleSelection(file)
                } else {
                    onItemLongClick(file, it)
                }
            }
            true 
        }
    }

    override fun getItemCount(): Int = files.size

    fun setMultiSelectMode(enabled: Boolean) {
        multiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
            notifyDataSetChanged()
        }
    }

    fun toggleSelection(file: File) {
        if (selectedItems.contains(file)) {
            selectedItems.remove(file)
        } else {
            selectedItems.add(file)
        }
        notifyItemChanged(files.indexOf(file))
        onSelectionChanged(selectedItems.size)
    }

    fun getSelectedFiles(): List<File> = selectedItems.toList()

    /**
     * Updates the adapter's data set and refreshes the list.
     * @param newFiles The new list of files to display.
     */
    fun updateFiles(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    private fun android.content.Context.getColorFromAttr(@AttrRes attrColor: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrColor, typedValue, true)
        return typedValue.data
    }
}
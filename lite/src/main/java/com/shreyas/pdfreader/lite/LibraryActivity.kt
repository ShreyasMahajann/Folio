package com.shreyas.pdfreader.lite

import android.app.ListActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.io.File

/** Lists the PDF files on the storage. The last opened book is first. */
class LibraryActivity : ListActivity() {

    private val progress by lazy { getSharedPreferences(Prefs.PROGRESS, MODE_PRIVATE) }
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.library)
        scan()
    }

    override fun onResume() {
        super.onResume()
        showFiles()
    }

    private fun scan() {
        emptyText().setText(R.string.scanning)
        Thread {
            val found = ArrayList<File>()
            val seen = HashSet<String>()
            for (root in listOf(Environment.getExternalStorageDirectory(), File("/mnt"), File("/storage"))) {
                findPdfs(root, MAX_DEPTH, seen, found)
            }
            runOnUiThread {
                files = found
                emptyText().setText(R.string.no_files)
                showFiles()
            }
        }.start()
    }

    private fun findPdfs(folder: File, depth: Int, seen: MutableSet<String>, found: MutableList<File>) {
        // The canonical path stops a second visit through a link such as /sdcard.
        if (!seen.add(folder.canonicalPath)) return
        for (file in folder.listFiles() ?: return) {
            when {
                file.name.startsWith(".") -> Unit
                file.isDirectory -> if (depth > 0 && file.name !in SKIPPED_FOLDERS) findPdfs(file, depth - 1, seen, found)
                file.name.endsWith(".pdf", ignoreCase = true) -> found.add(file)
            }
        }
    }

    private fun key(file: File) = Uri.fromFile(file).toString()

    private fun emptyText() = findViewById<TextView>(android.R.id.empty)

    private fun showFiles() {
        val sorted = files.sortedWith(
            compareByDescending<File> { progress.getLong(Prefs.TIME + key(it), 0) }.thenBy { it.name.lowercase() }
        )
        listAdapter = object : ArrayAdapter<File>(this, android.R.layout.simple_list_item_2, android.R.id.text1, sorted) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                val file = sorted[position]
                val count = progress.getInt(Prefs.COUNT + key(file), 0)
                row.findViewById<TextView>(android.R.id.text1).text = file.name.dropLast(4)
                row.findViewById<TextView>(android.R.id.text2).text = if (count > 0) {
                    getString(R.string.page_of, progress.getInt(Prefs.PAGE + key(file), 0) + 1, count)
                } else {
                    getString(R.string.size_mb, file.length() / 1_048_576.0)
                }
                return row
            }
        }
    }

    override fun onListItemClick(list: ListView, view: View, position: Int, id: Long) {
        val file = list.getItemAtPosition(position) as File
        startActivity(Intent(this, ReaderActivity::class.java).setData(Uri.fromFile(file)))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(R.string.rescan).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        scan()
        return true
    }

    private companion object {
        const val MAX_DEPTH = 6
        // System folders with no books. "Android" holds the data of other apps.
        val SKIPPED_FOLDERS = setOf("Android", "asec", "obb", "secure", "LOST.DIR", "DCIM")
    }
}

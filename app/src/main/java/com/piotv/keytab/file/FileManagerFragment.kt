package com.piotv.keytab.file

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.io.File
import java.util.concurrent.Executors

/** Dateimanager mit mehreren Tabs; jeder Tab merkt sich sein Verzeichnis. */
class FileManagerFragment : Fragment() {

    private val tabs = mutableListOf<File>()
    private var current = 0
    private lateinit var adapter: FileAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var pathView: TextView
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) reload()
            else Toast.makeText(requireContext(), R.string.storage_denied, Toast.LENGTH_LONG).show()
        }

    private inner class FileAdapter : RecyclerView.Adapter<FileHolder>() {
        val entries = mutableListOf<File>()

        fun set(files: List<File>) {
            // DiffUtil: nur geänderte Positionen neu binden statt komplettem Rebuild
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = entries.size
                override fun getNewListSize() = files.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    entries[oldPos].absolutePath == files[newPos].absolutePath
                override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                    entries[oldPos] == files[newPos]
            })
            entries.clear()
            entries.addAll(files)
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return FileHolder(v)
        }

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: FileHolder, position: Int) {
            val f = entries[position]
            holder.name.text = if (f.isDirectory) "\uD83D\uDCC1 " + f.name else "\uD83D\uDCC4 " + f.name
            holder.itemView.setOnClickListener {
                if (f.isDirectory) cd(f) else openFile(f)
            }
        }
    }

    private class FileHolder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.file_name)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_file_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tabLayout = view.findViewById(R.id.file_tabs)
        pathView = view.findViewById(R.id.current_path)
        val list = view.findViewById<RecyclerView>(R.id.file_list)
        adapter = FileAdapter()
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { current = tab.position; reload() }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        view.findViewById<View>(R.id.btn_new_tab).setOnClickListener { addTab() }
        view.findViewById<View>(R.id.btn_close_tab).setOnClickListener { closeTab() }
        view.findViewById<View>(R.id.btn_up).setOnClickListener { up() }

        if (savedInstanceState == null) {
            val root = Environment.getExternalStorageDirectory()
            val home = root?.absolutePath?.let { File(it, "Download") } ?: File("/")
            addTab(if (home.canRead()) home else File("/"))
        } else {
            reload()
        }
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun addTab(dir: File = tabs.getOrNull(current) ?: File("/")) {
        tabs.add(dir)
        current = tabs.size - 1
        val tab = tabLayout.newTab()
        tab.text = dir.name.ifEmpty { "/" }
        tabLayout.addTab(tab, true)
        reload()
    }

    private fun closeTab() {
        if (tabs.size <= 1) return
        val pos = tabLayout.selectedTabPosition
        tabs.removeAt(pos)
        tabLayout.removeTabAt(pos)
        current = (pos - 1).coerceAtLeast(0)
        reload()
    }

    private fun cd(dir: File) {
        if (tabs.isEmpty()) return
        tabs[current] = dir
        tabLayout.getTabAt(current)?.text = dir.name.ifEmpty { "/" }
        reload()
    }

    private fun up() {
        val dir = tabs.getOrNull(current) ?: return
        dir.parentFile?.let { cd(it) }
    }

    private fun reload() {
        val dir = tabs.getOrNull(current) ?: return
        pathView.text = dir.absolutePath
        // listFiles() vom Main-Thread fernhalten; Ergebnis nur anwenden,
        // wenn der Tab/Ordner inzwischen nicht gewechselt wurde
        ioExecutor.execute {
            val files = dir.listFiles()
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyList()
            view?.post {
                if (!isAdded || tabs.getOrNull(current)?.absolutePath != dir.absolutePath) return@post
                adapter.set(files)
            }
        }
    }

    private fun openFile(f: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            f
        )
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.fm_open_no_app, f.name), Toast.LENGTH_SHORT).show()
        }
    }
}

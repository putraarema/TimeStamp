package id.kameralokasigps

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GalleryActivity : ComponentActivity() {
    private lateinit var grid: GridView
    private lateinit var emptyText: TextView
    private lateinit var loader: ExecutorService
    private val imageUris = mutableListOf<Uri>()
    private val thumbnails = object : LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int =
            value.byteCount.coerceAtLeast(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loader = Executors.newFixedThreadPool(2)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
        }
        top.addView(Button(this).apply {
            text = "Kembali"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "  Riwayat Foto"
            textSize = 19f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(8, 0, 0, 0)
        })
        root.addView(top)
        emptyText = TextView(this).apply {
            text = "Belum ada foto. Ambil foto dari kamera untuk melihat riwayat di sini."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(24, 24, 24, 24)
        }
        grid = GridView(this).apply {
            numColumns = 3
            horizontalSpacing = 3
            verticalSpacing = 3
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setBackgroundColor(android.graphics.Color.BLACK)
            emptyView = emptyText
            setOnItemClickListener { _, _, position, _ -> openImage(imageUris[position]) }
        }
        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(emptyText)
        setContentView(root)
        loadImages()
    }

    private fun loadImages() {
        imageUris.clear()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf("Pictures/KameraLokasiGPS/")
        } else {
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/Pictures/KameraLokasiGPS/%")
        }
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(collection, projection, selection, selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC")
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    imageUris.add(Uri.withAppendedPath(collection,
                        it.getLong(idColumn).toString()))
                }
            }
        } catch (_: Exception) {
            // Galeri tetap menampilkan keadaan kosong bila penyedia media tidak dapat diakses.
        }
        grid.adapter = PhotoAdapter()
        emptyText.visibility = if (imageUris.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) }
        catch (_: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka foto.", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class PhotoAdapter : android.widget.BaseAdapter() {
        override fun getCount() = imageUris.size
        override fun getItem(position: Int) = imageUris[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val image = (convertView as? ImageView) ?: ImageView(this@GalleryActivity).apply {
                val side = (resources.displayMetrics.widthPixels / 3f).toInt()
                layoutParams = AbsListView.LayoutParams(side, side)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.DKGRAY)
            }
            val uri = imageUris[position]
            val key = uri.toString()
            image.tag = key
            image.setImageDrawable(null)
            val cached = thumbnails.get(key)
            if (cached != null) image.setImageBitmap(cached)
            else loader.execute {
                val bitmap = try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        contentResolver.loadThumbnail(uri,
                            android.util.Size(360, 360), null)
                    } else {
                        val id = uri.lastPathSegment?.toLongOrNull()
                        if (id != null) MediaStore.Images.Thumbnails.getThumbnail(
                            contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                        ) else null
                    }
                } catch (_: Exception) { null }
                if (bitmap != null) {
                    thumbnails.put(key, bitmap)
                    image.post {
                        if (image.tag == key) image.setImageBitmap(bitmap)
                    }
                }
            }
            return image
        }
    }

    override fun onDestroy() {
        loader.shutdown()
        super.onDestroy()
    }
}

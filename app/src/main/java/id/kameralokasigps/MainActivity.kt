package id.kameralokasigps

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var statusText: TextView
    private lateinit var watermarkSwitch: Switch
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var locationManager: LocationManager
    private var imageCapture: ImageCapture? = null
    private var latestLocation: Location? = null
    private var locationListener: LocationListener? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val prefs by lazy { getSharedPreferences("camera_settings", MODE_PRIVATE) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasCameraPermission()) startCamera()
            if (hasLocationPermission()) updateLocation()
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        buildScreen()
        requestNeededPermissions()
    }

    private fun buildScreen() {
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val sidePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            setBackgroundColor(Color.rgb(24, 24, 24))
        }
        val header = TextView(this).apply {
            text = "KAMERA LOKASI GPS"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(6, 6, 6, 8)
            setTypeface(null, Typeface.BOLD)
        }
        sidePanel.addView(header)

        captureButton = Button(this).apply {
            text = "●  AMBIL FOTO"
            textSize = 18f
            isAllCaps = false
            isEnabled = false
            minHeight = dp(58)
            setOnClickListener { takePhoto() }
        }
        sidePanel.addView(captureButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(58)
        ).apply { bottomMargin = dp(6) })

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        toolsRow.addView(Button(this).apply {
            text = "Balik kamera"; isAllCaps = false
            setOnClickListener { switchCamera() }
        }, weightedButtonParams())
        toolsRow.addView(Button(this).apply {
            text = "Watermark"; isAllCaps = false
            setOnClickListener { showWatermarkSettings() }
        }, weightedButtonParams())
        toolsRow.addView(Button(this).apply {
            text = "Galeri"; isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivity, GalleryActivity::class.java)) }
        }, weightedButtonParams())
        sidePanel.addView(toolsRow)

        sidePanel.addView(Button(this).apply {
            text = "Peta GPS"; isAllCaps = false
            setOnClickListener { showGpsMap() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            .apply { topMargin = dp(4) })

        watermarkSwitch = Switch(this).apply {
            text = "Watermark lokasi & waktu"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("watermark_enabled", true)
            setPadding(0, 3, 0, 3)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("watermark_enabled", checked).apply()
            }
        }
        sidePanel.addView(watermarkSwitch)

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.LTGRAY)
            text = "Mencari GPS..."
            setPadding(0, 3, 0, 3)
        }
        sidePanel.addView(statusText)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        if (landscape) {
            root.addView(previewView, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            root.addView(sidePanel, LinearLayout.LayoutParams(dp(250),
                LinearLayout.LayoutParams.MATCH_PARENT))
        } else {
            root.addView(previewView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(sidePanel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    private fun showGpsMap() {
        val loc = latestLocation
        if (loc == null) {
            Toast.makeText(this, "Menunggu koordinat GPS. Pastikan lokasi aktif.", Toast.LENGTH_LONG).show()
            updateLocation()
            return
        }
        val lat = loc.latitude
        val lon = loc.longitude
        val delta = 0.006
        val bbox = "${lon - delta},${lat - delta},${lon + delta},${lat + delta}"
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body,iframe{margin:0;width:100%;height:100%;border:0;background:#eee}</style></head>
            <body><iframe src="https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$lat,$lon"></iframe></body></html>
        """.trimIndent()
        val web = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380))
            loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Peta lokasi GPS")
            .setView(web)
            .show()
    }

    private fun weightedButtonParams() = LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
    ).apply { marginEnd = dp(3); marginStart = dp(3) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (!hasCameraPermission()) needed.add(Manifest.permission.CAMERA)
        if (!hasLocationPermission()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT <= 28) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.distinct().toTypedArray())
        else {
            startCamera()
            updateLocation()
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() = hasFineLocationPermission() ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (!hasCameraPermission()) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                if (!provider.hasCamera(selector)) {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }
                val selected = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selected, preview, imageCapture)
                captureButton.isEnabled = true
            } catch (e: Exception) {
                captureButton.isEnabled = false
                showStatus("Kamera gagal dibuka: ${e.localizedMessage ?: "kesalahan tidak diketahui"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        captureButton.isEnabled = false
        startCamera()
    }

    private fun updateLocation() {
        if (!hasLocationPermission()) {
            showStatus("Izin lokasi belum diberikan. Foto tetap bisa diambil tanpa koordinat.")
            return
        }
        try {
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                if (provider == LocationManager.GPS_PROVIDER && !hasFineLocationPermission()) continue
                val last = locationManager.getLastKnownLocation(provider)
                if (last != null && (latestLocation == null || last.time > (latestLocation?.time ?: 0L))) {
                    latestLocation = last
                }
            }
            locationListener?.let { locationManager.removeUpdates(it) }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    latestLocation = location
                    updateStatus()
                }
            }
            locationListener = listener
            val wantedProviders = if (hasFineLocationPermission()) {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            } else listOf(LocationManager.NETWORK_PROVIDER)
            wantedProviders.filter { it in providers }.forEach { provider ->
                try {
                    locationManager.requestLocationUpdates(
                        provider, 3000L, 5f, listener, Looper.getMainLooper()
                    )
                } catch (_: SecurityException) { }
            }
            updateStatus()
        } catch (_: SecurityException) {
            showStatus("Akses lokasi tidak tersedia.")
        } catch (_: Exception) {
            showStatus("Layanan lokasi tidak tersedia. Foto tetap bisa diambil.")
        }
    }

    private fun updateStatus() {
        val loc = latestLocation
        showStatus(if (loc != null) {
            "GPS: %.6f, %.6f  •  akurasi ±%.0f m".format(
                Locale.US, loc.latitude, loc.longitude, loc.accuracy
            )
        } else "Mencari GPS... Aktifkan layanan lokasi untuk hasil terbaik.")
    }

    private fun showWatermarkSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        content.addView(TextView(this).apply { text = "Posisi watermark" })
        val positions = listOf("Kiri bawah", "Kanan bawah", "Kiri atas", "Kanan atas")
        val values = listOf("BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_LEFT", "TOP_RIGHT")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, positions)
            setSelection(values.indexOf(prefs.getString("watermark_position", "BOTTOM_LEFT"))
                .coerceAtLeast(0))
        }
        content.addView(spinner)
        content.addView(TextView(this).apply {
            text = "Ukuran tulisan"
            setPadding(0, dp(16), 0, 0)
        })
        val sizeLabel = TextView(this).apply { text = "Sedang" }
        val sizeSeek = SeekBar(this).apply {
            max = 4
            progress = ((prefs.getFloat("watermark_scale", 1f) - 0.8f) / 0.1f)
                .toInt().coerceIn(0, 4)
        }
        fun updateSizeLabel(progress: Int) {
            sizeLabel.text = when (progress) {
                0 -> "Sangat kecil"
                1 -> "Kecil"
                2 -> "Sedang"
                3 -> "Besar"
                else -> "Sangat besar"
            }
        }
        updateSizeLabel(sizeSeek.progress)
        sizeSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSizeLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        content.addView(sizeSeek)
        content.addView(sizeLabel)

        android.app.AlertDialog.Builder(this)
            .setTitle("Pengaturan watermark")
            .setView(content)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                prefs.edit()
                    .putString("watermark_position", values[spinner.selectedItemPosition])
                    .putFloat("watermark_scale", 0.8f + sizeSeek.progress * 0.1f)
                    .apply()
                Toast.makeText(this, "Pengaturan watermark disimpan", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            showStatus("Kamera belum siap.")
            return
        }
        val addWatermark = watermarkSwitch.isChecked
        val locationSnapshot = latestLocation?.let { Location(it) }
        val watermarkPosition = prefs.getString("watermark_position", "BOTTOM_LEFT") ?: "BOTTOM_LEFT"
        val watermarkScale = prefs.getFloat("watermark_scale", 1f)
        captureButton.isEnabled = false
        val tempFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(output, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    try {
                        val uri = if (addWatermark) {
                            val decoded = decodeWithExifOrientation(tempFile)
                            val marked = addWatermark(decoded, locationSnapshot,
                                watermarkPosition, watermarkScale)
                            if (marked !== decoded) decoded.recycle()
                            try { saveBitmapToGallery(marked) } finally { marked.recycle() }
                        } else {
                            // Simpan berkas JPEG asli agar foto tanpa watermark tetap mempertahankan EXIF.
                            saveOriginalFileToGallery(tempFile)
                        }
                        tempFile.delete()
                        runOnUiThread {
                            captureButton.isEnabled = true
                            val message = if (uri != null) "Foto berhasil disimpan ke Galeri." else
                                "Gagal menyimpan foto. Periksa izin penyimpanan."
                            showStatus(message)
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        tempFile.delete()
                        runOnUiThread {
                            captureButton.isEnabled = true
                            showStatus("Gagal memproses foto: ${e.localizedMessage ?: "kesalahan"}")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    tempFile.delete()
                    runOnUiThread {
                        captureButton.isEnabled = true
                        showStatus("Gagal mengambil foto: ${exception.localizedMessage}")
                    }
                }
            })
    }

    private fun decodeWithExifOrientation(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("Gambar tidak dapat dibaca")
        val orientation = try {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED) return bitmap
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    private fun addWatermark(
        source: Bitmap,
        location: Location?,
        position: String,
        scaleMultiplier: Float
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("Memori foto tidak cukup")
        val canvas = Canvas(result)
        val scale = (result.width / 1080f).coerceAtLeast(0.65f) * scaleMultiplier
        val pad = 25f * scale
        val textSize = 27f * scale
        val gap = 8f * scale
        val now = Date()
        val lines = mutableListOf(
            "KAMERA LOKASI GPS",
            "${SimpleDateFormat("EEEE", Locale("id", "ID")).format(now)}, " +
                SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(now),
            "Pukul ${SimpleDateFormat("HH:mm:ss", Locale("id", "ID")).format(now)}"
        )
        if (location != null) {
            val placeName = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale("id", "ID")).getFromLocation(
                    location.latitude, location.longitude, 1
                )?.firstOrNull()?.let { address ->
                    listOfNotNull(address.subLocality, address.locality,
                        address.subAdminArea, address.adminArea)
                        .filter { it.isNotBlank() }.distinct().joinToString(", ")
                }
            } catch (_: Exception) { null }
            if (!placeName.isNullOrBlank()) lines.add(placeName)
            lines.add("Lat: %.6f  Long: %.6f".format(Locale.US,
                location.latitude, location.longitude))
            lines.add("Akurasi GPS: ±%.0f m".format(Locale("id", "ID"), location.accuracy))
        } else lines.add("Koordinat GPS tidak tersedia")

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(2.5f * scale, 1f, 1.5f, Color.BLACK)
        }
        val maxTextWidth = result.width - 4f * pad
        val wrappedLines = lines.flatMap { wrapText(it, paint, maxTextWidth) }
        val lineHeight = textSize + gap
        val contentWidth = wrappedLines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val panelWidth = (contentWidth + 2f * pad).coerceAtMost(result.width - 2f * pad)
        val panelHeight = pad * 1.5f + lineHeight * wrappedLines.size
        val isTop = position.startsWith("TOP")
        val isRight = position.endsWith("RIGHT")
        val left = if (isRight) result.width - pad - panelWidth else pad
        val right = left + panelWidth
        val top = if (isTop) pad else result.height - pad - panelHeight
        val panel = RectF(left, top, right, top + panelHeight)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(175, 0, 0, 0) }
        canvas.drawRoundRect(panel, 12f * scale, 12f * scale, background)
        var baseline = top + pad * 0.75f + textSize
        wrappedLines.forEachIndexed { index, line ->
            paint.textSize = if (index == 0) textSize * 1.08f else textSize
            val x = if (isRight) right - pad - paint.measureText(line) else left + pad
            canvas.drawText(line, x.coerceAtLeast(left + pad), baseline, paint)
            baseline += lineHeight
        }
        return result
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val output = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                output.add(line)
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) output.add(line)
        return output
    }

    private fun newImageValues(filename: String): ContentValues {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KameraLokasiGPS/")
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "KameraLokasiGPS"
            )
            if (!directory.exists()) directory.mkdirs()
            values.put(MediaStore.Images.Media.DATA, File(directory, filename).absolutePath)
        }
        return values
    }

    private fun finishPendingImage(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            contentResolver.update(uri, values, null, null)
        }
    }

    private fun saveOriginalFileToGallery(file: File): Uri? {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) return null
        val filename = "KameraLokasi_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newImageValues(filename)
        ) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IllegalStateException("Tidak dapat membuka penyimpanan")
            finishPendingImage(uri)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) return null
        val filename = "KameraLokasi_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newImageValues(filename)
        ) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw IllegalStateException("Gagal mengompres foto")
                }
            } ?: throw IllegalStateException("Tidak dapat membuka penyimpanan")
            finishPendingImage(uri)
            uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun showStatus(message: String) {
        if (::statusText.isInitialized) statusText.text = message
    }

    override fun onDestroy() {
        locationListener?.let {
            try { locationManager.removeUpdates(it) } catch (_: Exception) { }
        }
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

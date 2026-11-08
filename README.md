# Kamera Lokasi GPS — versi 2

Aplikasi Android native berbasis Kotlin + CameraX untuk memotret dengan watermark lokasi/waktu atau menyimpan JPEG asli tanpa watermark.

## Fitur
- Preview kamera dan tombol ambil foto.
- Beralih antara kamera belakang dan depan (jika perangkat mendukungnya).
- Sakelar watermark aktif/nonaktif; pilihan disimpan di preferensi aplikasi.
- Watermark berisi hari, tanggal, jam, nama area/kota jika layanan Geocoder tersedia, koordinat, dan akurasi GPS.
- Pengaturan posisi watermark (empat sudut) dan ukuran tulisan (lima tingkat), tersimpan setelah aplikasi ditutup.
- Foto tersimpan ke `Pictures/KameraLokasiGPS`.
- Galeri/riwayat internal aplikasi menampilkan foto dalam folder tersebut; ketuk thumbnail untuk membukanya dengan aplikasi galeri/peraga foto perangkat.
- Saat watermark nonaktif, JPEG asli disalin apa adanya agar EXIF/orientasi kamera tetap terjaga. Saat watermark aktif, orientasi EXIF diterapkan sebelum teks digambar.
- Mendukung penyimpanan scoped storage Android 10+ dan jalur MediaStore lama untuk Android 8/9.

## Cara membuka dan membangun APK
1. Ekstrak ZIP proyek.
2. Buka folder `KameraLokasiGPS` melalui Android Studio.
3. Pastikan Android Studio menggunakan JDK 17 dan Android SDK Platform 35.
4. Jalankan Gradle Sync. Koneksi internet diperlukan pada build pertama untuk mengambil plugin dan dependensi Gradle.
5. Hubungkan perangkat Android atau jalankan emulator yang memiliki kamera.
6. Berikan izin kamera dan lokasi. Pada Android 8/9 aplikasi juga meminta izin penyimpanan.
7. Pilih **Build → Build Bundle(s) / APK(s) → Build APK(s)**. APK debug biasanya berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan pengujian dan batasan
- Proyek ini adalah source project, bukan APK yang sudah ditandatangani.
- Build Android tidak dapat dijalankan di lingkungan pembuatan paket ini karena Android SDK/Gradle belum tersedia. Karena itu belum ada klaim bahwa APK berhasil dikompilasi atau diuji di perangkat.
- Nama alamat bergantung pada ketersediaan layanan Geocoder di perangkat; jika tidak tersedia, koordinat GPS tetap dicetak.
- Untuk akurasi GPS yang baik, aktifkan lokasi dan gunakan perangkat di area terbuka. Jika izin lokasi ditolak atau sinyal tidak tersedia, foto tetap dapat diambil dan watermark menyatakan koordinat tidak tersedia.
- Galeri aplikasi hanya menampilkan foto yang disimpan ke folder `Pictures/KameraLokasiGPS`.

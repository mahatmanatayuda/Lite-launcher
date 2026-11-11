# Lite Launcher

Launcher Android yang ringan dengan fitur ala Nova Launcher (termasuk fitur setara Prime). Dibuat dari nol,
bukan modifikasi Nova. Tanpa Compose dan AppCompat: hanya `core-ktx` dan `RecyclerView`.

## Fitur
- Beberapa halaman home (geser kiri/kanan), grid bisa diatur (3–8 kolom/baris)
- Dock dengan 3–7 ikon
- Drag & drop: pindahkan ikon, taruh di atas ikon lain untuk membuat folder
- Tarik ke tepi layar untuk pindah/menambah halaman
- Bar aksi saat drag: Hapus, Info aplikasi, Copot
- Lepas ikon di tempat semula untuk membuka menu (ubah ikon, hapus, info, copot)
- Folder: ganti nama, tarik keluar
- App drawer: geser ke atas, pencarian, sembunyikan aplikasi
- **Grup di app drawer**: kelompokkan aplikasi, tampil sebagai tab di atas daftar
  (tahan tab untuk menghapus grup)
- **Ubah ikon sesuka hati**, per aplikasi: dari icon pack, dari gambar di galeri
  (dipotong persegi, mengikuti bentuk ikon), atau kembalikan ke bawaan
- **Ganti nama aplikasi** (tampil di home, folder, drawer, dan pencarian)
- **Icon pack** (Nova/ADW/Apex/GO)
- **Bentuk ikon**: lingkaran, squircle, atau persegi membulat
- **Gestur** yang bisa diatur: geser ke atas, geser ke bawah, ketuk dua kali
  (tidak ada / app drawer / notifikasi / pengaturan)
- **Titik notifikasi** pada ikon (perlu izin akses notifikasi)
- **Kunci desktop**: ikon tidak bisa dipindah tanpa sengaja
- **Backup & restore** tata letak dan semua pengaturan ke file JSON
  (gambar ikon dari galeri tidak ikut tersimpan di backup)
- **Impor backup Nova** (`.novabackup`): halaman, posisi ikon, folder, dan dock
- Cache ikon dibatasi 8 MB

## Belum ada
Widget, loop halaman tanpa ujung, aksi geser pada ikon, dan pengaturan Nova selain tata letak
(preferensi di XML backup belum dibaca; ikon kustom Nova juga belum ikut terimpor).
Bentuk ikon belum berlaku untuk ikon non-adaptive.

## Rilis
Setiap kali versi dinaikkan (`versionName` di `app/build.gradle.kts`) dan di-push ke `main`,
GitHub Actions membuild APK dan menerbitkannya di bagian **Releases** sebagai `vX.Y`.

APK ditandatangani dengan kunci debug kecuali kamu menyimpan kunci sendiri di *Settings → Secrets and
variables → Actions*: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Dengan kunci sendiri, versi baru bisa langsung menimpa versi lama tanpa uninstall.

## Pasang sebagai launcher utama
Unduh APK dari *Releases* → instal → tekan Home → pilih *Lite Launcher* → *Selalu*.

# Lite Launcher

Launcher Android yang ringan dengan fitur ala Nova Launcher (termasuk fitur setara Prime). Dibuat dari nol,
bukan modifikasi Nova. Tanpa Compose dan AppCompat: hanya `core-ktx` dan `RecyclerView`.

## Fitur
- **Panduan setup awal**: muncul otomatis saat pertama kali launcher dibuka pada pemasangan baru, dan
  tersedia juga sebagai ikon terpisah "Lite Launcher Setup". Pilih ukuran grid, bentuk ikon, icon pack,
  gestur, dan opsi tambahan, atau langsung impor backup Nova / backup Lite Launcher, lalu jadikan
  launcher utama. Bisa dijalankan lagi dari Pengaturan.
- Beberapa halaman home (geser kiri/kanan), grid bisa diatur (3–8 kolom/baris)
- **Loop halaman**: dari halaman terakhir geser lagi untuk kembali ke halaman pertama (opsional)
- **Widget**: tahan area kosong di home → *Widget*. Tahan widget untuk memindahkan, lepas di tempat
  semula untuk mengubah ukuran (lebih lebar / sempit / tinggi / pendek) atau menghapus
- Dock dengan 3–7 ikon
- Drag & drop: pindahkan ikon, taruh di atas ikon lain untuk membuat folder
- Tarik ke tepi layar untuk pindah/menambah halaman
- Bar aksi saat drag: Hapus, Info aplikasi, Copot
- Lepas ikon di tempat semula untuk membuka menu (ubah ikon, hapus, info, copot)
- **Geser ke atas pada ikon**: buka menu ikon atau info aplikasi (pilih di Pengaturan)
- Folder: ganti nama, tarik keluar
- App drawer: geser ke atas, pencarian, sembunyikan aplikasi. Kalau tidak ada aplikasi yang cocok,
  tersedia pintasan **cari di web** (juga lewat tombol cari di keyboard)
- **Grup di app drawer**: kelompokkan aplikasi, tampil sebagai tab di atas daftar
  (tahan tab untuk menghapus grup)
- **Ubah ikon sesuka hati**, per aplikasi: dari icon pack, dari gambar di galeri
  (dipotong persegi, mengikuti bentuk ikon), atau kembalikan ke bawaan
- **Ganti nama aplikasi** (tampil di home, folder, drawer, dan pencarian)
- **Icon pack** (Nova/ADW/Apex/GO)
- **Bentuk ikon**: lingkaran, squircle, atau persegi membulat. Berlaku untuk ikon adaptive, ikon
  lama (diletakkan di atas alas terang), dan gambar dari galeri
- **Gestur** yang bisa diatur: geser ke atas, geser ke bawah, ketuk dua kali
  (tidak ada / app drawer / notifikasi / pengaturan)
- **Titik notifikasi** pada ikon (perlu izin akses notifikasi)
- **Kunci desktop**: ikon tidak bisa dipindah tanpa sengaja
- **Backup & restore** tata letak dan semua pengaturan ke file JSON
  (gambar ikon dari galeri dan widget tidak ikut tersimpan di backup)
- Tata letak home disimpan dengan **salinan cadangan otomatis**; kalau file utamanya rusak, salinan
  itu dipakai
- **Impor backup Nova** (`.novabackup`): halaman, posisi ikon, folder, dan dock
- Label aksesibilitas untuk ikon (berguna untuk pembaca layar)
- Cache ikon dibatasi 8 MB

## Belum ada
Impor widget, ikon kustom, dan preferensi dari backup Nova (hanya tata letak ikon yang diimpor).
Menarik ikon langsung dari app drawer ke home (sekarang lewat menu *Tambah ke home*). Ukuran widget
diatur per kotak, belum bisa ditarik bebas.

## Rilis
Setiap kali versi dinaikkan (`versionName` di `app/build.gradle.kts`) dan di-push ke `main`,
GitHub Actions membuild APK dan menerbitkannya di bagian **Releases** sebagai `vX.Y`.

APK ditandatangani dengan kunci debug kecuali kamu menyimpan kunci sendiri di *Settings → Secrets and
variables → Actions*: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Dengan kunci sendiri, versi baru bisa langsung menimpa versi lama tanpa uninstall.

## Pasang sebagai launcher utama
Unduh APK dari *Releases* → instal → buka ikon **Lite Launcher Setup** dan ikuti langkahnya
(atau tekan Home → pilih *Lite Launcher* → *Selalu*).

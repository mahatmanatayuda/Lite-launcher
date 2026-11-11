# Lite Launcher

Launcher Android yang ringan dengan fitur ala Nova Launcher. Dibuat dari nol (bukan modifikasi Nova).
Tanpa Compose dan AppCompat: hanya `core-ktx` dan `RecyclerView`.

## Fitur
- Beberapa halaman home (geser kiri/kanan), grid bisa diatur (3–8 kolom/baris)
- Dock dengan 3–7 ikon
- Drag & drop: pindahkan ikon, taruh di atas ikon lain untuk membuat folder
- Tarik ke tepi layar untuk pindah/menambah halaman
- Bar aksi saat drag: Hapus, Info aplikasi, Copot
- Lepas ikon di tempat semula untuk membuka menu (ubah ikon, hapus, info, copot)
- Folder: ganti nama, tarik keluar
- App drawer: geser ke atas, pencarian, sembunyikan aplikasi
- **Icon pack** (Nova/ADW/Apex/GO): pilih icon pack, dan ganti ikon per aplikasi
- **Bentuk ikon**: lingkaran, squircle, atau persegi membulat (untuk ikon adaptive)
- **Ketuk dua kali di home**: buka app drawer, notifikasi, atau pengaturan (bisa dipilih)
- **Impor backup Nova** (`.novabackup`): halaman, posisi ikon, folder, dan dock
- Geser ke bawah di home: panel notifikasi
- Cache ikon dibatasi 8 MB

## Belum ada
Widget, badge notifikasi, dan pengaturan Nova selain tata letak (preferensi di XML backup belum
dibaca). Ikon kustom Nova juga belum ikut terimpor. Bentuk ikon belum berlaku untuk ikon non-adaptive
(ikon lama tetap tampil seperti aslinya).

## Build
Push ke GitHub (branch `main`) → tab *Actions* → *Build APK* → unduh artifact.
Atau buka folder ini di Android Studio. APK release ditandatangani dengan debug key (pemakaian pribadi).

## Pasang sebagai launcher utama
Instal APK → tekan Home → pilih *Lite Launcher* → *Selalu*.

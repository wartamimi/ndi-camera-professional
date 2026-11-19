# Jawaban formulir Data Safety (Keamanan Data) Google Play – LCS Cam

Play Console > Kebijakan dan program > Konten aplikasi > Keamanan data. Jawaban ini sesuai dengan kode di repo ini per 19 November 2026 dan dengan [kebijakan privasi](PRIVACY_POLICY.md). Perbarui jika aplikasi menambah SDK analitik, iklan, crash reporting, login, atau server sendiri.

## Langkah 1 – Pengumpulan dan keamanan data
| Pertanyaan | Jawaban |
|---|---|
| Apakah aplikasi Anda mengumpulkan atau membagikan salah satu jenis data pengguna yang diwajibkan? | **Tidak** |

Setelah menjawab "Tidak", pertanyaan enkripsi dan jenis data tidak muncul lagi. Listing akan menampilkan "Tidak ada data yang dikumpulkan" dan "Tidak ada data yang dibagikan dengan pihak ketiga".

### Dasar jawaban "Tidak"
- **Video dan audio**: tidak pernah dikirim ke server pengembang atau pihak ketiga. Stream NDI dikirim langsung dari HP ke penerima milik pengguna sendiri di jaringan lokalnya (atau ke IP yang dia masukkan sendiri), hanya setelah pengguna menekan mulai. Aplikasi tidak merekam ke penyimpanan dan tidak mengunggah ke internet.
- **Pengaturan dan status paket**: hanya disimpan di HP (DataStore/SharedPreferences, `allowBackup=false`). Data yang hanya diproses di perangkat tidak dihitung sebagai "dikumpulkan".
- **Pembelian**: diproses oleh Google Play Billing. Aplikasi tidak punya server dan tidak mengirim data pembelian ke mana pun. Data yang ditangani Google Play sendiri tidak perlu dideklarasikan oleh developer.
- **Info jaringan** (jenis koneksi, RSSI Wi-Fi, IP lokal): hanya ditampilkan di layar diagnostik, tidak disimpan atau dikirim. Tidak ada izin lokasi.
- **Tidak ada** SDK analitik, iklan, Firebase/Crashlytics, atau login di `app/build.gradle.kts`.

## Langkah 2 – Bagian lain di "Konten aplikasi"
| Bagian | Jawaban |
|---|---|
| Kebijakan privasi | `https://wartamimi.github.io/ndi-camera-professional/privacy/` |
| Iklan | Tidak, aplikasi tidak berisi iklan |
| Akses aplikasi | Semua fitur tersedia tanpa akses khusus (tidak ada login). Catatan untuk reviewer: fitur berbayar dibuka lewat Google Play Billing; streaming butuh penerima NDI (mis. OBS + plugin DistroAV, atau NDI Studio Monitor) di jaringan yang sama |
| Target audiens | 18 tahun ke atas (atau 16+); bukan untuk anak-anak |
| Aplikasi berita | Tidak |
| ID iklan (Advertising ID) | Tidak menggunakan ID iklan |
| Izin layanan latar depan | Kamera + mikrofon: streaming NDI langsung yang dimulai pengguna, tetap berjalan saat layar mati, dengan notifikasi dan tombol Stop |
| Penghapusan akun | Tidak berlaku (tidak ada akun) |

## Jika suatu saat menambah fitur
Jika aplikasi menambah Crashlytics/analitik, akun, upload ke cloud, atau streaming ke server internet, jawaban Langkah 1 harus diubah menjadi "Ya" dan jenis data terkait (mis. "Log error", "ID perangkat", "Video", "Rekaman suara") harus dideklarasikan, serta kebijakan privasi diperbarui.

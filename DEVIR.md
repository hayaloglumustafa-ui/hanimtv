# HanımTV — devir notu

**Son durum (29.10.2026):** Sürüm 1.0.33, kanal listesi sürüm 31, 318 kanal.

**Yapılanlar:**
- Klasik kanal değiştirme, sabit sıra (1..271), kanal listesi (orta tuş), rakamla kanal seçimi
- Soldan açılan menü (sol tuş): Son izlenenler, Favoriler, Kategoriler, Tüm kanallar, Tema, Güncellemeler, Ayarlar
- 4 tema: Bordo-altın (varsayılan), Lacivert-altın, Siyah-beyaz, Zümrüt yeşili
- Geri tuşu: önceki kanala dönüş; sağ tuş: kanal bilgisi
- Yayın akışı: `araclar/epg.py` 6 saatte bir `epg.json` üretir (80 kanal); stick 3 saatte bir indirir
- Ayarlar: kanal testi, şeffaflık, yazı boyutu, açılışta başla, açılmayanları gizle, uyku zamanlayıcısı, görüntü (orijinal/doldur/uzat), kumanda rehberi
- Kendi kendine güncelleme: kanal listesi Wi-Fi ile kendiliğinden, uygulama "Yeni sürümü kur" ile

**29.10 eklenenler:** Yayın akışı ekranı (menü) + program hatırlatıcı (5 dk önce, OK ile geç). Reklam kaçışı: izlenen kanalın HLS listesinde son 3 parça "_era"/"/crea/" ise önceki kanala geçer, son 2 parça normal olunca döner (en fazla 12 dk). Ayarlar'dan kapatılabilir. 29.10 NOW sitesinde canlı inceleme: NOW listesi reklamı EXTINF başlığında ADV-IN / ADV-OUT ile işaretliyor, "_era" parçaları; kuşak arada ~60 sn kanal tanıtımıyla bölünüyor. Bu yüzden kaçış oynatıcı gecikmesi kadar (currentLiveOffset) bekleyip giriyor, dönüş için listenin sonunda en az 70 sn kesintisiz program arıyor. NOW sitesi reklam arasında kararma üstüne Google IMA ile kendi internet reklamını gösteriyor; bunu taklit etmiyoruz (izinsiz reklam isteği). Test (araclar/reklam_izle.py): NOW "_era.ts", Kanal D ve Show TV "/crea/" parçaları + DATERANGE X-DAION işaretleri kullanıyor; Star TV'de 25 dk'da işaret görülmedi.

**Deneme (29.10):** Ayarlar'da "NOW'u sitesinin oynatıcısıyla aç" (varsayılan kapalı). JSON'da `siteOynatici` alanı olan kanal WebView'da açılır, SITE_JS oynatıcıyı tam ekran yapıp gerisini gizler; sadece o alan adına gidilir; bu modda reklam kaçışı devre dışı. Stick'te ilk deneme (1.0.32): site reklamı oynadı ama reklamdan sonra yayın durdu (gri oynat resmi). 1.0.33'te düzeltildi (reklam modunda değilse her 3 sn play, varsayılan poster gizli) ama denenmedi; sahibi 29.10'da "şimdilik dursun" dedi.

**Özbekistan (29.10):** 272-305, kategori "Özbekistan"; kaynak iptv-org uz listesi. Çoğu cinerama.uz (Özbek OTT platformu) üzerinden, BIZ TV ve Milliy kendi sunucuları önde. Türkiye'den açılıp açılmadığı stick'teki kanal testiyle görülecek (bölge kısıtı olabilir).

**Kazakistan ve Kırgızistan (29.10):** 306-334. Sadece kanalların/devlet kurumlarının kendi sunucuları (qazcdn, kaztrk bölge kanalları, ktk.kz, adapto, kendi alan adları) alındı; uplink.kz, mcquack, mediabay, gohoski, 120.70.60.179 gibi aracı/yeniden yayın kaynakları alınmadı. 13 kaztrk.kz bölge kanalı Türkiye'den 403 verdi (bölge kısıtı), çıkarıldı. Khabar, 24KZ, Astana TV, 7 Kanal'ın resmi açık linki bulunamadı. Kırgızistan'daki 3 kanal (TV1 KG, 312 Kino, 312 TV) stick'te açılmadı, çıkarıldı. NOW sayfasında YouTube tanıtım videosu bulunup YouTube açılıyordu: sabit linki olan kanalda sayfadan gelen YouTube yok sayılıyor (1.0.34).

**Bekleyen işler:** Yok. İsteğe bağlı: Avrupa ve yerel kanallar için yayın akışı kaynağı.

**Dikkat:**
- İmza dosyası ve şifresinin yedeği sahibinin bilgisayarında, anahtarlar\HanimTV klasöründe. Şifre GitHub'da `HANIMTV_IMZA_SIFRESI` gizli ayarında. Bu ayar değiştirilmemeli.
- Kanal ekleme ya da düzeltme sadece `app/src/main/assets/kanallar.json` ile yapılır ve yeni sürüm gerektirmez. "no" sırası bozulmamalı.
- Ücretli, korsan ya da şifreli kanal eklenmez. Üçüncü kişilerin yeniden yayın linkleri (freeott, iptvperu, smartplaytv, onrender, github raw vb.) 29.10'da temizlendi; reklamda kesilmeyen yayınlar bu tür linkler olduğu için eklenmez.
- Donma bekçisi: görüntü 8 sn ilerlemezse sonraki linke geçer.
- TRT Belgesel donması 1.0.22 ile düzeldi (sahibi onayladı): resmi düz link önde, DAI link yedek.
- NOW, Star, Kanal D'nin resmi uygulamaları sadece telefon/tablet içindir, Android TV sürümü yok (29.10 araştırıldı).
- `araclar/akis_testi.sh` + `Akis testi` iş akışı: GitHub ABD'de olduğu için TRT gibi Türkiye'ye kısıtlı yayınları test edemez.
- NOW ve Star kanallarındaki reklam ekranı kanalın kendi yayınından geliyor, çözülemez.

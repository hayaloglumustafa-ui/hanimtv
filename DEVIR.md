# HanımTV — devir notu

**Son durum (29.10.2026):** Sürüm 1.0.22, kanal listesi sürüm 25, 271 kanal.

**Yapılanlar:**
- Klasik kanal değiştirme, sabit sıra (1..271), kanal listesi (orta tuş), rakamla kanal seçimi
- Soldan açılan menü (sol tuş): Son izlenenler, Favoriler, Kategoriler, Tüm kanallar, Tema, Güncellemeler, Ayarlar
- 4 tema: Bordo-altın (varsayılan), Lacivert-altın, Siyah-beyaz, Zümrüt yeşili
- Geri tuşu: önceki kanala dönüş; sağ tuş: kanal bilgisi
- Yayın akışı: `araclar/epg.py` 6 saatte bir `epg.json` üretir (80 kanal); stick 3 saatte bir indirir
- Ayarlar: kanal testi, şeffaflık, yazı boyutu, açılışta başla, açılmayanları gizle, uyku zamanlayıcısı, görüntü (orijinal/doldur/uzat), kumanda rehberi
- Kendi kendine güncelleme: kanal listesi Wi-Fi ile kendiliğinden, uygulama "Yeni sürümü kur" ile

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

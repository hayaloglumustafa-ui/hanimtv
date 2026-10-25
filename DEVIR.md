# HanımTV — devir notu

**Son durum (25.10.2026):** Sürüm 1.0.19, kanal listesi sürüm 24, 271 kanal.

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
- Ücretli, korsan ya da şifreli kanal eklenmez.
- NOW ve Star kanallarındaki reklam ekranı kanalın kendi yayınından geliyor, çözülemez.

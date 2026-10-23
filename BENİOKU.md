# HanımTV

Xiaomi Mi TV Stick için kişisel canlı TV uygulaması. Sadece ücretsiz yayın yapan kanalların resmi internet yayınlarını gösterir.

## Kumanda

| Tuş | Yayın izlerken | Kanal listesi açıkken |
|---|---|---|
| Yukarı | Sonraki kanal | Bir üst satır |
| Aşağı | Önceki kanal | Bir alt satır |
| OK | Kanal listesini açar | Seçili kanala geçer |
| Sağ / Sol | Kanal bilgisini gösterir | Sonraki / önceki 10 kanal |
| Geri | Kanal bilgisini gösterir (uygulamadan çıkarmaz) | Listeyi kapatır |
| Ev tuşu | Stick'in ana ekranına döner | — |

## Kanal listesi

Liste `app/src/main/assets/kanallar.json` dosyasındadır. Her kanalın `no` değeri sırasını belirler ve sıra sadece bu dosya değişince değişir.

- Dosya değiştiğinde uygulama yeni listeyi en geç 6 saat içinde Wi-Fi üzerinden kendiliğinden alır. Uygulamayı kapatıp açmak beklemeyi kısaltır.
- Bir kanala birden fazla link yazılabilir. İlki çalışmazsa uygulama sıradakini dener.
- Bozuk ya da eksik bir dosya gelirse uygulama onu kabul etmez, son sağlam listeyle çalışır.

## Kurulum

1. Mi TV Stick'te Play Store'dan **Downloader** uygulamasını kurun.
2. Ayarlar → Cihaz Tercihleri → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar → Downloader'a izin verin.
3. Downloader'a şu adresi yazın:
   `https://github.com/hayaloglumustafa-ui/hanimtv/releases/latest/download/HanimTV.apk`
4. "Yükle" deyin. HanımTV, stick her açıldığında kendiliğinden başlar.

## Güncellemeler

Kodda değişiklik olunca GitHub yeni APK'yı kendiliğinden üretir. Kanal listesinde en alta "Yeni sürümü kur" satırı çıkar; OK'a basınca kurulum ekranı açılır. İlk seferde HanımTV'ye "uygulama yükleme" izni vermek gerekir.

## İmza anahtarı

`imza/hanimtv.p12` dosyası şifrelidir. Şifresi bu depoda değil, GitHub'daki `HANIMTV_IMZA_SIFRESI` gizli ayarında durur. Şifreyi kaybetmeyin: yeni sürümler eski uygulamanın üzerine sadece aynı anahtarla kurulabilir.

package tr.hayaloglu.hanimtv

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** İnternetten küçük dosyaları güvenli şekilde indiren yardımcı. */
object Ag {
    const val TARAYICI =
        "Mozilla/5.0 (Linux; Android 9; Mi TV Stick) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    private fun baglan(adres: String, zamanAsimi: Int): HttpURLConnection {
        val b = URL(adres).openConnection() as HttpURLConnection
        b.connectTimeout = zamanAsimi
        b.readTimeout = zamanAsimi
        b.instanceFollowRedirects = true
        b.useCaches = false
        b.setRequestProperty("User-Agent", TARAYICI)
        return b
    }

    /** Metin dosyası indirir. Hata olursa ya da dosya [enFazlaBayt] değerinden büyükse null döner. */
    fun metinIndir(adres: String, enFazlaBayt: Int = 2_000_000, zamanAsimi: Int = 10_000): String? {
        var b: HttpURLConnection? = null
        return try {
            b = baglan(adres, zamanAsimi)
            if (b.responseCode !in 200..299) return null
            val cikti = ByteArrayOutputStream()
            val tampon = ByteArray(8192)
            b.inputStream.use { giris ->
                while (true) {
                    val n = giris.read(tampon)
                    if (n < 0) break
                    cikti.write(tampon, 0, n)
                    if (cikti.size() > enFazlaBayt) return null
                }
            }
            cikti.toString("UTF-8")
        } catch (e: Exception) {
            null
        } finally {
            b?.disconnect()
        }
    }

    /** Bir yayın linkinin cevap verip vermediğini hızlıca kontrol eder. */
    fun linkCalisiyorMu(adres: String): Boolean {
        var b: HttpURLConnection? = null
        return try {
            b = baglan(adres, 6_000)
            b.setRequestProperty("Range", "bytes=0-2047")
            val kod = b.responseCode
            if (kod !in 200..399) return false
            if (adres.contains(".m3u8")) {
                val ilk = ByteArray(2048)
                val n = b.inputStream.use { it.read(ilk) }
                n > 0 && String(ilk, 0, n, Charsets.UTF_8).contains("#EXTM3U")
            } else true
        } catch (e: Exception) {
            false
        } finally {
            b?.disconnect()
        }
    }

    /** Dosya indirir; önce geçici dosyaya yazar, tamamlanınca asıl adına taşır. */
    fun dosyaIndir(adres: String, hedef: File, enFazlaBayt: Long = 80_000_000L): Boolean {
        var b: HttpURLConnection? = null
        val gecici = File(hedef.parentFile, hedef.name + ".gecici")
        return try {
            b = baglan(adres, 20_000)
            if (b.responseCode !in 200..299) return false
            var toplam = 0L
            b.inputStream.use { giris ->
                gecici.outputStream().use { cikis ->
                    val tampon = ByteArray(16384)
                    while (true) {
                        val n = giris.read(tampon)
                        if (n < 0) break
                        toplam += n
                        if (toplam > enFazlaBayt) return false
                        cikis.write(tampon, 0, n)
                    }
                }
            }
            hedef.delete()
            gecici.renameTo(hedef)
        } catch (e: Exception) {
            false
        } finally {
            b?.disconnect()
            gecici.delete()
        }
    }
}

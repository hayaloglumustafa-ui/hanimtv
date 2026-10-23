package tr.hayaloglu.hanimtv

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Kanal listesinin tek sahibi.
 *
 * Sıralama kuralı: Kanallar her zaman dosyadaki "no" değerine göre dizilir. Liste ancak
 * sizin GitHub'daki dosyayı değiştirmenizle değişir. Bozuk ya da eksik bir dosya gelirse
 * kabul edilmez; uygulama son sağlam listeyle çalışmaya devam eder.
 */
object KanalDeposu {
    const val UZAK_LISTE =
        "https://raw.githubusercontent.com/hayaloglumustafa-ui/hanimtv/main/app/src/main/assets/kanallar.json"

    private const val KAYIT_DOSYASI = "kanallar.json"
    private const val EN_AZ_KANAL = 40

    /** Önce stick hafızasındaki son sağlam listeyi, o da yoksa uygulamanın içindekini yükler. */
    fun yukle(ctx: Context): List<Kanal> {
        val kayit = File(ctx.filesDir, KAYIT_DOSYASI)
        if (kayit.exists()) {
            val metin = runCatching { kayit.readText(Charsets.UTF_8) }.getOrNull()
            metin?.let { m -> coz(m)?.let { surumuKaydet(ctx, m, false); return it } }
            kayit.delete() // Bozuk kayıt kendini onarır: silinir, içerideki listeye dönülür.
        }
        val ic = ctx.assets.open(KAYIT_DOSYASI).use { it.readBytes().toString(Charsets.UTF_8) }
        surumuKaydet(ctx, ic, false)
        return coz(ic) ?: emptyList()
    }

    /**
     * GitHub'daki listeyi indirir. Geçerliyse ve öncekinden farklıysa hafızaya kaydedip döndürür.
     * Değişiklik yoksa ya da dosya geçersizse null döner.
     */
    fun uzaktanGuncelle(ctx: Context, mevcut: List<Kanal>): List<Kanal>? = uzaktanKontrol(ctx, mevcut).liste

    /** [basarili]: liste indirilip doğrulandı mı. [liste]: sadece öncekinden farklıysa dolu gelir. */
    class Sonuc(val basarili: Boolean, val liste: List<Kanal>?)

    fun uzaktanKontrol(ctx: Context, mevcut: List<Kanal>): Sonuc {
        val metin = Ag.metinIndir(UZAK_LISTE) ?: return Sonuc(false, null)
        val yeni = coz(metin) ?: return Sonuc(false, null)
        surumuKaydet(ctx, metin, zamaniKaydet = true)
        if (yeni == mevcut) return Sonuc(true, null)
        val kayit = File(ctx.filesDir, KAYIT_DOSYASI)
        val gecici = File(ctx.filesDir, "$KAYIT_DOSYASI.gecici")
        gecici.writeText(metin, Charsets.UTF_8)
        // Elektrik kesilse bile yarım dosya kalmaz: yeni dosya tamamen yazıldıktan sonra yer değiştirilir.
        if (!gecici.renameTo(kayit)) {
            gecici.delete()
            return Sonuc(false, null)
        }
        return Sonuc(true, yeni)
    }

    /** Listenin sürüm numarasını (ve istenirse kontrol saatini) Ayarlar'da göstermek için saklar. */
    private fun surumuKaydet(ctx: Context, metin: String, zamaniKaydet: Boolean) {
        val surum = runCatching { JSONObject(metin).optInt("surum", 0) }.getOrDefault(0)
        val e = ctx.getSharedPreferences("hanimtv", Context.MODE_PRIVATE).edit().putInt("listeSurumu", surum)
        if (zamaniKaydet) e.putLong("listeZamani", System.currentTimeMillis())
        e.apply()
    }

    /** Listeyi okur ve sıkı kontrolden geçirir. Bir kural bile bozuksa null döner. */
    fun coz(metin: String): List<Kanal>? {
        return try {
            cozKontrollu(metin)
        } catch (e: Exception) {
            null
        }
    }

    private fun cozKontrollu(metin: String): List<Kanal>? {
        val dizi = JSONObject(metin).getJSONArray("kanallar")
        val liste = ArrayList<Kanal>(dizi.length())
        for (i in 0 until dizi.length()) {
            val o = dizi.getJSONObject(i)
            val linkDizisi = o.optJSONArray("urls")
            val linkler = ArrayList<String>()
            if (linkDizisi != null) {
                for (j in 0 until linkDizisi.length()) {
                    val l = linkDizisi.getString(j).trim()
                    if (l.startsWith("https://") || l.startsWith("http://")) linkler.add(l)
                }
            }
            val isim = o.getString("name").trim()
            if (isim.isEmpty()) return null
            val yt = o.optString("youtube", "").trim().takeIf { it.startsWith("https://www.youtube.com/") }
            val baslikNesnesi = o.optJSONObject("basliklar")
            val basliklar = HashMap<String, String>()
            if (baslikNesnesi != null) {
                for (ad in listOf("Referer", "Origin", "User-Agent")) {
                    baslikNesnesi.optString(ad, "").trim().takeIf { it.isNotEmpty() }?.let { basliklar[ad] = it }
                }
            }
            val sayfa = o.optString("sayfa", "").trim().takeIf { it.startsWith("https://") }
            liste.add(Kanal(o.getInt("no"), isim, linkler, yt, basliklar, sayfa))
        }
        liste.sortBy { it.no }
        val siraDogru = liste.withIndex().all { (i, k) -> k.no == i + 1 }
        return if (liste.size >= EN_AZ_KANAL && siraDogru) liste else null
    }
}

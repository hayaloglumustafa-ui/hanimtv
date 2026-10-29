package tr.hayaloglu.hanimtv

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Yayın akışı: "Şimdi ne var, sırada ne var".
 * GitHub'daki küçük epg.json dosyası 3 saatte bir indirilir ve stick hafızasında saklanır.
 * Dosya bozuk gelirse ya da hiç gelmezse eski akış kullanılır; akış yoksa sadece gösterilmez.
 */
object Epg {
    private const val DOSYA = "epg.json"
    class Program(val bas: Long, val bit: Long, val ad: String)

    @Volatile private var akis: Map<String, List<Program>> = emptyMap()

    fun yukle(ctx: Context) {
        val f = File(ctx.filesDir, DOSYA)
        if (f.exists()) runCatching { coz(f.readText(Charsets.UTF_8)) }.getOrNull()?.let { akis = it }
    }

    /** İnternetten yeni akışı indirir. Başarılıysa true. */
    fun guncelle(ctx: Context): Boolean {
        val metin = Ag.githubDosyasi(DOSYA, enFazlaBayt = 6_000_000) ?: return false
        val yeni = runCatching { coz(metin) }.getOrNull() ?: return false
        val f = File(ctx.filesDir, DOSYA)
        val gecici = File(ctx.filesDir, "$DOSYA.gecici")
        runCatching { gecici.writeText(metin, Charsets.UTF_8); gecici.renameTo(f) }
        akis = yeni
        return true
    }

    private fun coz(metin: String): Map<String, List<Program>>? {
        val o = JSONObject(metin).getJSONObject("akis")
        val m = HashMap<String, List<Program>>()
        for (isim in o.keys()) {
            val d = o.getJSONArray(isim)
            m[isim] = (0 until d.length()).map { i ->
                val p = d.getJSONArray(i)
                Program(p.getLong(0) * 1000, p.getLong(1) * 1000, p.getString(2))
            }
        }
        return m.ifEmpty { null }
    }

    /** Kanalın şu anki ve sonraki programları (bitmiş olanlar hariç). */
    fun programlar(isim: String): List<Program> {
        val simdi = System.currentTimeMillis()
        return akis[isim].orEmpty().filter { it.bit > simdi }
    }

    /** "Şimdi: ..." ve "21:00 ..." satırları. Akış yoksa null. */
    fun yazi(isim: String): String? {
        val l = akis[isim] ?: return null
        val simdi = System.currentTimeMillis()
        val i = l.indexOfFirst { it.bas <= simdi && simdi < it.bit }
        val saat = java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr"))
        val sb = StringBuilder()
        if (i >= 0) sb.append("Şimdi: ").append(l[i].ad)
        val sonraki = l.firstOrNull { it.bas > simdi } ?: return sb.toString().ifEmpty { null }
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(saat.format(java.util.Date(sonraki.bas))).append("  ").append(sonraki.ad)
        return sb.toString()
    }
}

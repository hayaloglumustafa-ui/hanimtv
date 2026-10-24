package tr.hayaloglu.hanimtv

import java.net.URL

/**
 * Bazı kanallar yayın linkini her seferinde değişen bir anahtarla ya da sitelerindeki
 * gömülü bir oynatıcının içinden verir. Bu yardımcı, kanalın kendi canlı yayın sayfasını açıp
 * o anki linki bulur:
 *  1. Sayfanın içinde doğrudan bir yayın linki (.m3u8) arar.
 *  2. Bulamazsa sayfadaki gömülü oynatıcılara (iframe) iki kat derinliğe kadar bakar.
 *  3. Sayfada YouTube canlı yayını varsa "youtube:VIDEO_NO" döndürür.
 */
object SayfadanLink {
    private val M3U8 = Regex("""(?:https?:)?(?:\\?/){2}[^"'\s<>()]+?\.m3u8[^"'\s<>()]*""")
    /** Uzantısı olmayan ama yayın sunucusundaki canlı yayın klasörünü gösteren linkler (örneğin .../live/hls/kanal?token=...). */
    private val HLS_KLASORU = Regex("""https?:(?:\\?/){2}[^"'\s<>()]+?/live/hls/[^"'\s<>()]+""")
    /** Demirören (CNN Türk vb.) servisinin cevabı: sunucu adresi ve anahtarlı yol ayrı ayrı verilir. */
    private val SERVIS_URL = Regex("\"ServiceUrl\"\\s*:\\s*\"(https?://[^\"]+)\"")
    private val GUVENLI_YOL = Regex("\"SecurePath\"\\s*:\\s*\"(/[^\"]+)\"")
    private val IFRAME = Regex("""<iframe[^>]+?(?:data-src|src)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val YOUTUBE = Regex("""youtube(?:-nocookie)?\.com/(?:embed|live)/([A-Za-z0-9_-]{11})""")
    private val ATLA = Regex("""google|doubleclick|facebook|twitter|instagram|recaptcha|adsystem|criteo|taboola|disqus""")

    fun bul(sayfa: String, basliklar: Map<String, String>): String? {
        val ek = HashMap<String, String>()
        basliklar["Referer"]?.let { ek["Referer"] = it }
        basliklar["User-Agent"]?.let { ek["User-Agent"] = it }
        ek["Accept-Language"] = "tr-TR,tr;q=0.9"
        return tara(sayfa, ek, derinlik = 0)
    }

    private fun tara(adres: String, ek: Map<String, String>, derinlik: Int): String? {
        val html = Ag.metinIndir(adres, enFazlaBayt = 3_000_000, ekBasliklar = ek) ?: return null
        val servis = SERVIS_URL.find(html)?.groupValues?.get(1)
        val yol = GUVENLI_YOL.find(html)?.groupValues?.get(1)
        if (servis != null && yol != null) return temizle(servis.trimEnd('/') + yol, adres)
        M3U8.find(html)?.value?.let { return temizle(it, adres) }
        HLS_KLASORU.find(html)?.value?.let { return temizle(it, adres) }
        if (derinlik >= 2) return YOUTUBE.find(html)?.groupValues?.get(1)?.let { "youtube:$it" }

        val altSayfalar = IFRAME.findAll(html)
            .map { temizle(it.groupValues[1], adres) }
            .filter { it.startsWith("http") && !ATLA.containsMatchIn(it) && !YOUTUBE.containsMatchIn(it) }
            .distinct()
            .take(4)
            .toList()
        val altEk = ek + ("Referer" to adres)
        for (alt in altSayfalar) {
            tara(alt, altEk, derinlik + 1)?.let { return it }
        }
        return YOUTUBE.find(html)?.groupValues?.get(1)?.let { "youtube:$it" }
    }

    /** Kaçış karakterlerini temizler ve göreli adresleri tam adrese çevirir. */
    private fun temizle(ham: String, taban: String): String {
        val s = ham.replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&").trim()
        return runCatching { URL(URL(taban), s).toString() }.getOrDefault(s)
    }
}

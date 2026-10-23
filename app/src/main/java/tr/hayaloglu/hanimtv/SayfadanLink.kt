package tr.hayaloglu.hanimtv

/**
 * Bazı kanallar (DMAX, TLC gibi) yayın linkini her seferinde değişen bir anahtarla verir.
 * Bu yardımcı, kanalın kendi canlı yayın sayfasını açıp o anki linki bulur.
 */
object SayfadanLink {
    private val M3U8 = Regex("""https?:(?:\\?/){2}[^"'\s<>]+?\.m3u8[^"'\s<>]*""")

    fun bul(sayfa: String, basliklar: Map<String, String>): String? {
        val ek = HashMap<String, String>()
        basliklar["Referer"]?.let { ek["Referer"] = it }
        basliklar["User-Agent"]?.let { ek["User-Agent"] = it }
        ek["Accept-Language"] = "tr-TR,tr;q=0.9"
        val html = Ag.metinIndir(sayfa, enFazlaBayt = 3_000_000, ekBasliklar = ek) ?: return null
        val ham = M3U8.find(html)?.value ?: return null
        return ham.replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&")
    }
}

package tr.hayaloglu.hanimtv

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Yayını sadece YouTube'da olan kanalların o anki canlı yayınını bulup YouTube uygulamasında açar. */
object YouTube {
    private val VIDEO_NO = Regex("""<link rel="canonical" href="https://www\.youtube\.com/watch\?v=([A-Za-z0-9_-]{11})"""")
    private val CANLI = Regex(""""isLive(Now)?"\s*:\s*true""")

    /**
     * Kanalın "/live" sayfasından şu an yayında olan videonun numarasını bulur.
     * Kanal o anda canlı yayın yapmıyorsa null döner.
     */
    fun canliYayinBul(adres: String): String? {
        val sayfa = Ag.metinIndir(
            adres,
            enFazlaBayt = 3_000_000,
            ekBasliklar = mapOf(
                "Accept-Language" to "tr-TR,tr;q=0.9",
                // Çerez onay sayfasını atlamak için YouTube'un standart "seçim yapıldı" çerezi.
                "Cookie" to "SOCS=CAI",
            ),
        ) ?: return null
        val no = VIDEO_NO.find(sayfa)?.groupValues?.get(1) ?: return null
        return if (CANLI.containsMatchIn(sayfa)) no else null
    }

    /** Önce Android TV'nin YouTube uygulamasını, o yoksa bulunan ilk uygun uygulamayı açar. */
    fun ac(activity: Activity, videoNo: String): Boolean {
        val uri = Uri.parse("https://www.youtube.com/watch?v=$videoNo")
        val tv = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv")
        if (runCatching { activity.startActivity(tv) }.isSuccess) return true
        return runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
    }
}

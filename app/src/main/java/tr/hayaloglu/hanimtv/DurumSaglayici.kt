package tr.hayaloglu.hanimtv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Ana Ekran uygulamasına "kaldığınız yer" bilgisini verir: son izlenen kanalın numarası, adı ve
 * yayın akışındaki şu anki program. Sadece okunur; ve sadece aynı imzayla imzalanmış uygulamalar
 * (yani bizim Ana Ekran) okuyabilir.
 */
class DurumSaglayici : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? {
        val ctx = context ?: return null
        val c = MatrixCursor(arrayOf("no", "isim", "simdi"))
        runCatching {
            val no = ctx.getSharedPreferences("hanimtv", Context.MODE_PRIVATE).getInt("sonKanal", 1)
            val k = KanalDeposu.yukle(ctx).firstOrNull { it.no == no }
            if (k != null) {
                Epg.yukle(ctx)
                val simdi = Epg.yazi(k.isim)?.lineSequence()?.firstOrNull { it.startsWith("Şimdi:") }?.removePrefix("Şimdi:")?.trim()
                c.addRow(arrayOf<Any?>(k.no, k.isim, simdi))
            }
        }
        return c
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

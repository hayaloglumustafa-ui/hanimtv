package tr.hayaloglu.hanimev

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/**
 * Ana Ekran'ın kendini Wi-Fi üzerinden güncellemesi (HanımTV'deki yöntemin aynısı).
 * Yeni sürüm sadece sizin GitHub deponuzdan indirilir; Android de aynı imzayla imzalanmamış
 * bir dosyanın kurulmasına izin vermez.
 */
object Guncelleme {
    private const val IZINLI_ADRES = "https://github.com/hayaloglumustafa-ui/hanimtv/"
    private const val GUNDE_BIR = 6 * 60 * 60 * 1000L

    private fun mevcut(ctx: Context): Int {
        val b = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) b.longVersionCode.toInt() else @Suppress("DEPRECATION") b.versionCode
    }

    /** Arka planda çağrılır. Yeni sürüm varsa indirir ve kurulacak dosyayı döndürür; yoksa null. */
    fun kontrolEtVeIndir(ctx: Context): File? {
        val ayar = ctx.getSharedPreferences("anaekran", Context.MODE_PRIVATE)
        val dosya = File(File(ctx.cacheDir, "guncelleme").apply { mkdirs() }, "AnaEkran.apk")
        val hazirKod = ayar.getInt("hazirKod", 0)
        if (dosya.exists() && hazirKod > mevcut(ctx)) return dosya
        if (System.currentTimeMillis() - ayar.getLong("sonKontrol", 0L) < GUNDE_BIR) return null
        ayar.edit().putLong("sonKontrol", System.currentTimeMillis()).apply()
        val metin = Ag.githubDosyasi("surum-anaekran.json", enFazlaBayt = 10_000) ?: return null
        val o = runCatching { JSONObject(metin) }.getOrNull() ?: return null
        val kod = o.optInt("surumKodu", 0)
        val apk = o.optString("apk", "")
        if (kod <= mevcut(ctx) || !apk.startsWith(IZINLI_ADRES)) return null
        if (!Ag.dosyaIndir(apk, dosya)) return null
        ayar.edit().putInt("hazirKod", kod).apply()
        return dosya
    }

    fun kur(activity: Activity, dosya: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            }
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.dosyalar", dosya)
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Kurulumdan sonra eski dosyayı siler. */
    fun temizle(ctx: Context) {
        val ayar = ctx.getSharedPreferences("anaekran", Context.MODE_PRIVATE)
        if (ayar.getInt("hazirKod", 0) <= mevcut(ctx)) File(ctx.cacheDir, "guncelleme").deleteRecursively()
    }
}

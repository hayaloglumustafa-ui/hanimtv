package tr.hayaloglu.hanimtv

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
 * Uygulamanın kendini Wi-Fi üzerinden güncellemesi.
 *
 * Güvenlik: Yeni sürüm sadece sizin GitHub hesabınızdan indirilir. Android, aynı imza anahtarıyla
 * imzalanmamış bir dosyanın eski uygulamanın üzerine kurulmasına zaten izin vermez.
 */
object Guncelleyici {
    private const val SURUM_ADRESI =
        "https://raw.githubusercontent.com/hayaloglumustafa-ui/hanimtv/main/surum.json"
    private const val IZINLI_ADRES = "https://github.com/hayaloglumustafa-ui/hanimtv/"

    data class YeniSurum(val kod: Int, val apkAdresi: String)

    fun mevcutSurum(ctx: Context): Int {
        val bilgi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) bilgi.longVersionCode.toInt()
        else @Suppress("DEPRECATION") bilgi.versionCode
    }

    fun kontrolEt(ctx: Context): YeniSurum? {
        val metin = Ag.githubDosyasi("surum.json", enFazlaBayt = 10_000) ?: return null
        return try {
            val o = JSONObject(metin)
            val kod = o.getInt("surumKodu")
            val apk = o.getString("apk")
            if (kod > mevcutSurum(ctx) && apk.startsWith(IZINLI_ADRES)) YeniSurum(kod, apk) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Arka planda çağrılır. İndirme başarılıysa kurulacak dosyayı döndürür. */
    fun indir(ctx: Context, surum: YeniSurum): File? {
        val klasor = File(ctx.cacheDir, "guncelleme").apply { mkdirs() }
        val dosya = File(klasor, "HanimTV.apk")
        return if (Ag.dosyaIndir(surum.apkAdresi, dosya)) dosya else null
    }

    /** Ana ekranda çağrılır. Android'in kendi kurulum ekranını açar. */
    fun kur(activity: Activity, dosya: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            // İlk seferde HanımTV'ye "uygulama yükleme" izni verilmesi gerekir.
            val izin = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            runCatching { activity.startActivity(izin) }
            return false
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.dosyalar", dosya)
        val kurulum = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { activity.startActivity(kurulum) }.isSuccess
    }

    /** Kurulumdan sonra kalan eski dosyayı temizler. */
    fun temizle(ctx: Context) {
        File(ctx.cacheDir, "guncelleme").deleteRecursively()
    }
}

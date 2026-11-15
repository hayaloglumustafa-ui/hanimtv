package tr.hayaloglu.hanimev

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Stick'teki bütün uygulamalar: büyük karolar, kumandayla gezilir. En başta Ayarlar durur. */
class Uygulamalar : Activity() {
    class Uygulama(val ad: String, val paket: String, val resim: Drawable?)

    companion object {
        /** Açılabilen uygulamalar (TV ve telefon başlatıcıları), ada göre sıralı, Ana Ekran hariç. */
        fun liste(ctx: Context): List<Uygulama> {
            val pm = ctx.packageManager
            val bulunan = LinkedHashMap<String, Uygulama>()
            for (kategori in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val niyet = Intent(Intent.ACTION_MAIN).addCategory(kategori)
                val sonuc = runCatching { pm.queryIntentActivities(niyet, 0) }.getOrDefault(emptyList())
                for (r in sonuc) {
                    val paket = r.activityInfo.packageName
                    if (paket == ctx.packageName || bulunan.containsKey(paket)) continue
                    val resim = runCatching { r.activityInfo.loadBanner(pm) ?: r.loadIcon(pm) }.getOrNull()
                    bulunan[paket] = Uygulama(r.loadLabel(pm).toString(), paket, resim)
                }
            }
            return bulunan.values.sortedBy { it.ad.lowercase(java.util.Locale("tr")) }
        }
    }

    private fun dpi(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val yazi = runCatching { Typeface.createFromAsset(assets, "fonts/OpenSans.ttf") }.getOrDefault(Typeface.DEFAULT)
        val kaydir = ScrollView(this).apply { setBackgroundColor(Ev.KOYU); isFillViewport = true }
        val icerik = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(48f), dpi(36f), dpi(48f), dpi(36f))
        }
        kaydir.addView(icerik)
        icerik.addView(TextView(this).apply {
            text = "Uygulamalar"
            setTextColor(Ev.ALTIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = yazi
            setPadding(0, 0, 0, dpi(18f))
        })
        val izgara = GridLayout(this).apply { columnCount = 5 }
        icerik.addView(izgara)

        val karoG = (resources.displayMetrics.widthPixels - dpi(96f) - dpi(12f) * 5) / 5
        var ilk: View? = null
        fun karo(ad: String, resim: Drawable?, is_: () -> Unit) {
            val k = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(8f))
                background = GradientDrawable().apply { setColor(Ev.BORDO); cornerRadius = dpi(12f).toFloat() }
                isFocusable = true
                isClickable = true
                setOnClickListener { runCatching { is_() } }
                setOnFocusChangeListener { v, odak ->
                    v.background = GradientDrawable().apply {
                        setColor(Ev.BORDO); cornerRadius = dpi(12f).toFloat()
                        if (odak) setStroke(dpi(4f), Ev.ALTIN)
                    }
                    v.animate().scaleX(if (odak) 1.05f else 1f).scaleY(if (odak) 1.05f else 1f).setDuration(120).start()
                }
            }
            k.addView(ImageView(this).apply {
                setImageDrawable(resim)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (karoG * 0.42f).toInt()))
            k.addView(TextView(this).apply {
                text = ad
                setTextColor(Ev.KREM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = yazi
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dpi(6f), 0, 0)
            })
            izgara.addView(k, GridLayout.LayoutParams().apply {
                width = karoG
                height = (karoG * 0.72f).toInt()
                setMargins(0, 0, dpi(12f), dpi(12f))
            })
            if (ilk == null) ilk = k
        }

        val ayarResmi = runCatching { packageManager.getApplicationIcon("com.android.tv.settings") }.getOrNull()
        karo("Ayarlar", ayarResmi) { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        for (u in liste(this)) {
            karo(u.ad, u.resim) {
                val pm = packageManager
                val niyet = pm.getLeanbackLaunchIntentForPackage(u.paket) ?: pm.getLaunchIntentForPackage(u.paket)
                if (niyet != null) startActivity(niyet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        setContentView(kaydir)
        ilk?.requestFocus()
    }
}

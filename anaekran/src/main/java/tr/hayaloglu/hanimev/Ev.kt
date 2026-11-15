package tr.hayaloglu.hanimev

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Stick'in ana ekranı: solda saat, tarih ve hava; "Kaldığınız yer" kartı (HanımTV) ve dört düğme;
 * sağda aile fotoğrafı.
 *
 * Kendini onarma: Her parça kendi başına çalışır. Fotoğraf yoksa düz renk, hava gelmezse satır gizli,
 * HanımTV'den bilgi gelmezse kart "Televizyonu aç" der. Hiçbir hata ana ekranı kapatmaz.
 */
class Ev : Activity() {
    companion object {
        const val KOYU = 0xFF4A0E20.toInt()
        const val BORDO = 0xFF6E1530.toInt()
        const val ALTIN = 0xFFE8C77A.toInt()
        const val KREM = 0xFFF6E7C1.toInt()
        const val HANIMTV = "tr.hayaloglu.hanimtv"
        private val DURUM = Uri.parse("content://tr.hayaloglu.hanimtv.durum/son")
        private val TR = Locale("tr")

        // Hava durumu için şehir (Open-Meteo, anahtar gerektirmez). Varsayılan İstanbul.
        const val ENLEM = 41.01
        const val BOYLAM = 28.98
        const val SEHIR = "İstanbul"

        /** Düğmeler: görünen ad, denenecek paket adları, bulunamazsa adında aranacak kelime. */
        val DUGMELER = listOf(
            Triple("YouTube", listOf("com.google.android.youtube.tv", "com.google.android.youtube"), "youtube"),
            Triple("beIN Connect", listOf("com.digiturk.iq.mobil", "com.bein.connect", "com.beinsports.connect"), "bein"),
            Triple("Play Store", listOf("com.android.vending"), "play store"),
        )
    }

    private val ana = Handler(Looper.getMainLooper())
    private val arka = Executors.newSingleThreadExecutor()
    private lateinit var saat: TextView
    private lateinit var tarih: TextView
    private lateinit var kart: LinearLayout
    private lateinit var kartBaslik: TextView
    private lateinit var kartKanal: TextView
    private lateinit var kartSimdi: TextView
    private lateinit var guncelleDugmesi: TextView
    private lateinit var foto: ImageView
    private var hava: String? = null
    private var havaZamani = 0L
    private var hazirGuncelleme: File? = null

    private val yazi by lazy { runCatching { Typeface.createFromAsset(assets, "fonts/OpenSans.ttf") }.getOrDefault(Typeface.DEFAULT) }
    private val kalin by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(assets, "fonts/OpenSans.ttf").setFontVariationSettings("'wght' 700").build()
            else Typeface.create(yazi, Typeface.BOLD)
        }.getOrDefault(Typeface.DEFAULT_BOLD)
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kok = FrameLayout(this).apply { setBackgroundColor(KOYU) }
        val ekranY = resources.displayMetrics.heightPixels
        val fotoG = (ekranY * 630f / 1480f).toInt()

        foto = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(BORDO)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        kok.addView(foto, FrameLayout.LayoutParams(fotoG, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))

        val sol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(48f), dpi(40f), dpi(40f), dpi(36f))
        }
        kok.addView(sol, FrameLayout.LayoutParams(resources.displayMetrics.widthPixels - fotoG, ViewGroup.LayoutParams.MATCH_PARENT))

        saat = metin(84f, KREM, kalin).apply { includeFontPadding = false }
        tarih = metin(20f, KREM, yazi).apply { setPadding(dpi(4f), dpi(8f), 0, 0) }
        sol.addView(saat)
        sol.addView(tarih)
        sol.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        guncelleDugmesi = metin(16f, KREM, yazi).apply {
            text = "Ana ekranın yeni sürümü hazır · kurmak için OK"
            setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f))
            background = zemin(BORDO, 8f)
            visibility = View.GONE
            odaklanabilir(this, BORDO) { hazirGuncelleme?.let { Guncelleme.kur(this@Ev, it) } }
        }
        sol.addView(guncelleDugmesi, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(14f) })

        kart = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(24f), dpi(14f), dpi(24f), dpi(16f))
            background = zemin(ALTIN, 14f)
        }
        kartBaslik = metin(17f, KOYU, yazi).apply { text = "Kaldığınız yer" }
        kartKanal = metin(38f, KOYU, kalin).apply { text = "Televizyon"; isSingleLine = true }
        kartSimdi = metin(17f, KOYU, yazi).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END }
        kart.addView(kartBaslik); kart.addView(kartKanal); kart.addView(kartSimdi)
        odaklanabilir(kart, ALTIN) { ac(HANIMTV) }
        sol.addView(kart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(122f)).apply { bottomMargin = dpi(14f) })

        val satir = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tum = DUGMELER.map { (ad, paketler, kelime) -> ad to { acIlk(paketler, kelime) } } + ("Uygulamalar" to {
            startActivity(Intent(this, Uygulamalar::class.java))
        })
        tum.forEachIndexed { i, (ad, is_) ->
            val d = metin(17f, KREM, yazi).apply {
                text = ad
                gravity = Gravity.CENTER
                background = zemin(BORDO, 12f)
            }
            odaklanabilir(d, BORDO) { is_() }
            satir.addView(d, LinearLayout.LayoutParams(0, dpi(84f), 1f).apply { if (i > 0) marginStart = dpi(12f) })
        }
        sol.addView(satir)

        setContentView(kok)
        kart.requestFocus()
        Guncelleme.temizle(this)
    }

    private fun metin(sp: Float, renk: Int, tip: Typeface) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(renk)
        typeface = tip
    }

    private fun zemin(renk: Int, yaricap: Float, kenar: Int = 0) = GradientDrawable().apply {
        setColor(renk)
        cornerRadius = dp(yaricap)
        if (kenar != 0) setStroke(dpi(4f), kenar)
    }

    /** Kumandayla gelinince altın kenar ve hafif büyüme; OK'a basınca iş. */
    private fun odaklanabilir(v: View, renk: Int, is_: () -> Unit) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnClickListener { runCatching { is_() } }
        v.setOnFocusChangeListener { _, odak ->
            v.background = zemin(renk, if (v === kart) 14f else 12f, if (odak) (if (renk == ALTIN) KREM else ALTIN) else 0)
            v.animate().scaleX(if (odak) 1.04f else 1f).scaleY(if (odak) 1.04f else 1f).setDuration(120).start()
        }
    }

    private fun ac(paket: String): Boolean {
        val pm = packageManager
        val niyet = pm.getLeanbackLaunchIntentForPackage(paket) ?: pm.getLaunchIntentForPackage(paket) ?: return false
        return runCatching { startActivity(niyet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Önce bilinen paket adlarını dener; olmazsa uygulama adında kelimeyi arar. */
    private fun acIlk(paketler: List<String>, kelime: String) {
        if (paketler.any { ac(it) }) return
        val bulunan = Uygulamalar.liste(this).firstOrNull { it.ad.lowercase(TR).contains(kelime) }
        if (bulunan != null && ac(bulunan.paket)) return
        android.widget.Toast.makeText(this, "Bu uygulama stick'te kurulu değil", android.widget.Toast.LENGTH_LONG).show()
    }

    private val saatGuncelle: Runnable = object : Runnable { override fun run() {
        val simdi = Date()
        saat.text = SimpleDateFormat("HH:mm", TR).format(simdi)
        val t = SimpleDateFormat("d MMMM EEEE", TR).format(simdi)
        tarih.text = if (hava != null) "$t · $SEHIR $hava" else t
        ana.postDelayed(this, 10_000)
    } }

    override fun onResume() {
        super.onResume()
        ana.post(saatGuncelle)
        fotografiYukle()
        arka.execute {
            val durum = kaldiginizYer()
            val h = if (System.currentTimeMillis() - havaZamani > 30 * 60_000L) havaGetir() else hava
            val g = runCatching { Guncelleme.kontrolEtVeIndir(this) }.getOrNull()
            ana.post {
                if (durum != null) {
                    kartKanal.text = "${durum.first} · ${durum.second}"
                    kartSimdi.text = durum.third?.let { "Şimdi: $it" } ?: ""
                    kartSimdi.visibility = if (durum.third == null) View.GONE else View.VISIBLE
                } else {
                    kartKanal.text = "Televizyon"
                    kartSimdi.text = "Açmak için OK"
                    kartSimdi.visibility = View.VISIBLE
                }
                if (h != null) { hava = h; havaZamani = System.currentTimeMillis() }
                hazirGuncelleme = g
                guncelleDugmesi.visibility = if (g != null) View.VISIBLE else View.GONE
                saatGuncelle.run()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ana.removeCallbacks(saatGuncelle)
    }

    /** HanımTV'den son izlenen kanal: (numara, ad, şimdi oynayan program). */
    private fun kaldiginizYer(): Triple<Int, String, String?>? = runCatching {
        contentResolver.query(DURUM, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else Triple(c.getInt(0), c.getString(1), if (c.isNull(2)) null else c.getString(2))
        }
    }.getOrNull()

    private fun havaGetir(): String? = runCatching {
        val adres = "https://api.open-meteo.com/v1/forecast?latitude=$ENLEM&longitude=$BOYLAM&current=temperature_2m"
        val m = Ag.metinIndir(adres, 20_000, 8_000) ?: return null
        val derece = JSONObject(m).getJSONObject("current").getDouble("temperature_2m")
        "${Math.round(derece)}°"
    }.getOrNull()

    /** Aile fotoğrafı: stick'te bu uygulamanın klasöründeki aile.jpg (bilgisayardan gönderilir, internete konmaz). */
    private fun fotografiYukle() {
        val dosya = File(getExternalFilesDir(null), "aile.jpg")
        if (!dosya.exists()) { foto.setImageDrawable(null); return }
        arka.execute {
            val bmp = runCatching {
                val sinir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(dosya.path, sinir)
                var olcek = 1
                while (sinir.outHeight / (olcek * 2) >= resources.displayMetrics.heightPixels) olcek *= 2
                BitmapFactory.decodeFile(dosya.path, BitmapFactory.Options().apply { inSampleSize = olcek })
            }.getOrNull()
            ana.post { if (bmp != null) foto.setImageBitmap(bmp) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        kart.requestFocus()
    }

    /** Ana ekranda Geri tuşu bir yere götürmez. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

}

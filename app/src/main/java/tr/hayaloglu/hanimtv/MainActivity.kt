package tr.hayaloglu.hanimtv

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class MainActivity : Activity() {

    private companion object {
        const val OGE_GUNCELLEME = -1
        const val OGE_AYARLAR = -2
        const val SAYFA_BOYU = 10
        const val BILGI_SURESI = 3_000L
        const val LISTE_KAPANMA_SURESI = 20_000L
        const val RAKAM_BEKLEME = 1_800L
        const val TAKILMA_SINIRI = 10_000L
        const val KANAL_GECIS_GECIKMESI = 350L
        const val LISTE_GUNCELLEME_ARALIGI = 6 * 60 * 60 * 1000L
        const val SAGLIK_KONTROL_ARALIGI = 30 * 60 * 1000L
        const val SURUM_KONTROL_ARALIGI = 12 * 60 * 60 * 1000L
        const val BEYAZ = 0xFFFFFFFF.toInt()
        const val GRI = 0x80FFFFFF.toInt()
    }

    private lateinit var oynaticiGorunum: PlayerView
    private lateinit var oynatici: ExoPlayer
    private lateinit var bilgiKutusu: View
    private lateinit var bilgiNumara: TextView
    private lateinit var bilgiIsim: TextView
    private lateinit var listeKutusu: View
    private lateinit var listeBaslik: TextView
    private lateinit var durumYazisi: TextView
    private val satirlar = ArrayList<TextView>()
    private lateinit var ayarlar: SharedPreferences

    private val ana = Handler(Looper.getMainLooper())
    private val arkaPlan: ExecutorService = Executors.newFixedThreadPool(4)

    private var kanallar: List<Kanal> = emptyList()
    private var aktif = 0
    private var linkSirasi = 0
    private var hataSayisi = 0
    private var listeSecili = 0
    private var rakamGirisi = ""
    private var yeniSurum: Guncelleyici.YeniSurum? = null
    private var guncellemeIniyor = false
    private var agDinleyici: ConnectivityManager.NetworkCallback? = null

    /** Yayını gelmeyen kanalların numaraları. Kanal listeden çıkmaz, sadece gri görünür. */
    private val calismayanlar: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private val listeAcik get() = listeKutusu.visibility == View.VISIBLE

    // ---------------------------------------------------------------- Açılış

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Yayın açıkken stick ekran koruyucuya ya da uyku moduna geçmez.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        oynaticiGorunum = findViewById(R.id.oynatici)
        bilgiKutusu = findViewById(R.id.bilgiKutusu)
        bilgiNumara = findViewById(R.id.bilgiNumara)
        bilgiIsim = findViewById(R.id.bilgiIsim)
        listeKutusu = findViewById(R.id.listeKutusu)
        listeBaslik = findViewById(R.id.listeBaslik)
        durumYazisi = findViewById(R.id.durumYazisi)
        ayarlar = getSharedPreferences("hanimtv", Context.MODE_PRIVATE)

        yaziTipleriniUygula()
        listeSatirlariniKur()
        gorunumuUygula()

        kanallar = KanalDeposu.yukle(this)
        val sonKanal = ayarlar.getInt("sonKanal", 1)
        aktif = kanallar.indexOfFirst { it.no == sonKanal }.coerceAtLeast(0)
        sonOynayan = aktif

        oynaticiyiKur()
        agDinleyicisiniKur()
        Guncelleyici.temizle(this)

        ana.postDelayed(listeGuncelle, 5_000)
        ana.postDelayed(surumKontrol, 20_000)
        ana.postDelayed(saglikKontrol, 60_000)
    }

    override fun onStart() {
        super.onStart()
        if (kanallar.isNotEmpty()) oynat(aktif, 0, bilgiGoster = true)
    }

    override fun onStop() {
        super.onStop()
        // Ana ekrana dönülünce ses arkada çalmaya devam etmesin.
        ana.removeCallbacks(yenidenDene)
        ana.removeCallbacks(takilmaKontrol)
        oynatici.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ana.removeCallbacksAndMessages(null)
        agDinleyici?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        oynatici.release()
        arkaPlan.shutdownNow()
    }

    private fun yaziTipi(agirlik: Int): Typeface = try {
        if (Build.VERSION.SDK_INT >= 26) {
            Typeface.Builder(assets, "fonts/OpenSans.ttf").setFontVariationSettings("'wght' $agirlik").build()
        } else {
            Typeface.createFromAsset(assets, "fonts/OpenSans.ttf")
        }
    } catch (e: Exception) {
        Typeface.DEFAULT_BOLD
    }

    private lateinit var normalYazi: Typeface
    private lateinit var kalinYazi: Typeface

    private fun yaziTipleriniUygula() {
        normalYazi = yaziTipi(500)
        kalinYazi = yaziTipi(600)
        bilgiNumara.typeface = kalinYazi
        bilgiIsim.typeface = kalinYazi
        listeBaslik.typeface = normalYazi
        durumYazisi.typeface = normalYazi
    }

    private fun listeSatirlariniKur() {
        val kap = findViewById<LinearLayout>(R.id.listeSatirlari)
        val yogunluk = resources.displayMetrics.density
        repeat(SAYFA_BOYU) {
            val t = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(BEYAZ)
                typeface = normalYazi
                isSingleLine = true
                gravity = Gravity.CENTER_VERTICAL
                val y = (7 * yogunluk).toInt()
                val x = (14 * yogunluk).toInt()
                setPadding(x, y, x, y)
            }
            kap.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            satirlar.add(t)
        }
    }

    // ---------------------------------------------------------------- Oynatıcı

    private fun oynaticiyiKur() {
        // 100 Mbps bağlantı için başlangıçta en yüksek kaliteyle açılır; hız düşerse geçici olarak kaliteyi indirir.
        val bantGenisligi = DefaultBandwidthMeter.Builder(this).setInitialBitrateEstimate(20_000_000L).build()
        val tampon = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .build()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Ag.TARAYICI)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
        val kaynak = DefaultMediaSourceFactory(this).setDataSourceFactory(DefaultDataSource.Factory(this, http))

        oynatici = ExoPlayer.Builder(this)
            .setBandwidthMeter(bantGenisligi)
            .setLoadControl(tampon)
            .setMediaSourceFactory(kaynak)
            .build()
        oynaticiGorunum.player = oynatici

        oynatici.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Canlı yayının gerisinde kalındı: en güncel ana atla.
                    oynatici.seekToDefaultPosition()
                    oynatici.prepare()
                } else {
                    sonHata = hataAciklamasi(error)
                    sonrakiLinkiDene()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        ana.removeCallbacks(takilmaKontrol)
                        ana.postDelayed(takilmaKontrol, TAKILMA_SINIRI)
                    }
                    Player.STATE_READY -> {
                        ana.removeCallbacks(takilmaKontrol)
                        hataSayisi = 0
                        durumGizle()
                        kanallar.getOrNull(aktif)?.let { if (calismayanlar.remove(it.no) && listeAcik) listeyiCiz() }
                    }
                    else -> ana.removeCallbacks(takilmaKontrol)
                }
            }
        })
    }

    /** Kanalın istediği ek başlıklarla (Referer, Origin) yayın kaynağı hazırlar. */
    private fun kaynakFabrikasi(basliklar: Map<String, String>): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(basliklar["User-Agent"] ?: Ag.TARAYICI)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setDefaultRequestProperties(basliklar - "User-Agent")
        return DefaultMediaSourceFactory(this).setDataSourceFactory(DefaultDataSource.Factory(this, http))
    }

    /** Ekranda gösterilecek kısa hata sebebi; bana fotoğrafı gönderildiğinde sorunu anlamayı sağlar. */
    private var sonHata = ""

    private fun hataAciklamasi(e: PlaybackException): String {
        var c: Throwable? = e
        while (c != null) {
            if (c is HttpDataSource.InvalidResponseCodeException) return "sunucu kodu ${c.responseCode}"
            c = c.cause
        }
        return e.errorCodeName
    }

    private fun yayinOgesi(link: String): MediaItem {
        val b = MediaItem.Builder().setUri(link)
        val kucuk = link.lowercase()
        when {
            kucuk.contains(".m3u8") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            kucuk.contains(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        return b.build()
    }

    private fun oynat(indeks: Int, link: Int, bilgiGoster: Boolean = false) {
        ana.removeCallbacks(yenidenDene)
        ana.removeCallbacks(takilmaKontrol)
        aktif = indeks
        linkSirasi = link
        val k = kanallar[indeks]
        if (bilgiGoster) bilgiyiGoster(k.no.toString(), k.isim)

        if (k.linkler.isEmpty() && k.youtube != null) {
            youtubeAc(k)
            return
        }
        sonOynayan = indeks
        if (link == 0 && ayarlar.getInt("sonKanal", -1) != k.no) {
            // commit(): elektrik kesilse bile son kanal anında kaydedilmiş olur.
            ayarlar.edit().putInt("sonKanal", k.no).commit()
        }

        if (k.linkler.isEmpty()) {
            oynatici.stop()
            oynatici.clearMediaItems()
            calismayanlar.add(k.no)
            durumGoster("Bu kanalın internet yayını henüz eklenmedi")
            return
        }
        oynatici.setMediaSource(kaynakFabrikasi(k.basliklar).createMediaSource(yayinOgesi(k.linkler[link])))
        oynatici.prepare()
        oynatici.playWhenReady = true
    }

    /** En son normal şekilde oynatılan kanal. YouTube'dan geri dönünce bu kanal açılır. */
    private var sonOynayan = 0
    private var youtubeIstegi = 0

    /**
     * Yayını sadece YouTube'da olan kanallar: kanalın resmi canlı yayını YouTube uygulamasında açılır.
     * Geri tuşuyla HanımTV'ye dönülünce, ondan önce izlenen kanal devam eder.
     */
    private fun youtubeAc(k: Kanal) {
        oynatici.stop()
        oynatici.clearMediaItems()
        durumGoster("${k.isim} YouTube'da açılıyor…")
        val istek = ++youtubeIstegi
        val adres = k.youtube ?: return
        arkaPlan.execute {
            val videoNo = YouTube.canliYayinBul(adres)
            ana.post {
                // Bu arada başka kanala geçildiyse eski isteği yok say.
                if (istek != youtubeIstegi || kanallar.getOrNull(aktif)?.no != k.no) return@post
                if (videoNo == null) {
                    durumGoster("${k.isim} şu an YouTube'da canlı yayın yapmıyor")
                    return@post
                }
                durumGizle()
                if (YouTube.ac(this, videoNo)) {
                    aktif = sonOynayan
                } else {
                    durumGoster("YouTube uygulaması açılamadı")
                }
            }
        }
    }

    /** Kendini onarma: takılan ya da hata veren yayında önce yedek linkler denenir, sonra beklenip baştan denenir. */
    private fun sonrakiLinkiDene() {
        val k = kanallar.getOrNull(aktif) ?: return
        if (linkSirasi + 1 < k.linkler.size) {
            oynat(aktif, linkSirasi + 1)
            return
        }
        calismayanlar.add(k.no)
        if (listeAcik) listeyiCiz()
        hataSayisi++
        durumGoster("Yayın şu an gelmiyor, tekrar deneniyor… (${k.linkler.size} link · $sonHata)")
        val bekleme = (5_000L * hataSayisi).coerceAtMost(30_000L)
        ana.removeCallbacks(yenidenDene)
        ana.postDelayed(yenidenDene, bekleme)
    }

    private val yenidenDene = Runnable { if (kanallar.isNotEmpty()) oynat(aktif, 0) }

    private val takilmaKontrol = Runnable {
        if (oynatici.playbackState == Player.STATE_BUFFERING) {
            sonHata = "yayın 10 saniye gelmedi"
            sonrakiLinkiDene()
        }
    }

    private val gecikmeliOynat = Runnable { oynat(aktif, 0) }

    /** İnternet geri gelince beklemeden yayını yeniden başlatır. */
    private fun agDinleyicisiniKur() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val d = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ana.post {
                    val sorunlu = oynatici.playerError != null || oynatici.playbackState == Player.STATE_IDLE
                    if (sorunlu && kanallar.isNotEmpty() && !isFinishing) {
                        hataSayisi = 0
                        oynat(aktif, 0)
                    }
                }
            }
        }
        if (runCatching { cm.registerDefaultNetworkCallback(d) }.isSuccess) agDinleyici = d
    }

    // ---------------------------------------------------------------- Kanal değiştirme

    private fun kanalDegistir(yon: Int) {
        if (kanallar.isEmpty()) return
        rakamGirisi = ""
        aktif = (aktif + yon + kanallar.size) % kanallar.size
        hataSayisi = 0
        val k = kanallar[aktif]
        bilgiyiGoster(k.no.toString(), k.isim)
        // Art arda basıldığında her kanal için ayrı yükleme yapılmaz; tuş bırakılınca son kanal açılır.
        ana.removeCallbacks(gecikmeliOynat)
        ana.postDelayed(gecikmeliOynat, KANAL_GECIS_GECIKMESI)
    }

    private fun kanalaGit(indeks: Int) {
        ana.removeCallbacks(gecikmeliOynat)
        hataSayisi = 0
        oynat(indeks, 0, bilgiGoster = true)
    }

    private fun rakamBasildi(rakam: Int) {
        if (rakamGirisi.length >= 3) rakamGirisi = ""
        rakamGirisi += rakam
        val no = rakamGirisi.toInt()
        bilgiyiGoster(rakamGirisi, kanallar.firstOrNull { it.no == no }?.isim ?: "")
        ana.removeCallbacks(rakamUygula)
        ana.postDelayed(rakamUygula, RAKAM_BEKLEME)
    }

    private val rakamUygula = Runnable {
        val no = rakamGirisi.toIntOrNull()
        rakamGirisi = ""
        val indeks = kanallar.indexOfFirst { it.no == no }
        if (indeks >= 0) kanalaGit(indeks) else bilgiKutusu.visibility = View.GONE
    }

    // ---------------------------------------------------------------- Bilgi kutusu ve durum yazısı

    private fun bilgiyiGoster(numara: String, isim: String) {
        bilgiNumara.text = numara
        bilgiIsim.text = isim
        bilgiIsim.visibility = if (isim.isEmpty()) View.GONE else View.VISIBLE
        bilgiKutusu.visibility = View.VISIBLE
        ana.removeCallbacks(bilgiyiGizle)
        ana.postDelayed(bilgiyiGizle, BILGI_SURESI)
    }

    private val bilgiyiGizle = Runnable { bilgiKutusu.visibility = View.GONE }

    private fun durumGoster(yazi: String) {
        durumYazisi.text = yazi
        durumYazisi.visibility = View.VISIBLE
    }

    private fun durumGizle() {
        durumYazisi.visibility = View.GONE
    }

    // ---------------------------------------------------------------- Kanal listesi

    /** Listede görünen satırlar: kanal indeksleri, varsa "Yeni sürümü kur", en sonda "Ayarlar". */
    private fun ogeler(): List<Int> {
        val gizle = ayarlar.getBoolean("griGizle", false)
        val l = ArrayList<Int>(kanallar.size + 2)
        for (i in kanallar.indices) {
            if (!gizle || i == aktif || !calismayanlar.contains(kanallar[i].no)) l.add(i)
        }
        if (yeniSurum != null) l.add(OGE_GUNCELLEME)
        l.add(OGE_AYARLAR)
        return l
    }

    private fun listeyiAc() {
        if (kanallar.isEmpty()) return
        ayarModu = false
        listeSecili = ogeler().indexOf(aktif).coerceAtLeast(0)
        listeKutusu.visibility = View.VISIBLE
        bilgiKutusu.visibility = View.GONE
        listeyiCiz()
        listeZamanlayicisi()
    }

    private fun listeyiKapat() {
        listeKutusu.visibility = View.GONE
        ayarModu = false
        ana.removeCallbacks(listeyiKendiligindenKapat)
    }

    private val listeyiKendiligindenKapat = Runnable { listeyiKapat() }

    private fun listeZamanlayicisi() {
        ana.removeCallbacks(listeyiKendiligindenKapat)
        ana.postDelayed(listeyiKendiligindenKapat, LISTE_KAPANMA_SURESI)
    }

    private fun listeyiCiz() {
        if (ayarModu) {
            ayarlariCiz()
            return
        }
        val o = ogeler()
        val toplam = o.size
        listeSecili = listeSecili.coerceIn(0, toplam - 1)
        val sayfa = listeSecili / SAYFA_BOYU
        val sayfaSayisi = (toplam + SAYFA_BOYU - 1) / SAYFA_BOYU
        listeBaslik.text = buildString {
            append("Kanal listesi · ").append(sayfa + 1).append('/').append(sayfaSayisi)
            if (yeniSurum != null) append(" · Yeni sürüm var")
        }
        for (i in 0 until SAYFA_BOYU) {
            val t = satirlar[i]
            val konum = sayfa * SAYFA_BOYU + i
            if (konum >= toplam) {
                t.visibility = View.INVISIBLE
                t.background = null
                continue
            }
            val indeks = o[konum]
            when {
                indeks >= 0 -> {
                    val k = kanallar[indeks]
                    val yt = if (k.linkler.isEmpty() && k.youtube != null) "  · YouTube" else ""
                    t.text = String.format("%-4d %s", k.no, k.isim) + yt
                    t.setTextColor(if (calismayanlar.contains(k.no)) GRI else BEYAZ)
                }
                indeks == OGE_GUNCELLEME -> {
                    t.text = if (guncellemeIniyor) "Yeni sürüm indiriliyor…" else "Yeni sürümü kur"
                    t.setTextColor(BEYAZ)
                }
                else -> {
                    t.text = "Ayarlar"
                    t.setTextColor(BEYAZ)
                }
            }
            t.visibility = View.VISIBLE
            t.typeface = if (indeks == aktif) kalinYazi else normalYazi
            t.setBackgroundResource(if (konum == listeSecili) R.drawable.secili_satir else 0)
        }
    }

    private fun listeTusu(tus: Int): Boolean {
        listeZamanlayicisi()
        if (ayarModu) return ayarTusu(tus)
        val o = ogeler()
        val toplam = o.size
        val sayfaSayisi = (toplam + SAYFA_BOYU - 1) / SAYFA_BOYU
        val satir = listeSecili % SAYFA_BOYU
        val sayfa = listeSecili / SAYFA_BOYU
        when (tus) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP ->
                listeSecili = (listeSecili - 1 + toplam) % toplam
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN ->
                listeSecili = (listeSecili + 1) % toplam
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                listeSecili = (((sayfa + 1) % sayfaSayisi) * SAYFA_BOYU + satir).coerceAtMost(toplam - 1)
            KeyEvent.KEYCODE_DPAD_LEFT ->
                listeSecili = (((sayfa - 1 + sayfaSayisi) % sayfaSayisi) * SAYFA_BOYU + satir).coerceAtMost(toplam - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val indeks = o[listeSecili.coerceIn(0, toplam - 1)]
                when {
                    indeks >= 0 -> {
                        listeyiKapat()
                        if (indeks != aktif) kanalaGit(indeks)
                        else bilgiyiGoster(kanallar[aktif].no.toString(), kanallar[aktif].isim)
                    }
                    indeks == OGE_GUNCELLEME -> guncellemeyiBaslat()
                    else -> {
                        ayarModu = true
                        ayarSecili = 0
                        listeyiCiz()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                listeyiKapat()
                return true
            }
            else -> return true
        }
        listeyiCiz()
        return true
    }

    // ---------------------------------------------------------------- Ayarlar

    private var ayarModu = false
    private var ayarSecili = 0
    private val seffaflikAdlari = arrayOf("Az", "Orta", "Çok")
    private val boyutAdlari = arrayOf("Küçük", "Orta", "Büyük")

    private fun ayarSatirlari(): List<String> {
        val seffaflik = ayarlar.getInt("seffaflik", 1)
        val boyut = ayarlar.getInt("yaziBoyutu", 1)
        val acilis = ayarlar.getBoolean("acilistaBasla", true)
        val gizle = ayarlar.getBoolean("griGizle", false)
        val zaman = ayarlar.getLong("listeZamani", 0L).let {
            if (it == 0L) "—" else java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale("tr")).format(java.util.Date(it))
        }
        return listOf(
            "Kanal listesini şimdi güncelle",
            "Güncellemeleri kontrol et",
            "Şeffaflık: " + seffaflikAdlari[seffaflik],
            "Yazı boyutu: " + boyutAdlari[boyut],
            "Açılışta HanımTV başlasın: " + if (acilis) "Açık" else "Kapalı",
            "Açılmayan kanalları gizle: " + if (gizle) "Açık" else "Kapalı",
            "Sürüm 1.0.${Guncelleyici.mevcutSurum(this)} · Liste ${ayarlar.getInt("listeSurumu", 0)} · $zaman",
        )
    }

    private fun ayarlariCiz() {
        val s = ayarSatirlari()
        ayarSecili = ayarSecili.coerceIn(0, s.size - 1)
        listeBaslik.text = "Ayarlar · Geri tuşu: kanal listesi"
        for (i in 0 until SAYFA_BOYU) {
            val t = satirlar[i]
            if (i >= s.size) {
                t.visibility = View.INVISIBLE
                t.background = null
                continue
            }
            t.text = s[i]
            t.setTextColor(if (i == s.size - 1) GRI else BEYAZ)
            t.typeface = normalYazi
            t.visibility = View.VISIBLE
            t.setBackgroundResource(if (i == ayarSecili) R.drawable.secili_satir else 0)
        }
    }

    private fun ayarTusu(tus: Int): Boolean {
        val adet = ayarSatirlari().size
        when (tus) {
            KeyEvent.KEYCODE_DPAD_UP -> ayarSecili = (ayarSecili - 1 + adet) % adet
            KeyEvent.KEYCODE_DPAD_DOWN -> ayarSecili = (ayarSecili + 1) % adet
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                ayarModu = false
                listeSecili = ogeler().size - 1
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> ayarUygula(ayarSecili)
            else -> return true
        }
        listeyiCiz()
        return true
    }

    private fun ayarUygula(sira: Int) {
        val e = ayarlar.edit()
        when (sira) {
            0 -> listeyiSimdiGuncelle()
            1 -> surumuSimdiKontrolEt()
            2 -> e.putInt("seffaflik", (ayarlar.getInt("seffaflik", 1) + 1) % 3)
            3 -> e.putInt("yaziBoyutu", (ayarlar.getInt("yaziBoyutu", 1) + 1) % 3)
            4 -> e.putBoolean("acilistaBasla", !ayarlar.getBoolean("acilistaBasla", true))
            5 -> e.putBoolean("griGizle", !ayarlar.getBoolean("griGizle", false))
        }
        e.commit()
        if (sira == 2 || sira == 3) gorunumuUygula()
    }

    /** Seçilen şeffaflığı ve yazı boyutunu bütün kutulara uygular. */
    private fun gorunumuUygula() {
        val opaklik = intArrayOf(0xBF, 0x8C, 0x59)[ayarlar.getInt("seffaflik", 1).coerceIn(0, 2)]
        val oran = floatArrayOf(0.85f, 1f, 1.2f)[ayarlar.getInt("yaziBoyutu", 1).coerceIn(0, 2)]
        val yaricap = 12 * resources.displayMetrics.density
        fun zemin() = android.graphics.drawable.GradientDrawable().apply {
            setColor(opaklik shl 24)
            cornerRadius = yaricap
        }
        listeKutusu.background = zemin()
        bilgiKutusu.background = zemin()
        durumYazisi.background = zemin()
        satirlar.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * oran) }
        listeBaslik.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * oran)
        bilgiNumara.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f * oran)
        bilgiIsim.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * oran)
        durumYazisi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * oran)
        listeKutusu.layoutParams = listeKutusu.layoutParams.apply {
            width = (440 * oran * resources.displayMetrics.density).toInt()
        }
    }

    private fun kisaMesaj(yazi: String) {
        durumGoster(yazi)
        ana.removeCallbacks(mesajiGizle)
        ana.postDelayed(mesajiGizle, 5_000)
    }

    private val mesajiGizle = Runnable { durumGizle() }

    private fun listeyiSimdiGuncelle() {
        kisaMesaj("Kanal listesi kontrol ediliyor…")
        val mevcut = kanallar
        arkaPlan.execute {
            val s = KanalDeposu.uzaktanKontrol(this@MainActivity, mevcut)
            ana.post {
                val no = ayarlar.getInt("listeSurumu", 0)
                when {
                    !s.basarili -> kisaMesaj("Kanal listesine ulaşılamadı. İnternet bağlantısını kontrol edin.")
                    s.liste != null -> {
                        yeniListeyiUygula(s.liste)
                        kisaMesaj("Kanal listesi güncellendi (liste $no)")
                    }
                    else -> kisaMesaj("Kanal listesi zaten güncel (liste $no)")
                }
                if (listeAcik) listeyiCiz()
            }
        }
    }

    private fun surumuSimdiKontrolEt() {
        kisaMesaj("Yeni sürüm kontrol ediliyor…")
        arkaPlan.execute {
            val s = Guncelleyici.kontrolEt(this@MainActivity)
            ana.post {
                yeniSurum = s
                kisaMesaj(if (s != null) "Yeni sürüm var. Kanal listesinde \"Yeni sürümü kur\" satırını seçin." else "Uygulamanız güncel")
                if (listeAcik) listeyiCiz()
            }
        }
    }

    // ---------------------------------------------------------------- Kumanda

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (listeAcik) return listeTusu(keyCode)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> kanalDegistir(+1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> kanalDegistir(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> listeyiAc()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> rakamBasildi(keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> rakamBasildi(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            // Geri tuşu uygulamadan yanlışlıkla çıkarmaz; sadece kanal bilgisini gösterir. Çıkmak için ev tuşu kullanılır.
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO ->
                kanallar.getOrNull(aktif)?.let { bilgiyiGoster(it.no.toString(), it.isim) }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---------------------------------------------------------------- Arka plan işleri

    /** Kanal listesini GitHub'dan günceller. Sıra sadece sizin dosyanızdaki "no" değerlerine göre olur. */
    private val listeGuncelle: Runnable = object : Runnable { override fun run() {
        val mevcut = kanallar
        arkaPlan.execute {
            val yeni = KanalDeposu.uzaktanGuncelle(this@MainActivity, mevcut)
            if (yeni != null) ana.post { yeniListeyiUygula(yeni) }
        }
        ana.postDelayed(this, LISTE_GUNCELLEME_ARALIGI)
    } }

    private fun yeniListeyiUygula(yeni: List<Kanal>) {
        val eski = kanallar.getOrNull(aktif)
        kanallar = yeni
        aktif = yeni.indexOfFirst { it.no == eski?.no }.coerceAtLeast(0)
        val simdiki = yeni[aktif]
        if (eski != null && simdiki.linkler != eski.linkler) {
            // Bu kanalın linki değişmiş: yeni linkle hemen tekrar dene.
            hataSayisi = 0
            oynat(aktif, 0)
        }
        if (listeAcik) listeyiCiz()
    }

    /** Bütün kanalların yayınlarını arka planda kontrol eder; gelmeyenler listede gri görünür. */
    private val saglikKontrol: Runnable = object : Runnable { override fun run() {
        val liste = kanallar
        val izlenen = liste.getOrNull(aktif)?.no
        arkaPlan.execute {
            val calismayan = HashSet<Int>()
            for (k in liste) {
                if (Thread.currentThread().isInterrupted) return@execute
                if (k.no == izlenen) continue
                if (k.linkler.isEmpty() && k.youtube != null) continue
                if (k.linkler.take(2).none { Ag.linkCalisiyorMu(it) }) calismayan.add(k.no)
            }
            ana.post {
                val izlenenSorunlu = izlenen != null && calismayanlar.contains(izlenen)
                calismayanlar.clear()
                calismayanlar.addAll(calismayan)
                if (izlenenSorunlu && izlenen != null) calismayanlar.add(izlenen)
                if (listeAcik) listeyiCiz()
            }
        }
        ana.postDelayed(this, SAGLIK_KONTROL_ARALIGI)
    } }

    private val surumKontrol: Runnable = object : Runnable { override fun run() {
        arkaPlan.execute {
            val s = Guncelleyici.kontrolEt(this@MainActivity)
            ana.post {
                yeniSurum = s
                if (listeAcik) listeyiCiz()
            }
        }
        ana.postDelayed(this, SURUM_KONTROL_ARALIGI)
    } }

    private fun guncellemeyiBaslat() {
        val s = yeniSurum ?: return
        if (guncellemeIniyor) return
        guncellemeIniyor = true
        listeyiCiz()
        arkaPlan.execute {
            val dosya = Guncelleyici.indir(this, s)
            ana.post {
                guncellemeIniyor = false
                if (dosya == null) {
                    durumGoster("Yeni sürüm indirilemedi, daha sonra tekrar denenecek")
                    ana.postDelayed({ durumGizle() }, 5_000)
                } else {
                    listeyiKapat()
                    if (!Guncelleyici.kur(this, dosya)) {
                        durumGoster("İzin verdikten sonra listeden tekrar \"Yeni sürümü kur\" seçin")
                        ana.postDelayed({ durumGizle() }, 8_000)
                    }
                }
                if (listeAcik) listeyiCiz()
            }
        }
    }
}

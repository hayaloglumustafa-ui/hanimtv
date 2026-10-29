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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import android.net.Uri
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
        const val KANAL_GECIS_GECIKMESI = 180L
        const val LISTE_GUNCELLEME_ARALIGI = 6 * 60 * 60 * 1000L
        const val SAGLIK_KONTROL_ARALIGI = 30 * 60 * 1000L
        const val SURUM_KONTROL_ARALIGI = 12 * 60 * 60 * 1000L
        const val BEYAZ = 0xFFFFFFFF.toInt()
        const val GRI = 0x80FFFFFF.toInt()
        const val MENU_ANA = 0
        const val MENU_SON = 1
        const val MENU_FAVORI = 2
        const val MENU_KATEGORI = 3
        const val MENU_KATEGORI_KANAL = 4
        const val MENU_TEMA = 5
        const val MENU_GUNCELLEME = 6
        const val MENU_REHBER = 7
        const val MENU_AKIS = 8
        const val SITE_JS = """(function(){ var k=document.querySelector('.video-js'); var v=k?k.querySelector('video'):document.querySelector('video'); if(!v) return 'video yok'; if(!k) k=v.parentElement; document.documentElement.style.overflow='hidden';document.body.style.overflow='hidden'; k.style.cssText+=';position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;padding:0!important;z-index:2147483647!important;background:#000!important;margin:0!important'; var e=k; while(e&&e!==document.body&&e.parentElement){ [].forEach.call(e.parentElement.children,function(s){ if(s!==e&&!/SCRIPT|STYLE|LINK/.test(s.tagName)) s.style.setProperty('display','none','important'); }); e.style.setProperty('transform','none','important'); e.style.setProperty('filter','none','important'); e=e.parentElement; } [].slice.call(k.querySelectorAll('video')).forEach(function(x){x.style.objectFit='contain'}); window.__htv=(window.__htv||0)+1; var reklam=/vjs-ad-(playing|loading)/.test(k.className); if(v.paused && !reklam && window.__htv<15){ var d=k.querySelector('.vjs-big-play-button'); if(d) d.click(); var p=v.play(); if(p&&p.catch) p.catch(function(){}); } if(!reklam) v.muted=false; return reklam?'reklam':(v.paused?'durdu':'oynuyor'); })()"""
        const val SON_IZLENEN_SAYISI = 10
        const val MENU_SAYFA = 8

        class Tema(val ad: String, val zemin: Int, val vurgu: Int)

        /** İlk tema varsayılandır. */
        val TEMALAR = arrayOf(
            Tema("Bordo-altın", 0xFF6E1530.toInt(), 0xFFE8C77A.toInt()),
            Tema("Lacivert-altın", 0xFF14213D.toInt(), 0xFFD4AF37.toInt()),
            Tema("Siyah-beyaz", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            Tema("Zümrüt yeşili", 0xFF0F503C.toInt(), 0xFF9FE1CB.toInt()),
        )
    }

    private lateinit var oynaticiGorunum: PlayerView
    private lateinit var oynatici: ExoPlayer
    private lateinit var bilgiKutusu: View
    private lateinit var bilgiNumara: TextView
    private lateinit var bilgiIsim: TextView
    private lateinit var bilgiAkis: TextView
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
    private val menuAcik get() = menuKutusu.visibility == View.VISIBLE
    private lateinit var menuKutusu: View
    private lateinit var menuBaslik: TextView
    private val menuSatirlari = ArrayList<TextView>()

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
        bilgiAkis = findViewById(R.id.bilgiAkis)
        listeKutusu = findViewById(R.id.listeKutusu)
        listeBaslik = findViewById(R.id.listeBaslik)
        durumYazisi = findViewById(R.id.durumYazisi)
        menuKutusu = findViewById(R.id.menuKutusu)
        menuBaslik = findViewById(R.id.menuBaslik)
        ayarlar = getSharedPreferences("hanimtv", Context.MODE_PRIVATE)

        yaziTipleriniUygula()
        listeSatirlariniKur()
        menuSatirlariniKur()
        gorunumuUygula()

        kanallar = KanalDeposu.yukle(this)
        Epg.yukle(this)
        val sonKanal = ayarlar.getInt("sonKanal", 1)
        aktif = kanallar.indexOfFirst { it.no == sonKanal }.coerceAtLeast(0)
        sonOynayan = aktif

        oynaticiyiKur()
        agDinleyicisiniKur()
        Guncelleyici.temizle(this)

        ana.postDelayed(listeGuncelle, 5_000)
        ana.postDelayed(surumKontrol, 20_000)
        ana.postDelayed(saglikKontrol, 60_000)
        ana.postDelayed(akisGuncelle, 15_000)
        ana.postDelayed(hatirlatmaKontrol, 30_000)
        ana.postDelayed(reklamBekcisi, 20_000)
    }

    override fun onStart() {
        super.onStart()
        if (kanallar.isNotEmpty()) oynat(aktif, 0, bilgiGoster = true)
        ana.removeCallbacks(donmaBekcisi)
        ana.postDelayed(donmaBekcisi, 8_000)
    }

    override fun onStop() {
        super.onStop()
        siteOynaticiyiKapat()
        // Ana ekrana dönülünce ses arkada çalmaya devam etmesin.
        ana.removeCallbacks(yenidenDene)
        ana.removeCallbacks(takilmaKontrol)
        ana.removeCallbacks(donmaBekcisi)
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
        bilgiAkis.typeface = normalYazi
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
            // Yayın 0,7 saniyelik görüntü gelince başlar (eskiden 2,5 saniye bekliyordu); takılırsa 2 saniye toplayıp devam eder.
            .setBufferDurationsMs(15_000, 50_000, 700, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
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
                    Player.STATE_ENDED -> {
                        // Canlı yayın "bitti" derse (bazı kanallar reklam geçişinde böyle işaret verir) canlı noktadan devam et.
                        // 30 saniye içinde ikinci kez biterse link gerçekten bozuktur: yedeğe geç.
                        ana.removeCallbacks(takilmaKontrol)
                        val simdi = System.currentTimeMillis()
                        if (simdi - sonBitis < 30_000L) {
                            sonHata = "yayın kendiliğinden bitti"
                            sonrakiLinkiDene()
                        } else {
                            oynatici.seekToDefaultPosition()
                            oynatici.prepare()
                            oynatici.playWhenReady = true
                        }
                        sonBitis = simdi
                    }
                    else -> ana.removeCallbacks(takilmaKontrol)
                }
            }
        })
    }

    /** Kanalın istediği ek başlıklarla (Referer, Origin) yayın kaynağı hazırlar. */
    private fun kaynakFabrikasi(basliklar: Map<String, String>, anaLink: String = ""): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(basliklar["User-Agent"] ?: Ag.TARAYICI)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setDefaultRequestProperties(basliklar - "User-Agent")
        val temel = DefaultDataSource.Factory(this, http)
        return DefaultMediaSourceFactory(this).setDataSourceFactory(anahtarliFabrika(temel, anaLink))
    }

    /**
     * Anahtarlı (token) yayınlarda, ana linkteki anahtar parametrelerini aynı yayın sunucusuna giden
     * bütün isteklere (alt listeler ve görüntü parçaları) ekler. Sitelerin kendi oynatıcıları da böyle yapar.
     */
    private fun anahtarliFabrika(temel: DataSource.Factory, anaLink: String): DataSource.Factory {
        val ana = runCatching { Uri.parse(anaLink) }.getOrNull() ?: return temel
        if (ana.getQueryParameter("token") == null) return temel
        val parametreler = ana.queryParameterNames.associateWith { ana.getQueryParameter(it) ?: "" }
        val anaAlan = ana.host?.split('.')?.takeLast(2)?.joinToString(".") ?: return temel
        return ResolvingDataSource.Factory(temel) { istek ->
            val u = istek.uri
            val alan = u.host?.split('.')?.takeLast(2)?.joinToString(".")
            if (alan == anaAlan && u.getQueryParameter("token") == null) {
                val yeni = u.buildUpon()
                parametreler.forEach { (ad, deger) -> if (u.getQueryParameter(ad) == null) yeni.appendQueryParameter(ad, deger) }
                istek.withUri(yeni.build())
            } else istek
        }
    }

    /** Ekranda gösterilecek kısa hata sebebi; bana fotoğrafı gönderildiğinde sorunu anlamayı sağlar. */
    private var sonHata = ""
    private var sonBitis = 0L

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
            kucuk.contains(".m3u8") || kucuk.contains("/hls/") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
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

        if (k.siteOynatici != null && ayarlar.getBoolean("siteOynatici", false)) {
            siteOynaticiylaAc(k)
            return
        }
        siteOynaticiyiKapat()

        if (k.linkler.isEmpty() && k.sayfa == null && k.youtube != null) {
            youtubeAc(k)
            return
        }
        val oncekiKanal = sonOynayan
        if (link == 0 && indeks != oncekiKanal) geriKanal = oncekiKanal
        sonOynayan = indeks
        if (link == 0 && ayarlar.getInt("sonKanal", -1) != k.no) {
            // commit(): elektrik kesilse bile son kanal anında kaydedilmiş olur.
            ayarlar.edit().putInt("sonKanal", k.no).commit()
            sonIzlenenEkle(k.isim)
        }

        if (k.linkler.isEmpty() && k.sayfa == null) {
            oynatici.stop()
            oynatici.clearMediaItems()
            calismayanlar.add(k.no)
            durumGoster("Bu kanalın internet yayını henüz eklenmedi")
            return
        }
        if (link > 0) {
            baslat(k, link)
            return
        }
        aktifLinkler = k.linkler
        val sayfa = k.sayfa
        if (sayfa == null) {
            baslat(k, 0)
            return
        }
        // Kanalın kendi sitesinden o anki taze yayın linkini (anahtarıyla) al, sonra sabit linkleri yedek olarak kullan.
        val istek = ++sayfaIstegi
        // Son 5 dakika içinde alınmış anahtar varsa tekrar siteye gitmeden hemen kullan.
        sayfaOnbellegi[k.no]?.let { (link, zaman) ->
            if (System.currentTimeMillis() - zaman < 5 * 60_000L && !link.startsWith("youtube:") && !link.startsWith("token:")) {
                aktifLinkler = listOf(link) + k.linkler.filter { it != link }
                baslat(k, 0)
                return
            }
        }
        arkaPlan.execute {
            val taze = SayfadanLink.bul(sayfa, k.basliklar)
            if (taze != null) sayfaOnbellegi[k.no] = taze to System.currentTimeMillis()
            ana.post {
                if (istek != sayfaIstegi || kanallar.getOrNull(aktif)?.no != k.no) return@post
                if (taze != null && taze.startsWith("youtube:")) {
                    // Kanalın sitesindeki canlı yayın YouTube'da: YouTube uygulamasında aç, dönünce önceki kanal devam etsin.
                    oynatici.stop()
                    if (YouTube.ac(this, taze.removePrefix("youtube:"))) {
                        durumGizle()
                        aktif = oncekiKanal
                        sonOynayan = oncekiKanal
                    } else durumGoster("YouTube uygulaması açılamadı")
                    return@post
                }
                aktifLinkler = if (taze != null && taze.startsWith("token:")) {
                    // Sayfadan sadece anahtar geldi: sabit linklere ekle.
                    val anahtar = taze.removePrefix("token:")
                    k.linkler.map { l -> if (l.contains("{token}")) l.replace("{token}", anahtar) else l + (if (l.contains('?')) "&" else "?") + "token=" + anahtar }
                } else listOfNotNull(taze) + k.linkler.filter { it != taze }
                if (aktifLinkler.isEmpty()) {
                    sonHata = "sitede yayın linki bulunamadı"
                    sonrakiLinkiDene()
                } else baslat(k, 0)
            }
        }
    }

    /** O anda denenen linkler: varsa kanalın sitesinden alınan taze link, ardından sabit linkler. */
    private var aktifLinkler: List<String> = emptyList()
    private var sayfaIstegi = 0
    private val sayfaOnbellegi = ConcurrentHashMap<Int, Pair<String, Long>>()

    private fun baslat(k: Kanal, link: Int) {
        linkSirasi = link
        val adres = aktifLinkler.getOrNull(link) ?: return
        oynatici.setMediaSource(kaynakFabrikasi(k.basliklar, adres).createMediaSource(yayinOgesi(adres)))
        oynatici.prepare()
        oynatici.playWhenReady = true
    }

    /** En son normal şekilde oynatılan kanal. YouTube'dan geri dönünce bu kanal açılır. */
    private var sonOynayan = 0
    /** Geri tuşuyla dönülecek, bir önceki izlenen kanal. */
    private var geriKanal = -1
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
        if (linkSirasi + 1 < aktifLinkler.size) {
            ana.removeCallbacks(takilmaKontrol)
            baslat(k, linkSirasi + 1)
            return
        }
        calismayanlar.add(k.no)
        sayfaOnbellegi.remove(k.no)
        if (listeAcik) listeyiCiz()
        hataSayisi++
        durumGoster("Yayın şu an gelmiyor, tekrar deneniyor… (${aktifLinkler.size} link · $sonHata)")
        val bekleme = (5_000L * hataSayisi).coerceAtMost(30_000L)
        ana.removeCallbacks(yenidenDene)
        ana.postDelayed(yenidenDene, bekleme)
    }

    private val yenidenDene = Runnable { if (kanallar.isNotEmpty()) oynat(aktif, 0) }

    /**
     * Donma bekçisi: yayın "oynuyor" görünüp görüntü ilerlemiyorsa (resim donduysa)
     * 8 saniye içinde fark eder ve bir sonraki linke geçer. Hata vermeden donan yayınlar için.
     */
    private var sonKonum = -1L
    private var donmaSayaci = 0
    private val donmaBekcisi: Runnable = object : Runnable { override fun run() {
        if (oynatici.playbackState == Player.STATE_READY && oynatici.playWhenReady) {
            val konum = oynatici.currentPosition
            if (konum == sonKonum) {
                if (++donmaSayaci >= 2) {
                    donmaSayaci = 0
                    sonHata = "görüntü dondu"
                    sonrakiLinkiDene()
                }
            } else donmaSayaci = 0
            sonKonum = konum
        } else {
            donmaSayaci = 0
            sonKonum = -1L
        }
        ana.postDelayed(this, 4_000)
    } }

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
        val akis = if (isim.isEmpty()) null else Epg.yazi(isim)
        bilgiAkis.text = akis ?: ""
        bilgiAkis.visibility = if (akis == null) View.GONE else View.VISIBLE
        bilgiKutusu.visibility = View.VISIBLE
        ana.removeCallbacks(bilgiyiGizle)
        ana.postDelayed(bilgiyiGizle, if (akis == null) BILGI_SURESI else BILGI_SURESI + 2_000L)
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

    private var sonSurumKontrolu = 0L

    private fun listeyiAc() {
        if (kanallar.isEmpty()) return
        // Liste her açıldığında (en fazla 10 dakikada bir) yeni sürüm olup olmadığına da bakılır.
        val simdi = System.currentTimeMillis()
        if (simdi - sonSurumKontrolu > 10 * 60_000L) {
            sonSurumKontrolu = simdi
            arkaPlan.execute {
                val s = Guncelleyici.kontrolEt(this)
                ana.post {
                    if (s != null && yeniSurum == null) {
                        yeniSurum = s
                        if (listeAcik) listeyiCiz()
                    }
                }
            }
        }
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
        testModu = false
        ana.removeCallbacks(listeyiKendiligindenKapat)
    }

    // Test sonuçları okunurken (fotoğraf çekilirken) liste kendiliğinden kapanmaz.
    private val listeyiKendiligindenKapat = Runnable { if (!testModu) listeyiKapat() }

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
                    val yt = if (k.linkler.isEmpty() && k.sayfa == null && k.youtube != null) "  · YouTube" else ""
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
            t.background = if (konum == listeSecili) seciliZemin() else null
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
    private val goruntuAdlari = arrayOf("Orijinal", "Ekranı doldur", "Uzat")

    private fun ayarSatirlari(): List<String> {
        val seffaflik = ayarlar.getInt("seffaflik", 1)
        val boyut = ayarlar.getInt("yaziBoyutu", 1)
        val acilis = ayarlar.getBoolean("acilistaBasla", true)
        val gizle = ayarlar.getBoolean("griGizle", false)
        val zaman = ayarlar.getLong("listeZamani", 0L).let {
            if (it == 0L) "—" else java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale("tr")).format(java.util.Date(it))
        }
        return listOf(
            "Bütün kanalları test et",
            "Şeffaflık: " + seffaflikAdlari[seffaflik],
            "Yazı boyutu: " + boyutAdlari[boyut],
            "Açılışta HanımTV başlasın: " + if (acilis) "Açık" else "Kapalı",
            "Açılmayan kanalları gizle: " + if (gizle) "Açık" else "Kapalı",
            "Uyku zamanlayıcısı: " + uykuYazisi(),
            "Görüntü: " + goruntuAdlari[ayarlar.getInt("goruntu", 0).coerceIn(0, 2)],
            "Reklamda önceki kanala geç: " + if (ayarlar.getBoolean("reklamKacis", true)) "Açık" else "Kapalı",
            "NOW'u sitesinin oynatıcısıyla aç (deneme): " + if (ayarlar.getBoolean("siteOynatici", false)) "Açık" else "Kapalı",
            "Kumanda rehberi",
            "Sürüm 1.0.${Guncelleyici.mevcutSurum(this)} · Liste ${ayarlar.getInt("listeSurumu", 0)} · $zaman",
        )
    }

    private fun ayarlariCiz() {
        if (testModu) {
            testiCiz()
            return
        }
        val s = ayarSatirlari()
        ayarSecili = ayarSecili.coerceIn(0, s.size - 1)
        listeBaslik.text = "Ayarlar · Geri tuşu: kanal listesi"
        val bas = (ayarSecili / SAYFA_BOYU) * SAYFA_BOYU
        for (i in 0 until SAYFA_BOYU) {
            val t = satirlar[i]
            val j = bas + i
            if (j >= s.size) {
                t.visibility = View.INVISIBLE
                t.background = null
                continue
            }
            t.text = s[j]
            t.setTextColor(if (j == s.size - 1) GRI else BEYAZ)
            t.typeface = normalYazi
            t.visibility = View.VISIBLE
            t.background = if (j == ayarSecili) seciliZemin() else null
        }
    }

    private fun ayarTusu(tus: Int): Boolean {
        if (testModu) return testTusu(tus)
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
            0 -> kanalTestiniBaslat()
            1 -> e.putInt("seffaflik", (ayarlar.getInt("seffaflik", 1) + 1) % 3)
            2 -> e.putInt("yaziBoyutu", (ayarlar.getInt("yaziBoyutu", 1) + 1) % 3)
            3 -> e.putBoolean("acilistaBasla", !ayarlar.getBoolean("acilistaBasla", true))
            4 -> e.putBoolean("griGizle", !ayarlar.getBoolean("griGizle", false))
            5 -> uykuDegistir()
            6 -> e.putInt("goruntu", (ayarlar.getInt("goruntu", 0) + 1) % 3)
            7 -> {
                e.putBoolean("reklamKacis", !ayarlar.getBoolean("reklamKacis", true))
                if (reklamda) reklamKacisiniBitir()
            }
            8 -> {
                e.putBoolean("siteOynatici", !ayarlar.getBoolean("siteOynatici", false))
                e.commit()
                kanallar.getOrNull(aktif)?.let { if (it.siteOynatici != null) kanalaGit(aktif) }
                return
            }
            9 -> {
                listeyiKapat()
                menuyuAc(MENU_REHBER)
                return
            }
        }
        e.commit()
        if (sira == 1 || sira == 2 || sira == 6) gorunumuUygula()
    }

    /** Seçilen şeffaflığı ve yazı boyutunu bütün kutulara uygular. */
    private fun gorunumuUygula() {
        val opaklik = intArrayOf(0xBF, 0x8C, 0x59)[ayarlar.getInt("seffaflik", 1).coerceIn(0, 2)]
        val oran = floatArrayOf(0.85f, 1f, 1.2f)[ayarlar.getInt("yaziBoyutu", 1).coerceIn(0, 2)]
        val yaricap = 12 * resources.displayMetrics.density
        val tema = TEMALAR[ayarlar.getInt("tema", 0).coerceIn(0, TEMALAR.size - 1)]
        fun zemin() = android.graphics.drawable.GradientDrawable().apply {
            setColor((opaklik shl 24) or (tema.zemin and 0xFFFFFF))
            cornerRadius = yaricap
        }
        listeKutusu.background = zemin()
        menuKutusu.background = android.graphics.drawable.GradientDrawable().apply {
            setColor((opaklik shl 24) or (tema.zemin and 0xFFFFFF))
            val r = 18 * resources.displayMetrics.density
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        menuBaslik.setTextColor(tema.vurgu)
        listeBaslik.setTextColor(tema.vurgu)
        menuSatirlari.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * oran) }
        menuBaslik.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * oran)
        menuKutusu.layoutParams = menuKutusu.layoutParams.apply {
            width = (400 * oran * resources.displayMetrics.density).toInt()
        }
        bilgiKutusu.background = zemin()
        durumYazisi.background = zemin()
        satirlar.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * oran) }
        listeBaslik.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * oran)
        bilgiNumara.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f * oran)
        bilgiIsim.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * oran)
        bilgiAkis.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * oran)
        oynaticiGorunum.resizeMode = intArrayOf(0, 4, 3)[ayarlar.getInt("goruntu", 0).coerceIn(0, 2)]
        durumYazisi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * oran)
        listeKutusu.layoutParams = listeKutusu.layoutParams.apply {
            width = (440 * oran * resources.displayMetrics.density).toInt()
        }
    }

    // ---------------------------------------------------------------- Kanal testi

    private var testModu = false
    private var testCalisiyor = false
    private var testIlerleme = 0
    private var testSayfa = 0
    private var testSonuclari: List<String> = emptyList()

    /** Bütün kanalları stick'in kendisinden (Türkiye'den) dener; açılmayanları sebebiyle listeler. */
    private fun kanalTestiniBaslat() {
        testModu = true
        testSayfa = 0
        listeZamanlayicisi()
        if (testCalisiyor) {
            listeyiCiz()
            return
        }
        testCalisiyor = true
        testIlerleme = 0
        testSonuclari = emptyList()
        val liste = kanallar
        listeyiCiz()
        arkaPlan.execute {
            val sonuc = java.util.Collections.synchronizedList(ArrayList<Pair<Int, String>>())
            val havuz = Executors.newFixedThreadPool(6)
            for (k in liste) {
                havuz.execute {
                    val sorun: String? = when {
                        k.linkler.isEmpty() && k.sayfa == null && k.youtube != null -> null
                        else -> {
                            val adaylar = ArrayList<String>()
                            var siteYoutube = false
                            k.sayfa?.let { s -> SayfadanLink.bul(s, k.basliklar)?.let {
                                when {
                                    it.startsWith("youtube:") -> siteYoutube = true
                                    it.startsWith("token:") -> {
                                        val a = it.removePrefix("token:")
                                        adaylar.addAll(k.linkler.map { l -> l + (if (l.contains('?')) "&" else "?") + "token=" + a })
                                    }
                                    else -> adaylar.add(it)
                                }
                            } }
                            adaylar.addAll(k.linkler.take(4))
                            if (siteYoutube) null
                            else if (adaylar.isEmpty()) "link yok"
                            else {
                                var ilkSorun: String? = "link yok"
                                for (a in adaylar) {
                                    val s = Ag.linkSorunu(a, k.basliklar)
                                    if (s == null) { ilkSorun = null; break }
                                    if (ilkSorun == "link yok") ilkSorun = s
                                }
                                ilkSorun
                            }
                        }
                    }
                    if (sorun != null) sonuc.add(k.no to "${k.no}  ${k.isim} · $sorun")
                    ana.post {
                        testIlerleme++
                        if (sorun != null) calismayanlar.add(k.no) else calismayanlar.remove(k.no)
                        if (testModu && listeAcik && testIlerleme % 5 == 0) listeyiCiz()
                    }
                }
            }
            havuz.shutdown()
            havuz.awaitTermination(15, java.util.concurrent.TimeUnit.MINUTES)
            val sirali = sonuc.sortedBy { it.first }.map { it.second }
            ana.post {
                testCalisiyor = false
                testSonuclari = sirali
                if (listeAcik) listeyiCiz()
            }
        }
    }

    private fun testiCiz() {
        listeZamanlayicisi()
        val toplam = kanallar.size
        if (testCalisiyor) {
            listeBaslik.text = "Kanallar test ediliyor… $testIlerleme / $toplam"
            for (i in 0 until SAYFA_BOYU) {
                val t = satirlar[i]
                t.text = if (i == 0) "Bu işlem 1-2 dakika sürer. Bekleyin." else ""
                t.background = null
                t.setTextColor(BEYAZ)
                t.visibility = if (i == 0) View.VISIBLE else View.INVISIBLE
            }
            return
        }
        val sonuclar = testSonuclari
        val sayfaSayisi = maxOf(1, (sonuclar.size + SAYFA_BOYU - 1) / SAYFA_BOYU)
        testSayfa = testSayfa.coerceIn(0, sayfaSayisi - 1)
        listeBaslik.text = if (sonuclar.isEmpty()) "Test bitti · bütün kanallar açılıyor"
        else "Açılmayan ${sonuclar.size} kanal · sayfa ${testSayfa + 1}/$sayfaSayisi"
        for (i in 0 until SAYFA_BOYU) {
            val t = satirlar[i]
            val s = sonuclar.getOrNull(testSayfa * SAYFA_BOYU + i)
            t.background = null
            t.setTextColor(BEYAZ)
            t.typeface = normalYazi
            if (s == null) t.visibility = View.INVISIBLE else {
                t.text = s
                t.visibility = View.VISIBLE
            }
        }
    }

    private fun testTusu(tus: Int): Boolean {
        val sayfaSayisi = maxOf(1, (testSonuclari.size + SAYFA_BOYU - 1) / SAYFA_BOYU)
        when (tus) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> testSayfa = (testSayfa + 1) % sayfaSayisi
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> testSayfa = (testSayfa - 1 + sayfaSayisi) % sayfaSayisi
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> testModu = false
            else -> return true
        }
        listeyiCiz()
        return true
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

    // ---------------------------------------------------------------- Soldan açılan menü

    /** Menüde bir satır: görünen yazı ve seçilince yapılacak iş (null ise sadece bilgi satırı). */
    private class MenuSatiri(val yazi: String, val renk: Int = BEYAZ, val is_: (() -> Unit)?)

    private var menuSeviye = MENU_ANA
    private var menuSecili = 0
    private var menuKategori = ""
    private val menuGecmisi = ArrayList<Pair<Int, Int>>()

    private fun menuSatirlariniKur() {
        val kap = findViewById<LinearLayout>(R.id.menuSatirlari)
        val yogunluk = resources.displayMetrics.density
        repeat(MENU_SAYFA) {
            val t = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(BEYAZ)
                typeface = normalYazi
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                val y = (8 * yogunluk).toInt()
                val x = (14 * yogunluk).toInt()
                setPadding(x, y, x, y)
            }
            kap.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            menuSatirlari.add(t)
        }
        menuBaslik.typeface = kalinYazi
    }

    private fun seciliZemin(): android.graphics.drawable.Drawable {
        val tema = TEMALAR[ayarlar.getInt("tema", 0).coerceIn(0, TEMALAR.size - 1)]
        val d = resources.displayMetrics.density
        return android.graphics.drawable.GradientDrawable().apply {
            setColor((0x55 shl 24) or (tema.vurgu and 0xFFFFFF))
            setStroke((2 * d).toInt(), tema.vurgu)
            cornerRadius = 8 * d
        }
    }

    private fun isimListesi(anahtar: String): List<String> =
        ayarlar.getString(anahtar, "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun isimListesiKaydet(anahtar: String, l: List<String>) {
        ayarlar.edit().putString(anahtar, l.joinToString("\n")).apply()
    }

    private fun sonIzlenenEkle(isim: String) {
        val l = isimListesi("sonIzlenenler").toMutableList()
        l.remove(isim)
        l.add(0, isim)
        isimListesiKaydet("sonIzlenenler", l.take(SON_IZLENEN_SAYISI))
    }

    private fun indeksBul(isim: String) = kanallar.indexOfFirst { it.isim == isim }

    private fun kanalSatiri(indeks: Int): MenuSatiri {
        val k = kanallar[indeks]
        return MenuSatiri(String.format("%-4d %s", k.no, k.isim), if (calismayanlar.contains(k.no)) GRI else BEYAZ) {
            menuyuKapat()
            if (indeks != aktif) kanalaGit(indeks) else bilgiyiGoster(k.no.toString(), k.isim)
        }
    }

    private fun menuIcerigi(): Pair<String, List<MenuSatiri>> = when (menuSeviye) {
        MENU_SON -> "Son izlenenler" to isimListesi("sonIzlenenler").map { indeksBul(it) }.filter { it >= 0 }
            .map { kanalSatiri(it) }.ifEmpty { listOf(MenuSatiri("Henüz kanal izlenmedi", GRI, null)) }
        MENU_FAVORI -> {
            val favoriler = isimListesi("favoriler")
            val k = kanallar.getOrNull(aktif)
            val l = ArrayList<MenuSatiri>()
            if (k != null) {
                val var_ = favoriler.contains(k.isim)
                l.add(MenuSatiri(if (var_) "✕  ${k.isim} çıkar" else "★  ${k.isim} ekle") {
                    isimListesiKaydet("favoriler", if (var_) favoriler - k.isim else favoriler + k.isim)
                    kisaMesaj(if (var_) "${k.isim} favorilerden çıkarıldı" else "${k.isim} favorilere eklendi")
                    menuyuCiz()
                })
            }
            favoriler.map { indeksBul(it) }.filter { it >= 0 }.sortedBy { kanallar[it].no }.forEach { l.add(kanalSatiri(it)) }
            "Favoriler" to l
        }
        MENU_KATEGORI -> "Kategoriler" to kanallar.map { it.kategori }.distinct().map { ad ->
            val adet = kanallar.count { it.kategori == ad }
            MenuSatiri("$ad  ($adet)") { menuyeGir(MENU_KATEGORI_KANAL); menuKategori = ad; menuyuCiz() }
        }
        MENU_KATEGORI_KANAL -> menuKategori to kanallar.indices.filter { kanallar[it].kategori == menuKategori }.map { kanalSatiri(it) }
        MENU_TEMA -> {
            val secili = ayarlar.getInt("tema", 0)
            "Tema" to TEMALAR.mapIndexed { i, t ->
                MenuSatiri((if (i == secili) "●  " else "○  ") + t.ad) {
                    ayarlar.edit().putInt("tema", i).commit()
                    gorunumuUygula()
                    menuyuCiz()
                }
            }
        }
        MENU_GUNCELLEME -> {
            val zaman = ayarlar.getLong("listeZamani", 0L).let {
                if (it == 0L) "—" else java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale("tr")).format(java.util.Date(it))
            }
            val l = arrayListOf(
                MenuSatiri("Kanal listesini güncelle") { listeyiSimdiGuncelle() },
                MenuSatiri("Yeni sürümü kontrol et") { surumuSimdiKontrolEt() },
            )
            if (yeniSurum != null) l.add(MenuSatiri(if (guncellemeIniyor) "Yeni sürüm indiriliyor…" else "Yeni sürümü kur") { guncellemeyiBaslat() })
            l.add(MenuSatiri("Sürüm 1.0.${Guncelleyici.mevcutSurum(this)}", GRI, null))
            l.add(MenuSatiri("Liste ${ayarlar.getInt("listeSurumu", 0)} · $zaman", GRI, null))
            "Güncellemeler" to l
        }
        MENU_AKIS -> {
            val k = kanallar.getOrNull(aktif)
            val l = ArrayList<MenuSatiri>()
            val saat = java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr"))
            val simdi = System.currentTimeMillis()
            val kurulu = hatirlatmalar()
            if (k != null) for (p in Epg.programlar(k.isim)) {
                val anahtar = "${k.isim}|${p.bas}|${p.ad}"
                val oyunda = p.bas <= simdi
                val isaret = when { oyunda -> "▶ "; anahtar in kurulu -> "⏰ "; else -> "    " }
                l.add(MenuSatiri(isaret + saat.format(java.util.Date(p.bas)) + "  " + p.ad) {
                    if (oyunda) menuyuKapat() else {
                        val yeni = if (anahtar in kurulu) kurulu - anahtar else kurulu + anahtar
                        isimListesiKaydet("hatirlatmalar", yeni)
                        kisaMesaj(if (anahtar in kurulu) "Hatırlatma kaldırıldı" else "${p.ad} başlamadan 5 dakika önce hatırlatılacak")
                        menuyuCiz()
                    }
                })
            }
            if (l.isEmpty()) l.add(MenuSatiri("Bu kanalın yayın akışı yok", GRI, null))
            "${k?.isim ?: ""} · yayın akışı" to l
        }
        MENU_REHBER -> "Kumanda rehberi" to listOf(
            "Yukarı / Aşağı: kanal değiştir",
            "Rakamlar: kanal numarası",
            "Orta tuş: kanal listesi",
            "Sol tuş: bu menü",
            "Sağ tuş: kanal bilgisi",
            "Hatırlatma çıkınca OK: o kanala geç",
            "Reklam arasında önceki kanala geçer",
            "Listede sağ / sol: sayfa",
            "Geri: önceki kanala dön",
            "Menüde geri: bir önceki ekran",
        ).map { MenuSatiri(it, BEYAZ, null) }
        else -> "HanımTV" to listOf(
            MenuSatiri("Son izlenenler") { menuyeGir(MENU_SON) },
            MenuSatiri("Favoriler") { menuyeGir(MENU_FAVORI) },
            MenuSatiri("Kategoriler") { menuyeGir(MENU_KATEGORI) },
            MenuSatiri("Yayın akışı") { menuyeGir(MENU_AKIS) },
            MenuSatiri("Tüm kanallar") { menuyuKapat(); listeyiAc() },
            MenuSatiri("Tema") { menuyeGir(MENU_TEMA) },
            MenuSatiri(if (yeniSurum != null) "Güncellemeler  ●" else "Güncellemeler") { menuyeGir(MENU_GUNCELLEME) },
            MenuSatiri("Ayarlar") {
                menuyuKapat()
                listeyiAc()
                ayarModu = true
                ayarSecili = 0
                listeyiCiz()
            },
        )
    }

    private fun menuyuAc(seviye: Int) {
        if (kanallar.isEmpty()) return
        menuGecmisi.clear()
        menuSeviye = seviye
        menuSecili = 0
        bilgiKutusu.visibility = View.GONE
        menuKutusu.visibility = View.VISIBLE
        menuyuCiz()
    }

    private fun menuyeGir(seviye: Int) {
        menuGecmisi.add(menuSeviye to menuSecili)
        menuSeviye = seviye
        menuSecili = 0
        menuyuCiz()
    }

    private fun menuyuKapat() {
        menuKutusu.visibility = View.GONE
        ana.removeCallbacks(menuyuKendiligindenKapat)
    }

    private val menuyuKendiligindenKapat = Runnable { menuyuKapat() }

    private fun menuyuCiz() {
        ana.removeCallbacks(menuyuKendiligindenKapat)
        ana.postDelayed(menuyuKendiligindenKapat, LISTE_KAPANMA_SURESI)
        val (baslik, l) = menuIcerigi()
        menuSecili = menuSecili.coerceIn(0, maxOf(0, l.size - 1))
        val sayfa = menuSecili / MENU_SAYFA
        val sayfaSayisi = maxOf(1, (l.size + MENU_SAYFA - 1) / MENU_SAYFA)
        menuBaslik.text = if (sayfaSayisi > 1) "$baslik · ${sayfa + 1}/$sayfaSayisi" else baslik
        val secilebilir = l.any { it.is_ != null }
        for (i in 0 until MENU_SAYFA) {
            val t = menuSatirlari[i]
            val satir = l.getOrNull(sayfa * MENU_SAYFA + i)
            if (satir == null) {
                t.visibility = View.GONE
                continue
            }
            t.text = satir.yazi
            t.setTextColor(satir.renk)
            t.visibility = View.VISIBLE
            t.background = if (secilebilir && sayfa * MENU_SAYFA + i == menuSecili) seciliZemin() else null
        }
    }

    private fun menuTusu(tus: Int): Boolean {
        val l = menuIcerigi().second
        val adet = maxOf(1, l.size)
        when (tus) {
            KeyEvent.KEYCODE_DPAD_UP -> menuSecili = (menuSecili - 1 + adet) % adet
            KeyEvent.KEYCODE_DPAD_DOWN -> menuSecili = (menuSecili + 1) % adet
            KeyEvent.KEYCODE_CHANNEL_UP -> menuSecili = (menuSecili - MENU_SAYFA).coerceAtLeast(0)
            KeyEvent.KEYCODE_CHANNEL_DOWN -> menuSecili = (menuSecili + MENU_SAYFA).coerceAtMost(adet - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                l.getOrNull(menuSecili)?.is_?.invoke()
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> {
                val onceki = menuGecmisi.removeLastOrNull()
                if (onceki == null) {
                    menuyuKapat()
                    return true
                }
                menuSeviye = onceki.first
                menuSecili = onceki.second
            }
            else -> return true
        }
        menuyuCiz()
        return true
    }

    // ---------------------------------------------------------------- Program hatırlatıcı

    /** Kurulu hatırlatmalar: "kanal adı|başlangıç (ms)|program adı". */
    private fun hatirlatmalar() = isimListesi("hatirlatmalar")

    /** Ekrandaki hatırlatmanın kanalı; OK tuşu bu kanala geçirir. */
    private var hatirlatmaKanali = -1
    private val hatirlatmaGosterildi = HashSet<String>()

    private val hatirlatmaKontrol: Runnable = object : Runnable { override fun run() {
        val simdi = System.currentTimeMillis()
        val l = hatirlatmalar()
        val kalan = l.filter { (it.split('|').getOrNull(1)?.toLongOrNull() ?: 0L) > simdi - 10 * 60_000L }
        if (kalan.size != l.size) isimListesiKaydet("hatirlatmalar", kalan)
        for (h in kalan) {
            val p = h.split('|', limit = 3)
            if (p.size < 3) continue
            val bas = p[1].toLongOrNull() ?: continue
            val indeks = indeksBul(p[0])
            if (indeks < 0) continue
            val asama = when {
                simdi >= bas && simdi < bas + 2 * 60_000L -> "basladi"
                simdi >= bas - 5 * 60_000L && simdi < bas -> "yakinda"
                else -> null
            } ?: continue
            if (!hatirlatmaGosterildi.add("$h|$asama")) continue
            if (indeks == aktif && asama == "basladi") continue
            val dk = ((bas - simdi) / 60_000L + 1).coerceAtLeast(1)
            hatirlatmaKanali = indeks
            durumGoster(if (asama == "yakinda") "⏰ ${p[2]} $dk dk sonra · ${p[0]}\nGeçmek için OK" else "⏰ ${p[2]} başladı · ${p[0]}\nGeçmek için OK")
            ana.removeCallbacks(hatirlatmayiKapat)
            ana.postDelayed(hatirlatmayiKapat, 60_000L)
        }
        ana.postDelayed(this, 20_000L)
    } }

    private val hatirlatmayiKapat = Runnable {
        hatirlatmaKanali = -1
        durumGizle()
    }

    // ---------------------------------------------------------------- Reklam arası kaçışı

    /*
     * Bazı kanallar (NOW, Kanal D, Show TV...) internet yayınının içine reklam arası parçaları koyar
     * (adında "_era" ya da "/crea/" geçen parçalar). İzlenen kanalın yayın listesine 5 saniyede bir bakılır:
     * son 3 parça reklamsa önceki kanala geçilir; o sırada asıl kanala bakılmaya devam edilir,
     * program başlayınca (son 2 parça normal) asıl kanala geri dönülür.
     */
    private val C_UNSET = Long.MIN_VALUE + 1
    private val REKLAM_PARCASI = Regex("""_era[._]|/crea/|/slate/|reklam""", RegexOption.IGNORE_CASE)
    private var reklamda = false
    private var asilKanal = -1
    private var kacisKanali = -1
    private var izlenenNo = -1
    private var medyaListesi: String? = null
    private var bekciMesgul = false
    private var reklamBaslangici = 0L

    private fun parcalar(liste: String): List<String> = liste.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    /** Listenin sonundan geriye: (aynı türden ardışık parçaların toplam süresi sn, son parça reklam mı). */
    private fun sondakiSure(liste: String): Pair<Double, Boolean> {
        val sureler = ArrayList<Pair<Double, Boolean>>()
        var sure = 0.0
        for (satir in liste.lines()) {
            val t = satir.trim()
            if (t.startsWith("#EXTINF:")) sure = t.removePrefix("#EXTINF:").substringBefore(',').toDoubleOrNull() ?: 0.0
            else if (t.isNotEmpty() && !t.startsWith("#")) sureler.add(sure to REKLAM_PARCASI.containsMatchIn(t))
        }
        val son = sureler.lastOrNull() ?: return 0.0 to false
        var toplam = 0.0
        for (i in sureler.indices.reversed()) {
            if (sureler[i].second != son.second) break
            toplam += sureler[i].first
        }
        // Listenin tamamı aynı türdeyse (hiç reklam yoksa) çok uzun say.
        if (sureler.all { it.second == son.second }) toplam = 9_999.0
        return toplam to son.second
    }

    private fun reklamKacisiniBitir() {
        reklamda = false
        asilKanal = -1
        kacisKanali = -1
    }

    private val reklamBekcisi: Runnable = object : Runnable { override fun run() {
        ana.postDelayed(this, 5_000)
        if (bekciMesgul || kanallar.isEmpty() || !ayarlar.getBoolean("reklamKacis", true)) return
        // Kullanıcı reklam sırasında kendisi başka kanala geçtiyse kaçış iptal.
        if (reklamda && aktif != kacisKanali && aktif != asilKanal) reklamKacisiniBitir()
        // Güvenlik: reklam arası 12 dakikadan uzun sürmez; işaret okunamazsa yine de geri dönülür.
        if (reklamda && System.currentTimeMillis() - reklamBaslangici > 12 * 60_000L) { reklamdanDon(); return }
        val hedef = if (reklamda) asilKanal else aktif
        val k = kanallar.getOrNull(hedef) ?: return
        // Kanal kendi sitesinin oynatıcısıyla açıksa reklamları site gösterir; kaçmaya gerek yok.
        if (!reklamda && k.siteOynatici != null && ayarlar.getBoolean("siteOynatici", false)) return
        if (k.no != izlenenNo) {
            izlenenNo = k.no
            medyaListesi = null
        }
        val ana_ = if (hedef == aktif) aktifLinkler.getOrNull(linkSirasi) else null
        val bilinen = medyaListesi
        if (bilinen == null && ana_ == null) return
        bekciMesgul = true
        arkaPlan.execute {
            var liste: String? = null
            var adres = bilinen
            try {
                if (adres == null && ana_ != null) {
                    val m = Ag.metinIndir(ana_, 300_000, 8_000, k.basliklar)
                    if (m != null && m.contains("#EXT-X-STREAM-INF")) {
                        parcalar(m).firstOrNull()?.let { adres = java.net.URL(java.net.URL(ana_), it).toString() }
                    } else if (m != null) {
                        adres = ana_
                        liste = m
                    }
                }
                if (liste == null) adres?.let { liste = Ag.metinIndir(it, 300_000, 8_000, k.basliklar) }
            } catch (e: Exception) { liste = null }
            ana.post {
                bekciMesgul = false
                if (izlenenNo != k.no) return@post
                medyaListesi = adres
                val (sure, reklamSonda) = sondakiSure(liste ?: return@post)
                if (!reklamda) {
                    // Oynatıcı canlının biraz gerisinden oynar: reklam ekrana gelince (o kadar süre sonra) kaçılır,
                    // böylece programın son saniyeleri kaçmaz.
                    val gecikme = (if (hedef == aktif) oynatici.currentLiveOffset else C_UNSET).let { if (it <= 0 || it > 120_000) 30_000L else it }
                    if (reklamSonda && sure * 1000 >= gecikme - 3_000 && hedef == aktif && !menuAcik && !listeAcik) reklamaGir(hedef)
                } else {
                    // Reklam kuşağı arada kanal tanıtımlarıyla bölünür (yaklaşık 1 dk). Program en az 70 sn kesintisiz
                    // gelmeden dönülmez; yoksa tanıtım sırasında dönüp yeniden kaçılırdı.
                    if (!reklamSonda && sure >= 70.0) reklamdanDon()
                }
            }
        }
    } }

    private fun oynatilabilir(i: Int) = kanallar.getOrNull(i)?.let { it.linkler.isNotEmpty() || it.sayfa != null } == true

    private fun reklamaGir(asil: Int) {
        val kacis = when {
            oynatilabilir(geriKanal) && geriKanal != asil -> geriKanal
            asil != 0 -> 0
            else -> 1
        }
        reklamda = true
        reklamBaslangici = System.currentTimeMillis()
        asilKanal = asil
        kacisKanali = kacis
        kanalaGit(kacis)
        hatirlatmaKanali = asil
        durumGoster("Reklam arası · ${kanallar[asil].isim} başlayınca geri dönülecek\nHemen dönmek için OK")
        ana.removeCallbacks(hatirlatmayiKapat)
        ana.postDelayed(hatirlatmayiKapat, 10_000L)
    }

    private fun reklamdanDon() {
        val asil = asilKanal
        val buradaydi = aktif == kacisKanali
        reklamKacisiniBitir()
        if (buradaydi && asil in kanallar.indices) {
            kanalaGit(asil)
            kisaMesaj("${kanallar[asil].isim} programına dönüldü")
        }
    }

    // ---------------------------------------------------------------- Kanalın kendi sitesindeki oynatıcı (deneme)

    /*
     * Kanalın resmi sitesini uygulamanın içinde, sadece oynatıcı tam ekran görünecek şekilde açar.
     * Reklam arasında sitenin kendi reklamları oynar (bilgisayarda tarayıcıdan izlemekle aynı).
     * Güvenlik: sadece kanalın kendi alan adındaki sayfalara gidilir; başka siteye geçiş engellenir.
     */
    private var siteGorunum: android.webkit.WebView? = null
    private var siteAlanAdi = ""

    private val siteDuzenle: Runnable = object : Runnable { override fun run() {
        val w = siteGorunum ?: return
        if (w.visibility != View.VISIBLE) return
        w.evaluateJavascript(SITE_JS, null)
        ana.postDelayed(this, 3_000)
    } }

    private fun siteOynaticiylaAc(k: Kanal) {
        val adres = k.siteOynatici ?: return
        oynatici.stop()
        ana.removeCallbacks(takilmaKontrol)
        durumGizle()
        val w = siteGorunum ?: try {
            android.webkit.WebView(this).also { yeni ->
                yeni.isFocusable = false
                yeni.isFocusableInTouchMode = false
                yeni.setBackgroundColor(android.graphics.Color.BLACK)
                yeni.settings.javaScriptEnabled = true
                yeni.settings.domStorageEnabled = true
                yeni.settings.mediaPlaybackRequiresUserGesture = false
                yeni.settings.allowFileAccess = false
                yeni.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView, istek: android.webkit.WebResourceRequest): Boolean {
                        if (!istek.isForMainFrame) return false
                        val host = istek.url.host ?: return true
                        return !(host == siteAlanAdi || host.endsWith(".$siteAlanAdi"))
                    }
                    override fun onPageFinished(view: android.webkit.WebView, url: String) {
                        ana.removeCallbacks(siteDuzenle)
                        ana.postDelayed(siteDuzenle, 1_500)
                    }
                }
                (oynaticiGorunum.parent as android.view.ViewGroup).addView(yeni, 1,
                    android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                siteGorunum = yeni
            }
        } catch (e: Exception) {
            // Stick'te tarayıcı bileşeni yoksa normal oynatıcıyla devam.
            kisaMesaj("Site oynatıcısı bu cihazda açılamadı")
            ayarlar.edit().putBoolean("siteOynatici", false).apply()
            oynat(aktif, 0)
            return
        }
        siteAlanAdi = (android.net.Uri.parse(adres).host ?: "").removePrefix("www.")
        w.visibility = View.VISIBLE
        w.onResume()
        w.loadUrl(adres)
    }

    private fun siteOynaticiyiKapat() {
        val w = siteGorunum ?: return
        ana.removeCallbacks(siteDuzenle)
        if (w.visibility == View.VISIBLE) {
            w.loadUrl("about:blank")
            w.onPause()
            w.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------- Uyku zamanlayıcısı

    private var uykuBitis = 0L
    private val uykuSecenekleri = intArrayOf(0, 30, 60, 90)

    private fun uykuYazisi(): String {
        if (uykuBitis == 0L) return "Kapalı"
        val kalan = ((uykuBitis - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
        return "$kalan dk sonra"
    }

    /** Kapalı → 30 → 60 → 90 dk → Kapalı. Süre dolunca yayın durur ve uygulama kapanır. */
    private fun uykuDegistir() {
        val simdiki = if (uykuBitis == 0L) 0 else ((uykuBitis - System.currentTimeMillis()) / 60_000L + 1).toInt()
        val sonraki = uykuSecenekleri.firstOrNull { it > simdiki } ?: 0
        ana.removeCallbacks(uykuUyarisi)
        ana.removeCallbacks(uykuKapat)
        if (sonraki == 0) {
            uykuBitis = 0L
            kisaMesaj("Uyku zamanlayıcısı kapatıldı")
            return
        }
        uykuBitis = System.currentTimeMillis() + sonraki * 60_000L
        ana.postDelayed(uykuUyarisi, (sonraki - 1) * 60_000L)
        ana.postDelayed(uykuKapat, sonraki * 60_000L)
        kisaMesaj("HanımTV $sonraki dakika sonra kapanacak")
    }

    private val uykuUyarisi = Runnable { kisaMesaj("HanımTV 1 dakika sonra kapanacak. Kapatmamak için Ayarlar'dan uyku zamanlayıcısını kapatın.") }

    private val uykuKapat = Runnable {
        uykuBitis = 0L
        oynatici.stop()
        finish()
    }

    // ---------------------------------------------------------------- Kumanda

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuAcik) return menuTusu(keyCode)
        if (listeAcik) return listeTusu(keyCode)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> menuyuAc(MENU_ANA)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> kanalDegistir(+1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> kanalDegistir(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (hatirlatmaKanali >= 0 && durumYazisi.visibility == View.VISIBLE) {
                    val h = hatirlatmaKanali
                    hatirlatmaKanali = -1
                    ana.removeCallbacks(hatirlatmayiKapat)
                    durumGizle()
                    if (h != aktif) kanalaGit(h)
                } else listeyiAc()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> rakamBasildi(keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> rakamBasildi(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            // Geri tuşu uygulamadan yanlışlıkla çıkarmaz; sadece kanal bilgisini gösterir. Çıkmak için ev tuşu kullanılır.
            // Geri tuşu: bir önceki kanala dön (reklam arasında iki kanal arasında gidip gelmek için).
            KeyEvent.KEYCODE_BACK -> if (geriKanal in kanallar.indices && geriKanal != aktif) kanalaGit(geriKanal)
                else kanallar.getOrNull(aktif)?.let { bilgiyiGoster(it.no.toString(), it.isim) }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO ->
                kanallar.getOrNull(aktif)?.let { bilgiyiGoster(it.no.toString(), it.isim) }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---------------------------------------------------------------- Arka plan işleri

    /** Kanal listesini GitHub'dan günceller. Sıra sadece sizin dosyanızdaki "no" değerlerine göre olur. */
    /** Yayın akışını 3 saatte bir yeniler; olmazsa 20 dakika sonra tekrar dener. */
    private val akisGuncelle: Runnable = object : Runnable { override fun run() {
        arkaPlan.execute {
            val tamam = Epg.guncelle(this@MainActivity)
            ana.postDelayed(this, if (tamam) 3 * 60 * 60 * 1000L else 20 * 60 * 1000L)
        }
    } }

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
                if (k.linkler.isEmpty() && (k.youtube != null || k.sayfa != null)) continue
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

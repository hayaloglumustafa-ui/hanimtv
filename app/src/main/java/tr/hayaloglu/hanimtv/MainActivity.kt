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
    private val toplamSatir get() = kanallar.size + if (yeniSurum != null) 1 else 0

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
        oynatici.setMediaItem(yayinOgesi(k.linkler[link]))
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
        durumGoster("Yayın şu an gelmiyor, tekrar deneniyor…")
        val bekleme = (5_000L * hataSayisi).coerceAtMost(30_000L)
        ana.removeCallbacks(yenidenDene)
        ana.postDelayed(yenidenDene, bekleme)
    }

    private val yenidenDene = Runnable { if (kanallar.isNotEmpty()) oynat(aktif, 0) }

    private val takilmaKontrol = Runnable {
        if (oynatici.playbackState == Player.STATE_BUFFERING) sonrakiLinkiDene()
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

    private fun listeyiAc() {
        if (kanallar.isEmpty()) return
        listeSecili = aktif
        listeKutusu.visibility = View.VISIBLE
        bilgiKutusu.visibility = View.GONE
        listeyiCiz()
        listeZamanlayicisi()
    }

    private fun listeyiKapat() {
        listeKutusu.visibility = View.GONE
        ana.removeCallbacks(listeyiKendiligindenKapat)
    }

    private val listeyiKendiligindenKapat = Runnable { listeyiKapat() }

    private fun listeZamanlayicisi() {
        ana.removeCallbacks(listeyiKendiligindenKapat)
        ana.postDelayed(listeyiKendiligindenKapat, LISTE_KAPANMA_SURESI)
    }

    private fun listeyiCiz() {
        val toplam = toplamSatir
        if (toplam == 0) return
        listeSecili = listeSecili.coerceIn(0, toplam - 1)
        val sayfa = listeSecili / SAYFA_BOYU
        val sayfaSayisi = (toplam + SAYFA_BOYU - 1) / SAYFA_BOYU
        listeBaslik.text = buildString {
            append("Kanal listesi · ").append(sayfa + 1).append('/').append(sayfaSayisi)
            if (yeniSurum != null) append(" · Yeni sürüm var")
        }
        for (i in 0 until SAYFA_BOYU) {
            val t = satirlar[i]
            val indeks = sayfa * SAYFA_BOYU + i
            when {
                indeks < kanallar.size -> {
                    val k = kanallar[indeks]
                    t.text = String.format("%-4d %s", k.no, k.isim) + if (k.linkler.isEmpty() && k.youtube != null) "  · YouTube" else ""
                    t.setTextColor(if (calismayanlar.contains(k.no)) GRI else BEYAZ)
                    t.visibility = View.VISIBLE
                }
                indeks < toplam -> {
                    t.text = if (guncellemeIniyor) "Yeni sürüm indiriliyor…" else "Yeni sürümü kur"
                    t.setTextColor(BEYAZ)
                    t.visibility = View.VISIBLE
                }
                else -> t.visibility = View.INVISIBLE
            }
            t.typeface = if (indeks == aktif) kalinYazi else normalYazi
            t.setBackgroundResource(if (indeks == listeSecili) R.drawable.secili_satir else 0)
        }
    }

    private fun listeTusu(tus: Int): Boolean {
        val toplam = toplamSatir
        val sayfaSayisi = (toplam + SAYFA_BOYU - 1) / SAYFA_BOYU
        val satir = listeSecili % SAYFA_BOYU
        val sayfa = listeSecili / SAYFA_BOYU
        listeZamanlayicisi()
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
                if (listeSecili < kanallar.size) {
                    listeyiKapat()
                    if (listeSecili != aktif) kanalaGit(listeSecili) else bilgiyiGoster(kanallar[aktif].no.toString(), kanallar[aktif].isim)
                } else {
                    guncellemeyiBaslat()
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

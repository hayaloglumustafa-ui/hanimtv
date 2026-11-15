package tr.hayaloglu.hanimev

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Ev tuşunu Ana Ekran'a yönlendirir; Google TV'nin kendi ana ekranı kapatılmaz (sistem değişmez).
 *  - Kumandada Ev tuşuna basılınca Ana Ekran açılır.
 *  - Google TV ana ekranı yine de bir yerden açılırsa (stick açılırken gibi) hemen Ana Ekran'a geçilir.
 * Güvenlik: Ekran içeriğini okumaz; sadece tuş olayını ve öne gelen uygulamanın paket adını görür.
 */
class EvTusu : AccessibilityService() {
    private val googleTvEvleri = setOf(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
    )

    private fun evAc() {
        runCatching {
            startActivity(Intent(this, Ev::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) evAc()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() in googleTvEvleri) evAc()
    }

    override fun onInterrupt() {}
}

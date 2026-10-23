package tr.hayaloglu.hanimtv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stick açıldığında ya da uygulama güncellendiğinde HanımTV'yi kendiliğinden başlatır. */
class AcilisAlici : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val eylem = intent.action ?: return
        if (eylem != Intent.ACTION_BOOT_COMPLETED && eylem != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val ac = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(ac) }
    }
}

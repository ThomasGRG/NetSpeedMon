package jp.ikigai.netspeedmon.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiverService : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val serviceIntent = Intent(context, NetSpeedMonService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
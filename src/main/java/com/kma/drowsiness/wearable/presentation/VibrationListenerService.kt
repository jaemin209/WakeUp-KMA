package com.kma.drowsiness.wearable.presentation


import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService


class VibrationListenerService : WearableListenerService() {

    private val PATH_VIBRATE = "/vibrate_command"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_VIBRATE) {
            Log.d("Watch", "진동 명령 수신! 진동 실행.")
            startStrongVibration()
        }
    }

    private fun startStrongVibration() {
        val vibrator = getSystemService(Vibrator::class.java) as Vibrator

        val pattern = longArrayOf(0, 1000)

        if (vibrator.hasVibrator()) {
            val effect = VibrationEffect.createWaveform(pattern, -1) // -1이 한번만 진동임
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
        Log.i("Watch", "강력한 진동 실행됨.")
    }
}
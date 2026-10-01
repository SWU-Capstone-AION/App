package com.aion.hrtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 테스트 전용: 워치 대신 adb로 심박값을 넣는다. 실제 수신과 같은 경로(onReceived)를 탄다.
 *   adb shell am broadcast -n com.aion.hrtest/.DebugInjectReceiver --ei bpm 80
 *   adb shell am broadcast -n com.aion.hrtest/.DebugInjectReceiver --ez behavior true
 *   adb shell am broadcast -n com.aion.hrtest/.DebugInjectReceiver --ez alert true
 *   adb shell am broadcast -n com.aion.hrtest/.DebugInjectReceiver --ez reset true
 */
class DebugInjectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = HrRepository.get(context)
        if (intent.getBooleanExtra("reset", false)) repo.resetBaseline()
        if (intent.hasExtra("behavior")) repo.behaviorActive.value = intent.getBooleanExtra("behavior", false)
        val bpm = intent.getIntExtra("bpm", -1)
        if (bpm > 0) {
            val now = System.currentTimeMillis()
            repo.onReceived(HrReceived(bpm = bpm, at = now, receivedAt = now), sourceNode = "adb")
        }
        if (intent.getBooleanExtra("alert", false)) repo.onVisionAlert()
    }
}

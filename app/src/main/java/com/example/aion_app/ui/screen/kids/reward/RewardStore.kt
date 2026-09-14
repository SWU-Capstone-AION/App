package com.example.aion_app.ui.kids.reward

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 앱 전체에서 하나만 쓰는 저장 공간 (파일 이름: kids_reward) */
private val Context.rewardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kids_reward"
)

class RewardStore(private val context: Context) {

    companion object {
        /**
         * 호흡 가이드로 하루에 받을 수 있는 박스 개수.
         * 팀에서 정해지면 이 숫자만 바꾸면 된다. (매번 주려면 999처럼 크게)
         */
        const val BREATH_DAILY_LIMIT = 1

        /** 아직 안 연 박스가 너무 쌓이지 않도록 하는 상한 */
        const val MAX_UNOPENED_BOXES = 3

        private val KEY_UNOPENED = intPreferencesKey("unopened_boxes")
        private val KEY_BREATH_DATE = stringPreferencesKey("breath_date")
        private val KEY_BREATH_COUNT = intPreferencesKey("breath_count")

        /** 구슬 색마다 개수를 따로 저장 (예: marble_GREEN = 3) */
        private fun marbleKey(marble: Marble) = intPreferencesKey("marble_${marble.name}")

        /** 오늘 날짜를 "2026-09-14" 모양 글자로 */
        private fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    }

    /** 아직 안 연 박스 개수 (0이면 메인 홈에 박스를 안 보여준다) */
    val unopenedBoxes: Flow<Int> =
        context.rewardDataStore.data.map { it[KEY_UNOPENED] ?: 0 }

    /** 색깔별 구슬 개수 — 구슬 주머니 화면에서 사용 */
    val marbleCounts: Flow<Map<Marble, Int>> =
        context.rewardDataStore.data.map { prefs ->
            Marble.values().associateWith { prefs[marbleKey(it)] ?: 0 }
        }

    /** 모은 구슬 총 개수 */
    val totalMarbles: Flow<Int> =
        marbleCounts.map { counts -> counts.values.sum() }

    /**
     * 활동을 마쳤을 때 박스를 1개 준다.
     * 호흡 가이드는 하루 상한이 있어서 넘으면 안 주고 false를 돌려준다.
     */
    suspend fun grant(source: RewardSource): Boolean {
        var granted = false

        context.rewardDataStore.edit { prefs ->
            // 1) 호흡 가이드면 오늘 몇 번 받았는지 먼저 확인
            if (source == RewardSource.BREATHING) {
                val savedDate = prefs[KEY_BREATH_DATE] ?: ""
                val todayStr = today()

                // 날짜가 바뀌었으면 0부터 다시 센다
                val countToday = if (savedDate == todayStr) {
                    prefs[KEY_BREATH_COUNT] ?: 0
                } else {
                    0
                }

                if (countToday >= BREATH_DAILY_LIMIT) {
                    return@edit   // 상한을 넘었으면 아무것도 안 함
                }

                prefs[KEY_BREATH_DATE] = todayStr
                prefs[KEY_BREATH_COUNT] = countToday + 1
            }

            // 2) 박스 1개 추가 (상한까지만)
            val current = prefs[KEY_UNOPENED] ?: 0
            if (current < MAX_UNOPENED_BOXES) {
                prefs[KEY_UNOPENED] = current + 1
            }
            granted = true
        }

        return granted
    }

    /**
     * 박스를 연다. 박스가 없으면 null.
     * 열면 구슬 1개를 랜덤으로 뽑아 주머니에 넣는다.
     */
    suspend fun openBox(): Marble? {
        val boxes = unopenedBoxes.first()
        if (boxes <= 0) return null

        val marble = Marble.random()

        context.rewardDataStore.edit { prefs ->
            prefs[KEY_UNOPENED] = (prefs[KEY_UNOPENED] ?: 0) - 1
            val key = marbleKey(marble)
            prefs[key] = (prefs[key] ?: 0) + 1
        }

        return marble
    }

    /** 테스트용 — 모은 구슬과 박스를 전부 지운다 */
    suspend fun clearAll() {
        context.rewardDataStore.edit { it.clear() }
    }
}
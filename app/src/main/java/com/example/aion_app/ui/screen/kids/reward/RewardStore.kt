package com.example.aion_app.ui.screen.kids.reward

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

// ============================================================
// 구슬 저장소
// ============================================================
// 태블릿 안에 파일로 저장한다(DataStore). 앱을 껐다 켜도 값이 남는다.
//
// 왜 서버(Firestore/Django)가 아니라 기기 저장인가
//   구슬은 아이가 자기 태블릿에서만 보는 값이라 교사 앱이나 리포트가 읽을 일이 없다.
//   Firestore 에 넣으면 보안 규칙의 update 조건을 또 열어야 해서 위험만 늘어난다.
//   나중에 교사 리포트에 "이번 주 구슬 5개" 를 넣고 싶어지면 그때 Django 로 올리면 된다.
//
// ⚠ 아이 계정별로 나뉘지 않는다. 한 태블릿을 여러 아이가 돌려 쓰면 구슬이 섞인다.
//   지금은 아동 1명 = 태블릿 1대 전제라 그대로 두었다.
//   나중에 나눠야 하면 키 앞에 uid 를 붙이면 된다. (marble_{uid}_GREEN)
private val Context.rewardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kids_reward"
)

class RewardStore(context: Context) {

    // Activity 가 아니라 Application 컨텍스트를 들고 있어야 화면이 사라져도 누수가 없다
    private val appContext = context.applicationContext

    companion object {
        // ----------------------------------------------------
        // 호흡 가이드로 하루에 받을 수 있는 상자 개수
        // ----------------------------------------------------
        // ⚠ 팀에서 아직 안 정한 값이라 임시로 1 로 뒀다. 이 숫자만 바꾸면 된다.
        //   (매번 주려면 999 처럼 크게)
        //
        // 상한을 둔 이유
        //   호흡 가이드는 아이가 고르는 놀이가 아니라 '상동행동이 감지되면 뜨는' 화면이다.
        //   완료할 때마다 구슬을 주면 아이 입장에서 "손을 흔들면 구슬이 생긴다" 로
        //   이어질 수 있다. 보상이 오히려 그 행동을 늘리는 방향이 된다.
        //   잡초 뽑기 · 칠판 지우기(아이가 직접 고르는 활동)에는 상한이 없다.
        const val BREATH_DAILY_LIMIT = 999

        /** 안 연 상자가 쌓이지 않도록 하는 상한 */
        const val MAX_UNOPENED_BOXES = 3

        private val KEY_UNOPENED = intPreferencesKey("unopened_boxes")
        private val KEY_BREATH_DATE = stringPreferencesKey("breath_date")
        private val KEY_BREATH_COUNT = intPreferencesKey("breath_count")

        /** 색마다 개수를 따로 저장한다 (예: marble_GREEN = 3) */
        private fun marbleKey(marble: Marble) = intPreferencesKey("marble_${marble.name}")

        /** 오늘 날짜를 "2026-09-14" 모양 글자로 */
        private fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    }

    /** 아직 안 연 상자 개수. 0 이면 홈에 상자를 안 보여준다 */
    val unopenedBoxes: Flow<Int> =
        appContext.rewardDataStore.data.map { it[KEY_UNOPENED] ?: 0 }

    /** 색깔별 구슬 개수 — 구슬 주머니 화면에서 쓴다 */
    val marbleCounts: Flow<Map<Marble, Int>> =
        appContext.rewardDataStore.data.map { prefs ->
            Marble.entries.associateWith { prefs[marbleKey(it)] ?: 0 }
        }

    /** 모은 구슬 총 개수 — 상단 배지에 쓴다 */
    val totalMarbles: Flow<Int> =
        marbleCounts.map { counts -> counts.values.sum() }

    /**
     * 활동을 마쳤을 때 상자를 1개 준다.
     *
     * 호흡 가이드는 하루 상한이 있어서 넘으면 아무것도 주지 않는다.
     * @return 실제로 상자를 줬으면 true
     */
    suspend fun grant(source: RewardSource): Boolean {
        var granted = false

        appContext.rewardDataStore.edit { prefs ->
            // 1) 호흡 가이드면 오늘 몇 번 받았는지 먼저 본다
            if (source == RewardSource.BREATHING) {
                val savedDate = prefs[KEY_BREATH_DATE] ?: ""
                val todayStr = today()

                // 날짜가 바뀌었으면 0 부터 다시 센다
                val countToday =
                    if (savedDate == todayStr) prefs[KEY_BREATH_COUNT] ?: 0 else 0

                if (countToday >= BREATH_DAILY_LIMIT) {
                    return@edit   // 상한을 넘었으면 상자를 주지 않는다
                }

                prefs[KEY_BREATH_DATE] = todayStr
                prefs[KEY_BREATH_COUNT] = countToday + 1
            }

            // 2) 상자 1개 추가 (상한까지만)
            val current = prefs[KEY_UNOPENED] ?: 0
            if (current < MAX_UNOPENED_BOXES) {
                prefs[KEY_UNOPENED] = current + 1
            }
            granted = true
        }

        return granted
    }

    /**
     * 상자를 연다. 상자가 없으면 null.
     * 열면 구슬 1개를 랜덤으로 뽑아 주머니에 넣는다.
     */
    suspend fun openBox(): Marble? {
        if (unopenedBoxes.first() <= 0) return null

        val marble = Marble.random()

        appContext.rewardDataStore.edit { prefs ->
            prefs[KEY_UNOPENED] = ((prefs[KEY_UNOPENED] ?: 0) - 1).coerceAtLeast(0)
            val key = marbleKey(marble)
            prefs[key] = (prefs[key] ?: 0) + 1
        }

        return marble
    }

    /** 테스트용 — 모은 구슬과 상자를 전부 지운다 */
    suspend fun clearAll() {
        appContext.rewardDataStore.edit { it.clear() }
    }
}
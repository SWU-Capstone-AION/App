package com.example.aion_app.ui.screen.kids.reward

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
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
// 저장 형태
//   얻은 순서대로 "색이름:날짜" 를 이어 붙인 글자 하나로 둔다.
//   예: "GREEN:2026-09-14,PINK:2026-09-15"
//   주머니에서 구슬을 누르면 이 날짜가 보인다.
//
// 아이 계정별로 따로 저장한다
//   저장 이름 뒤에 로그인한 아이의 uid 를 붙인다. (marble_history_{uid})
//   그래서 한 태블릿에서 김지우 · 이주미 · 전소미가 번갈아 로그인해도
//   각자 자기 구슬만 보인다.
//   로그인 정보가 없을 때(테스트 화면 등)는 "guest" 칸을 쓴다.
private val Context.rewardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kids_reward"
)

class RewardStore(
    context: Context,
    // 지금 로그인한 아이. 기본값으로 Firebase 로그인 정보를 읽는다
    uid: String = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
) {

    // Activity 가 아니라 Application 컨텍스트를 들고 있어야 화면이 사라져도 누수가 없다
    private val appContext = context.applicationContext

    // ----------------------------------------------------
    // 저장 이름 — 아이마다 따로
    // ----------------------------------------------------
    private val keyUnopened = intPreferencesKey("unopened_boxes_$uid")
    private val keyHistory = stringPreferencesKey("marble_history_$uid")
    private val keyBreathDate = stringPreferencesKey("breath_date_$uid")
    private val keyBreathCount = intPreferencesKey("breath_count_$uid")

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

        /** 오늘 날짜를 "2026-09-14" 모양 글자로 */
        private fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())

        /** 저장용 글자로 바꾼다 */
        private fun List<MarbleRecord>.toRaw(): String =
            joinToString(",") { "${it.marble.name}:${it.date ?: ""}" }
    }

    /** 저장된 값을 구슬 목록으로 읽는다 (얻은 순서대로) */
    private fun Preferences.readHistory(): List<MarbleRecord> {
        val raw = this[keyHistory] ?: return emptyList()
        return raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                // "GREEN:2026-09-14" 또는 날짜 없이 "GREEN"
                val name = entry.substringBefore(":")
                val date = entry.substringAfter(":", "").ifBlank { null }
                Marble.fromName(name)?.let { MarbleRecord(it, date) }
            }
    }

    /**
     * 받아뒀지만 아직 안 연 상자 개수.
     * 0 보다 크면 홈에 상자 팝업이 뜬다. 하나 열면 하나 줄어든다.
     */
    val unopenedBoxes: Flow<Int> =
        appContext.rewardDataStore.data.map { it[keyUnopened] ?: 0 }

    /** 얻은 순서대로 늘어놓은 구슬 목록 (색 + 얻은 날짜) */
    val marbleHistory: Flow<List<MarbleRecord>> =
        appContext.rewardDataStore.data.map { it.readHistory() }

    /** 모은 구슬 총 개수 — 상단 배지에 쓴다 */
    val totalMarbles: Flow<Int> =
        marbleHistory.map { it.size }

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
                val savedDate = prefs[keyBreathDate] ?: ""
                val todayStr = today()

                // 날짜가 바뀌었으면 0 부터 다시 센다
                val countToday =
                    if (savedDate == todayStr) prefs[keyBreathCount] ?: 0 else 0

                if (countToday >= BREATH_DAILY_LIMIT) {
                    return@edit   // 상한을 넘었으면 상자를 주지 않는다
                }

                prefs[keyBreathDate] = todayStr
                prefs[keyBreathCount] = countToday + 1
            }

            // 2) 상자 1개 추가 (상한까지만)
            val current = prefs[keyUnopened] ?: 0
            if (current < MAX_UNOPENED_BOXES) {
                prefs[keyUnopened] = current + 1
            }
            granted = true
        }

        return granted
    }

    /**
     * 상자를 연다. 상자가 없으면 null.
     * 열면 구슬 1개를 랜덤으로 뽑아 목록 맨 뒤에 붙인다.
     */
    suspend fun openBox(): Marble? {
        if (unopenedBoxes.first() <= 0) return null

        val marble = Marble.random()

        appContext.rewardDataStore.edit { prefs ->
            prefs[keyUnopened] = ((prefs[keyUnopened] ?: 0) - 1).coerceAtLeast(0)

            val updated = prefs.readHistory() + MarbleRecord(marble, today())
            prefs[keyHistory] = updated.toRaw()
        }

        return marble
    }

    /** 테스트용 — 지금 로그인한 아이의 구슬과 상자만 지운다 */
    suspend fun clearAll() {
        appContext.rewardDataStore.edit { prefs ->
            prefs.remove(keyUnopened)
            prefs.remove(keyHistory)
            prefs.remove(keyBreathDate)
            prefs.remove(keyBreathCount)
        }
    }
}
package com.example.aion_app.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aion_app.monitor.rule.BEHAVIORS
import com.example.aion_app.monitor.rule.DEFAULT_SENSITIVITY
import com.example.aion_app.monitor.rule.Frame
import com.example.aion_app.monitor.rule.GROUPS
import com.example.aion_app.monitor.rule.RuleV3
import com.example.aion_app.monitor.rule.RuleV3Output
import com.example.aion_app.monitor.rule.behaviorName
import com.example.aion_app.monitor.rule.excludeFromTouchRule
import com.example.aion_app.monitor.rule.partLabel
import com.example.aion_app.monitor.ui.theme.ShareTechMono
import kotlin.math.max

// ============================================================
// 규칙모델 v3 모니터링 화면 (HTML 대시보드의 앱 버전)
//   - 관절 33개 + 손 21×2 오버레이
//   - 앙상블 확률 / 지속 시간 / 규칙·학습 점수 게이지
//   - 행동 17가지 점수 (묶음별)
//   - 터치 정도 f, 게임 중 스위치, fps
// ============================================================

private val Blue = Color(0xFF34C6FF)
private val Amber = Color(0xFFFFB020)
private val Red = Color(0xFFFF5A3C)
private val Green = Color(0xFF3CE08A)
private val Ink = Color(0xFFCFE6FF)
private val InkDim = Color(0xFF7FA6CF)
private val PanelBg = Color(0xDB050B12)
private val BarBg = Color(0xFF0A1622)
private val Line = Color(0x4D34C6FF)
private val Mono = ShareTechMono
private val PanelShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
private val ButtonShape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)

private val HandColor = Color(0xFF7FF0FF)
private val HandWeak = Color(0xFF6C7F94)

private val GROUP_COLOR = mapOf(
    "전신" to Color(0xFFB48CFF), "머리" to Color(0xFFFFD36B), "어깨" to Color(0xFFFF9F5A),
    "손·손가락" to Color(0xFF34C6FF), "감각" to Color(0xFFFF7AD9), "전신(실험)" to Color(0xFF8FA3B8),
)
private val STAGE_TXT = listOf("안정", "주의", "위험")
private val STAGE_COLOR = listOf(Green, Amber, Red)

private val POSE_BONES = arrayOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19, 12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 24 to 26, 25 to 27, 26 to 28, 27 to 29, 28 to 30, 29 to 31, 30 to 32, 27 to 31, 28 to 32,
)
private val HAND_BONES = arrayOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8, 5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16, 13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)
private val FINGER_TIPS = setOf(4, 8, 12, 16, 20)

/**
 * 포즈 33 + 손 오버레이. 프리뷰(FILL_CENTER, 전면 카메라라 좌우 반전되어 보임) 위에 그린다.
 * 검출은 반전하지 않은 원본으로 했으므로 x 를 뒤집어 그린다.
 */
@Composable
fun RuleV3Overlay(frame: Frame?, imageWidth: Int, imageHeight: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val f = frame ?: return@Canvas
        if (imageWidth == 0 || imageHeight == 0) return@Canvas
        val scale = max(size.width / imageWidth, size.height / imageHeight)
        val ox = (size.width - imageWidth * scale) / 2f
        val oy = (size.height - imageHeight * scale) / 2f
        // frame 좌표는 원본 이미지 px → 반전 + 화면 크기로
        fun pt(x: Double, y: Double) = Offset(((imageWidth - x) * scale + ox).toFloat(), (y * scale + oy).toFloat())

        val p = f.p
        for ((a, b) in POSE_BONES) {
            if (p[a].v < 0.3 || p[b].v < 0.3) continue
            drawLine(Blue, pt(p[a].x, p[a].y), pt(p[b].x, p[b].y), strokeWidth = 5f)
        }
        p.forEachIndexed { i, q ->
            if (q.v < 0.3) return@forEachIndexed
            drawCircle(if (i <= 10) Color(0xFFFFD36B) else Color(0xFFD6F0FF), radius = if (i <= 10) 5f else 8f, center = pt(q.x, q.y))
        }
        for (hd in f.hands.values) {
            val pts = hd.pts ?: continue
            if (!hd.model) continue
            val col = if (hd.score >= 0.8) HandColor else HandWeak
            for ((a, b) in HAND_BONES) drawLine(col, pt(pts[a].x, pts[a].y), pt(pts[b].x, pts[b].y), strokeWidth = 3f)
            pts.forEachIndexed { i, q -> drawCircle(col, radius = if (i in FINGER_TIPS) 6f else 4f, center = pt(q.x, q.y)) }
            drawCircle(col.copy(alpha = 0.6f), radius = 9f, center = pt(hd.palm.x, hd.palm.y), style = Stroke(2f))
        }
    }
}

/** HUD 대시보드 (카메라·오버레이 위에 겹침) */
@Composable
fun RuleV3Dashboard(
    out: RuleV3Output?,
    fps: Int,
    inferenceMs: Long?,
    running: Boolean,
    gameActive: Boolean,
    handsOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleGame: () -> Unit,
    onToggleHands: () -> Unit,
    onCycleSensitivity: () -> Unit,
    record: RecordUi,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        // 버튼·패널 영역의 터치는 '태블릿 터치(기능적 행동)'로 세지 않는다 (HTML 과 같음)
        Box(modifier = Modifier.excludeFromTouchRule("v3_top")) {
            TopBar(out, fps, inferenceMs, running, onStart, onStop, onBack)
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            Column(
                modifier = Modifier.width(300.dp).fillMaxHeight().excludeFromTouchRule("v3_left")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                RecordPanel(record)
                OutputPanel(out, onCycleSensitivity)
                TouchPanel(out, gameActive, handsOn, onToggleGame, onToggleHands)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.width(320.dp).fillMaxHeight().excludeFromTouchRule("v3_right")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                BehaviorPanel(out)
            }
        }
    }
}

@Composable
private fun TopBar(
    out: RuleV3Output?, fps: Int, inferenceMs: Long?, running: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onBack: (() -> Unit)?,
) {
    val tick = out?.tick
    val stage = tick?.stage ?: 0
    val (statusText, statusColor) = if (!running) "대기 중" to InkDim else STAGE_TXT[stage] to STAGE_COLOR[stage]
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xD1030810)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) HudButton("← 뒤로", Ink, onBack)
            Text("AION · 규칙모델 v${RuleV3.MODEL_VERSION}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val low = fps < RuleV3.LOW_FPS
            Text(
                "$fps fps" + (if (low) " · 손가락 정확도 낮음" else "") + (inferenceMs?.let { " · ${it}ms" } ?: ""),
                color = if (low) Amber else InkDim, fontFamily = Mono, fontSize = 12.sp,
            )
            if (running) HudButton("■ 정지", Red, onStop) else HudButton("▶ 시작", Blue, onStart)
        }
    }
}

@Composable
private fun OutputPanel(out: RuleV3Output?, onCycleSensitivity: () -> Unit) {
    val t = out?.tick
    val dur = out?.durationS ?: DEFAULT_SENSITIVITY.durationS
    Panel("판정 (0.5초마다)") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("민감도: ${out?.sensitivity?.label ?: if (out == null) DEFAULT_SENSITIVITY.label else "사용자 지정"}", Amber, onCycleSensitivity)
        }
        Text(
            "앙상블 = %.1f × 학습 + %.1f × 규칙 · τ=%.2f 이상이 %.1f초 쌓이면 위험".format(
                out?.ensembleW ?: DEFAULT_SENSITIVITY.ensembleW, 1 - (out?.ensembleW ?: DEFAULT_SENSITIVITY.ensembleW),
                out?.tau ?: DEFAULT_SENSITIVITY.tau, dur,
            ),
            color = InkDim, fontFamily = Mono, fontSize = 10.sp,
        )
        Gauge("앙상블 확률", t?.p, t?.p?.let { "%.2f".format(it) } ?: "—",
            tag = t?.mode?.label ?: "대기", color = t?.stage?.let { STAGE_COLOR[it] } ?: Blue)
        val d = t?.d ?: 0.0
        Gauge("지속 시간", d, "%.1fs / %.1fs".format(d * dur, dur),
            color = if (d >= 0.999) Red else if (d >= 0.5) Amber else Blue)
        Gauge("규칙모델", t?.pRule, t?.pRule?.let { "%.2f".format(it) } ?: "판단 불가",
            tag = if (t != null && (t.pRuleRaw ?: 0.0) > 0.05) t.dominantName ?: "" else if (t?.ruleCalibrated == true) "보정" else "미보정")
        Gauge("학습모델", t?.pDl, t?.pDl?.let { "%.2f".format(it) } ?: "미연결")
        Text(
            "서버 부위: ${partLabel(t?.part) ?: "—"}" + (t?.part?.let { " ($it)" } ?: "") +
                " · 행동: ${t?.behaviorType?.let { c -> BEHAVIORS.firstOrNull { it.typeCode == c }?.typeLabel ?: c } ?: "—"}",
            color = Ink, fontFamily = Mono, fontSize = 11.sp,
        )
        Text(
            "위험 ${out?.episodesTotal ?: 0}회 · 알림 ${out?.alertsSent ?: 0}회" +
                (if (t?.alert?.repeated == true) " · 10분 안 반복 발생" else ""),
            color = InkDim, fontFamily = Mono, fontSize = 11.sp,
        )
        if (t != null && !t.ruleCalibrated) {
            Text("규칙모델 확률이 아직 보정되지 않았습니다. 지금 값은 설계 점수입니다.", color = InkDim, fontSize = 10.sp)
        }
    }
}

/** 기록 패널에 필요한 값과 버튼 동작 */
class RecordUi(
    val recording: Boolean,
    val label: Boolean,
    val rows: Int,
    val savedPath: String?,
    val onToggleRecord: () -> Unit,
    val onToggleLabel: () -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
)

@Composable
private fun RecordPanel(r: RecordUi) {
    Panel("기록 (0.5초마다 CSV)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(if (r.recording) "■ 기록 멈춤" else "● 기록 시작", if (r.recording) Red else Blue, r.onToggleRecord)
            HudButton("저장", if (r.rows > 0) Ink else InkDim, r.onSave)
            if (r.savedPath != null) HudButton("공유", Green, r.onShare)
        }
        // 라벨: 시험하는 사람이 '지금 상동행동을 하는 중'을 표시 → CSV 의 label 칸 (정답)
        Box(
            modifier = Modifier.fillMaxWidth().clip(ButtonShape)
                .background(if (r.label) Red.copy(alpha = 0.85f) else BarBg)
                .border(1.dp, if (r.label) Red else Line, ButtonShape)
                .clickable { r.onToggleLabel() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (r.label) "라벨: 상동행동 중 (누르면 끝)" else "라벨: 평소 (상동행동 시작할 때 누르기)",
                color = if (r.label) Color.White else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "${r.rows}줄" + (if (r.recording) " · 기록 중" else "") + (r.savedPath?.let { "\n저장: $it" } ?: ""),
            color = InkDim, fontFamily = Mono, fontSize = 10.sp,
        )
    }
}

@Composable
private fun TouchPanel(out: RuleV3Output?, gameActive: Boolean, handsOn: Boolean, onToggleGame: () -> Unit, onToggleHands: () -> Unit) {
    val touch = out?.tick?.touch
    Panel("기능적 행동 (터치)") {
        Gauge("터치 정도 f", touch?.f ?: 0.0, "%.2f".format(touch?.f ?: 0.0),
            tag = touch?.let { "탭 ${it.taps}" + (it.tapInterval?.let { iv -> " · 간격 %.2fs".format(iv) } ?: "") } ?: "")
        Text("화면을 두드리면 손·팔·손가락 행동 점수 × (1 − 0.8 × f)", color = InkDim, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(if (gameActive) "게임 중 ●" else "게임 중", if (gameActive) Amber else InkDim, onToggleGame)
            HudButton(if (handsOn) "손 모델 켬" else "손 모델 끔 (빠름)", if (handsOn) Blue else InkDim, onToggleHands)
        }
    }
}

@Composable
private fun BehaviorPanel(out: RuleV3Output?) {
    val ev = out?.tick?.ev
    Panel("행동별 점수 (최근 2초)") {
        for (g in GROUPS) {
            Text(g, color = GROUP_COLOR[g] ?: Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp))
            for (b in BEHAVIORS.filter { it.group == g }) {
                val e = ev?.beh?.get(b.key)
                val name = behaviorName(b.key, e) + (if (b.experimental) " (실험)" else "")
                val tag = when {
                    e == null -> "인식 약함"
                    e.suppressed != null -> "↓ ${e.suppressed}"
                    e.functional > 0 && e.rawScore >= 0.5 -> "터치 ↓"
                    else -> ""
                }
                Gauge(name, e?.score ?: 0.0, e?.let { "%.2f".format(it.score) } ?: "—", tag = tag,
                    color = GROUP_COLOR[g] ?: Blue, compact = true)
            }
        }
    }
}

// ---------- 작은 부품 ----------
@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(PanelShape).background(PanelBg).border(1.dp, Line, PanelShape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(title, color = Blue, fontFamily = Mono, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun Gauge(label: String, value: Double?, text: String, tag: String = "", color: Color = Blue, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontSize = if (compact) 11.sp else 12.sp, modifier = Modifier.weight(1f))
            if (tag.isNotEmpty()) Text(tag, color = InkDim, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Text(text, color = Ink, fontFamily = Mono, fontSize = if (compact) 11.sp else 12.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(if (compact) 4.dp else 6.dp).background(BarBg)) {
            val frac = (value ?: 0.0).coerceIn(0.0, 1.0).toFloat()
            if (frac > 0f) Box(modifier = Modifier.fillMaxWidth(frac).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun HudButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(ButtonShape).border(1.dp, color, ButtonShape).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

package com.example.aion_app.monitor.ml

import kotlin.math.abs
import kotlin.math.sqrt

// ============================================================
// 학습모델 입력 만들기 (안드로이드 코드 없음 → 단위 테스트 가능)
// ============================================================
// 카메라에서 들어오는 MediaPipe 좌표를 학습 때(build_dataset.py + train_keras.py)와
// 똑같은 순서로 손질해서 모델에 넣을 [60프레임 x 26채널] 숫자 묶음을 만든다.
//
//  ① 30fps로 맞추기      build_dataset.resample()  — 시간 기준 선형 보간
//                        카메라 fps가 들쭉날쭉해도 60프레임 = 항상 2초가 되게
//  ② 빈 프레임 채우기     load_mediapipe_csv()      — 사람이 안 잡힌 프레임은 직전 좌표 복사
//  ③ 어깨 기준으로 맞추기  normalize_landmarks()     — 어깨 가운데 = 0, 어깨너비 = 1
//  ④ 60프레임씩 자르기    make_windows()            — 0.5초(15프레임)마다 한 번
//                        좌표가 80% 미만으로 잡혔거나 튐이 3번 넘으면 건너뜀
//  ⑤ 속도로 바꾸기        train_keras.py            — 프레임 사이 변화량, 첫 칸은 0
//  ⑥ 표준화              train_keras.py            — (값 - mean) / std  (model_meta.json)
//
// ⚠ 학습 때와 다른 점 하나
//   학습 때 어깨너비 평활은 '앞뒤 61프레임 중앙값'이었지만
//   실시간에서는 미래 프레임이 없으므로 '최근 61프레임 중앙값'을 쓴다.
//   하한·상한 기준(영상 전체 중앙값)도 '최근 1분 중앙값'으로 대신한다.
// ============================================================
class MlFeatureBuilder(
    /** 채널마다 MediaPipe 관절 번호 (model_meta.json 의 coordinate_columns 순서) */
    private val landmarkIndex: IntArray,
    /** 채널마다 x 면 true, y 면 false */
    private val isX: BooleanArray,
    private val mean: FloatArray,
    private val std: FloatArray,
    /** null 이면 자르지 않음 (model_meta.json 의 clip_abs) */
    private val clipAbs: FloatArray? = null,
    val window: Int = 60,
    val stride: Int = 15,
    targetFps: Double = 30.0,
) {
    val channels = landmarkIndex.size

    init {
        require(isX.size == channels && mean.size == channels && std.size == channels) {
            "채널 수가 안 맞음: 관절 ${channels} / mean ${mean.size} / std ${std.size}"
        }
        require(clipAbs == null || clipAbs.size == channels) { "clip_abs 채널 수가 안 맞음" }
    }

    /** 결과 */
    sealed class Output {
        /** 모델에 바로 넣을 값. 길이 = window * channels, [프레임][채널] 순서 */
        class Ready(val input: FloatArray) : Output()

        /** 이번 차례는 학습 때도 버렸을 조각이라 판정하지 않음 */
        class Skipped(val reason: String) : Output()
    }

    private val gridStepMs = 1000.0 / targetFps

    // ── ① 리샘플용: 직전 원본 프레임 ──────────────────────
    private var prevT = -1.0
    private var prevPts: FloatArray? = null     // 33 * 2 (x0, y0, x1, y1 ...)
    private var prevValid = false
    private var nextGridT = 0.0

    // ── ③ 어깨너비 기록 ──────────────────────────────────
    private val shortWidths = RingDouble(SCALE_SMOOTH)      // 최근 61프레임
    private val longWidths = RingDouble(LONG_HISTORY)       // 최근 1분

    // ── ④ 최근 60프레임 (정규화된 좌표) ────────────────────
    private val frames = Array(window) { FloatArray(channels) }
    private val frameValid = BooleanArray(window)
    private val frameJump = BooleanArray(window)
    private var head = 0          // 다음에 쓸 자리
    private var filled = 0        // 쌓인 프레임 수 (최대 window)
    private var sinceLast = 0     // 마지막 판정 이후 프레임 수
    private var prevCx = Double.NaN
    private var prevCy = Double.NaN

    /** 처음 상태로 (감지를 멈췄다 다시 켤 때) */
    fun reset() {
        prevT = -1.0; prevPts = null; prevValid = false; nextGridT = 0.0
        shortWidths.clear(); longWidths.clear()
        head = 0; filled = 0; sinceLast = 0
        prevCx = Double.NaN; prevCy = Double.NaN
    }

    /**
     * 카메라 프레임 1장.
     * @param points MediaPipe 33관절의 x, y (0~1 비율 좌표), 길이 66. 사람이 안 잡혔으면 null
     * @return 판정할 차례가 된 결과들 (보통 비어 있고, 0.5초마다 1개)
     */
    fun addFrame(timestampMs: Long, points: FloatArray?): List<Output> {
        val t = timestampMs.toDouble()
        val out = ArrayList<Output>(1)

        // 시간이 거꾸로 가거나 1초 넘게 끊겼으면 (앱 일시정지 등) 처음부터
        if (prevT >= 0 && (t <= prevT || t - prevT > MAX_GAP_MS)) {
            if (t <= prevT) return out
            reset()
        }

        // ② 사람이 안 잡힌 프레임은 직전 좌표 복사 (아직 한 번도 안 잡혔으면 기다림)
        val valid = points != null
        val pts = points ?: prevPts ?: return out
        require(pts.size >= 33 * 2) { "points 길이는 66 이상이어야 함" }

        if (prevPts == null) {
            // 첫 프레임: 그대로 한 칸
            pushGridFrame(pts, valid, out)
            prevT = t; prevPts = pts.copyOf(); prevValid = valid
            nextGridT = t + gridStepMs
            return out
        }

        // ① 직전 프레임 ~ 지금 프레임 사이에 30fps 칸이 오면 보간해서 채움
        val p0 = prevPts!!
        val span = t - prevT
        val tmp = FloatArray(pts.size)
        while (nextGridT <= t + 1e-6) {
            val a = ((nextGridT - prevT) / span).toFloat()
            for (i in tmp.indices) tmp[i] = p0[i] + (pts[i] - p0[i]) * a
            // 검출 여부는 더 가까운 원본 프레임 것을 씀 (학습 때와 같음)
            val v = if (a < 0.5f) prevValid else valid
            pushGridFrame(tmp, v, out)
            nextGridT += gridStepMs
        }
        prevT = t; prevPts = pts.copyOf(); prevValid = valid
        return out
    }

    // 30fps 칸 1개를 받아 정규화해서 쌓고, 차례가 되면 모델 입력을 만든다
    private fun pushGridFrame(pts: FloatArray, valid: Boolean, out: MutableList<Output>) {
        val lsx = pts[L_SHOULDER * 2].toDouble(); val lsy = pts[L_SHOULDER * 2 + 1].toDouble()
        val rsx = pts[R_SHOULDER * 2].toDouble(); val rsy = pts[R_SHOULDER * 2 + 1].toDouble()
        val cx = (lsx + rsx) / 2.0
        val cy = (lsy + rsy) / 2.0
        val width = sqrt((lsx - rsx) * (lsx - rsx) + (lsy - rsy) * (lsy - rsy))
        val scale = stableScale(width)

        // 튐: 어깨 가운데가 한 프레임에 어깨너비 이상 움직임 (frame_jumps)
        val jump = if (prevCx.isNaN()) false else {
            val step = sqrt((cx - prevCx) * (cx - prevCx) + (cy - prevCy) * (cy - prevCy))
            step / scale > JUMP_LIMIT
        }
        prevCx = cx; prevCy = cy

        // ③ 어깨 기준 정규화
        val row = frames[head]
        for (c in 0 until channels) {
            val v = pts[landmarkIndex[c] * 2 + if (isX[c]) 0 else 1].toDouble()
            row[c] = ((v - if (isX[c]) cx else cy) / scale).toFloat()
        }
        frameValid[head] = valid
        frameJump[head] = jump
        head = (head + 1) % window
        if (filled < window) filled++
        sinceLast++

        // ④ 60프레임이 찼고, 첫 판정이거나 15프레임이 지났으면
        if (filled < window) return
        if (sinceLast < stride && sinceLast != window) return
        sinceLast = 0
        out.add(buildWindow())
    }

    private fun buildWindow(): Output {
        var nValid = 0
        var nJump = 0
        for (i in 0 until window) {
            if (frameValid[i]) nValid++
            if (frameJump[i]) nJump++
        }
        if (nValid.toDouble() / window < MIN_VALID_RATIO) {
            return Output.Skipped("사람이 잡힌 프레임 ${nValid}/${window}")
        }
        if (nJump > MAX_JUMPS_PER_WINDOW) {
            return Output.Skipped("좌표 튐 ${nJump}번")
        }

        // ⑤ 속도 + ⑥ 표준화. head 가 가장 오래된 프레임 자리
        val input = FloatArray(window * channels)
        for (k in 0 until window) {
            val cur = frames[(head + k) % window]
            val prev = if (k == 0) cur else frames[(head + k - 1) % window]
            for (c in 0 until channels) {
                var v = cur[c] - prev[c]          // 첫 프레임은 0
                if (clipAbs != null) v = v.coerceIn(-clipAbs[c], clipAbs[c])
                val s = if (abs(std[c]) < 1e-6f) 1f else std[c]
                input[k * channels + c] = (v - mean[c]) / s
            }
        }
        return Output.Ready(input)
    }

    // 어깨너비를 그대로 나누면 아이가 옆으로 돌 때 값이 폭발한다 (_stable_scale)
    private fun stableScale(width: Double): Double {
        if (width > 1e-6) longWidths.add(width)
        shortWidths.add(if (width > 1e-6) width else Double.NaN)

        val med = longWidths.median()
        if (med.isNaN() || med <= 1e-6) return 1.0
        var s = shortWidths.median()
        if (s.isNaN() || s <= 1e-6) s = med
        return s.coerceIn(SCALE_MIN_RATIO * med, SCALE_MAX_RATIO * med)
    }

    /** 고정 크기 기록. NaN 은 중앙값 계산에서 뺀다 */
    private class RingDouble(private val cap: Int) {
        private val buf = DoubleArray(cap)
        private var n = 0
        private var pos = 0
        fun add(v: Double) { buf[pos] = v; pos = (pos + 1) % cap; if (n < cap) n++ }
        fun clear() { n = 0; pos = 0 }
        fun median(): Double {
            val arr = DoubleArray(n)
            var m = 0
            for (i in 0 until n) { val v = buf[i]; if (!v.isNaN()) arr[m++] = v }
            if (m == 0) return Double.NaN
            arr.sort(0, m)
            return if (m % 2 == 1) arr[m / 2] else (arr[m / 2 - 1] + arr[m / 2]) / 2.0
        }
    }

    companion object {
        // build_dataset.py 와 같은 값
        const val SCALE_SMOOTH = 61
        const val SCALE_MIN_RATIO = 0.5
        const val SCALE_MAX_RATIO = 2.0
        const val JUMP_LIMIT = 1.0
        const val MAX_JUMPS_PER_WINDOW = 3
        const val MIN_VALID_RATIO = 0.8

        // 실시간용으로 정한 값
        const val LONG_HISTORY = 1800       // 어깨너비 기준값: 최근 1분 (30fps)
        const val MAX_GAP_MS = 1000.0       // 이보다 오래 끊기면 처음부터

        const val L_SHOULDER = 11
        const val R_SHOULDER = 12

        /** MediaPipe Pose 33관절 이름 → 번호 (CSV 컬럼 이름과 같은 표기) */
        val LANDMARK_NAMES = listOf(
            "nose", "left_eye_inner", "left_eye", "left_eye_outer",
            "right_eye_inner", "right_eye", "right_eye_outer",
            "left_ear", "right_ear", "mouth_left", "mouth_right",
            "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
            "left_wrist", "right_wrist", "left_pinky", "right_pinky",
            "left_index", "right_index", "left_thumb", "right_thumb",
            "left_hip", "right_hip", "left_knee", "right_knee",
            "left_ankle", "right_ankle", "left_heel", "right_heel",
            "left_foot_index", "right_foot_index",
        )

        /**
         * model_meta.json 의 coordinate_columns ("nose_x", "left_wrist_y" ...) 로 만든다.
         * 관절 이름을 못 찾으면 바로 에러 → 학습 설정과 앱이 어긋난 걸 일찍 알 수 있다.
         */
        fun fromColumns(
            columns: List<String>,
            mean: FloatArray,
            std: FloatArray,
            clipAbs: FloatArray? = null,
            window: Int = 60,
            stride: Int = 15,
        ): MlFeatureBuilder {
            val idx = IntArray(columns.size)
            val isX = BooleanArray(columns.size)
            columns.forEachIndexed { i, raw ->
                val col = raw.trim().lowercase()
                val axis = col.substringAfterLast('_')
                val joint = col.substringBeforeLast('_')
                require(axis == "x" || axis == "y") { "x/y 가 아닌 채널은 앱에서 지원 안 함: $raw" }
                val j = LANDMARK_NAMES.indexOf(joint)
                require(j >= 0) { "모르는 관절 이름: $raw" }
                idx[i] = j
                isX[i] = axis == "x"
            }
            return MlFeatureBuilder(idx, isX, mean, std, clipAbs, window, stride)
        }
    }
}

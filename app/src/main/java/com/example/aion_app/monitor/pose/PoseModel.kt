package com.example.aion_app.monitor.pose

/** MediaPipe Pose 33점 중 사용하는 인덱스 (HTML 원본 FULL_IDX 와 동일). */
object PoseIndex {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    /** 스켈레톤 그리기에 쓰는 전체 관절 인덱스 */
    val DRAW_POINTS = intArrayOf(
        NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
        LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE,
    )
}

/** 뼈대 연결 (HTML SKELETON_BONES 와 동일). 팔은 분석 대상이라 강조. */
val SKELETON_BONES: Array<Pair<Int, Int>> = arrayOf(
    PoseIndex.LEFT_SHOULDER to PoseIndex.RIGHT_SHOULDER,
    PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_ELBOW,
    PoseIndex.LEFT_ELBOW to PoseIndex.LEFT_WRIST,
    PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_ELBOW,
    PoseIndex.RIGHT_ELBOW to PoseIndex.RIGHT_WRIST,
    PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_HIP,
    PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_HIP,
    PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP,
    PoseIndex.LEFT_HIP to PoseIndex.LEFT_KNEE,
    PoseIndex.LEFT_KNEE to PoseIndex.LEFT_ANKLE,
    PoseIndex.RIGHT_HIP to PoseIndex.RIGHT_KNEE,
    PoseIndex.RIGHT_KNEE to PoseIndex.RIGHT_ANKLE,
)

/** 팔(분석 대상) 관절 인덱스 — 굵게 강조용 */
val ARM_POINTS = setOf(
    PoseIndex.LEFT_ELBOW, PoseIndex.RIGHT_ELBOW,
    PoseIndex.LEFT_WRIST, PoseIndex.RIGHT_WRIST,
)

/** 3D 타겟 박스 산출에 쓰는 인덱스 (가시성 좋은 점들) — HTML BOX_IDX 와 동일 */
val BOX_IDX = intArrayOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)

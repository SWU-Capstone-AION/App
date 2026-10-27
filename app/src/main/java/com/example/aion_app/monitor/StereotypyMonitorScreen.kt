package com.example.aion_app.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.key
import com.example.aion_app.monitor.audio.AlarmSound
import com.example.aion_app.monitor.camera.PoseHandCameraView
import com.example.aion_app.monitor.ml.StereotypyMlDetector
import com.example.aion_app.monitor.net.DetectionSender
import com.example.aion_app.monitor.pose.MinigameGate
import com.example.aion_app.monitor.pose.PoseHandLandmarkerHelper
import com.example.aion_app.monitor.rule.RuleV3ConfigLoader
import com.example.aion_app.monitor.rule.RuleV3Engine
import com.example.aion_app.monitor.rule.RuleV3Output
import com.example.aion_app.monitor.rule.RuleV3TouchBus
import com.example.aion_app.monitor.rule.RuleV3Recorder
import com.example.aion_app.monitor.ui.RecordUi
import java.io.File
import com.example.aion_app.monitor.rule.Sensitivity
import com.example.aion_app.monitor.rule.DEFAULT_SENSITIVITY
import com.example.aion_app.monitor.ui.AlarmBanner
import com.example.aion_app.monitor.ui.RuleV3Dashboard
import com.example.aion_app.monitor.ui.RuleV3Overlay
import com.example.aion_app.watch.WatchHeartRate
import com.example.aion_app.watch.WifiHrReceiver

// ============================================================
// 상동행동 모니터링(인식) 화면
// ============================================================
// 아동용 홈(KidsHomeScreen) 좌상단 '모니터링' 버튼으로 들어온다.
//
// 이 화면은 '인식 결과를 보여주는 화면'만 담당한다.
//   카메라 프리뷰 + 관절 33개·손 오버레이 + 규칙모델 v3 대시보드(HUD)
//   (1024_aion_monitor_v3_33pose_hands.html 대시보드의 앱 버전)
//
// 아동이 평소에 보는 화면(구체 / 진정 팝업 / 호흡)은 KidsHomeScreen 하나로 합쳤다.
// 예전에는 여기서도 ChildScreen 으로 같은 화면을 한 번 더 그려서 아동 홈이 두 개였는데,
// 그쪽은 삭제했다. 홈 뒤에서 도는 감지는 StereotypyDetectionHost 가 맡는다.
//
// ⚠ 카메라는 한 번에 한 곳만 쓸 수 있다.
//   홈 → 모니터링 이동 시 홈의 StereotypyDetectionHost 가 먼저 카메라를 놓아준다.
//   (AionNavHost 에서 enabled 를 현재 라우트로 묶어 순서를 보장한다)
@Composable
fun StereotypyMonitorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current

    // 이 화면에서도 워치 심박을 Wi-Fi로 받는다 (감지 호스트는 이 화면에서 꺼져 있음)
    // 화면이 꺼지면 카메라와 심박 수신이 함께 멈추므로 켜 둔다
    val view = LocalView.current
    DisposableEffect(Unit) {
        WifiHrReceiver.acquire(context)
        view.keepScreenOn = true
        onDispose {
            WifiHrReceiver.release()
            view.keepScreenOn = false
        }
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    var bundle by remember { mutableStateOf<PoseHandLandmarkerHelper.ResultBundle?>(null) }
    var fps by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    val fpsTimes = remember { ArrayDeque<Long>() }
    val engine = remember { RuleV3Engine().also { RuleV3ConfigLoader.applyFromAssets(context, it) } }
    var out by remember { mutableStateOf<RuleV3Output?>(null) }
    var running by remember { mutableStateOf(true) }
    var gameActive by remember { mutableStateOf(false) }
    var handsOn by remember { mutableStateOf(true) }
    val alarmSound = remember { AlarmSound() }

    // 판정 기록 (CSV). 라벨 버튼으로 '지금 상동행동 중'을 표시한다
    val recorder = remember { RuleV3Recorder() }
    var recording by remember { mutableStateOf(false) }
    var labelOn by remember { mutableStateOf(false) }
    var recordRows by remember { mutableIntStateOf(0) }
    var savedFile by remember { mutableStateOf<File?>(null) }

    // 학습모델(TFLite). 없으면 규칙모델 단독
    val mlDetector = remember { StereotypyMlDetector.createOrNull(context) }
    DisposableEffect(Unit) {
        RuleV3TouchBus.engine = engine
        onDispose {
            if (RuleV3TouchBus.engine === engine) RuleV3TouchBus.engine = null
            RuleV3TouchBus.clearExcluded()
            mlDetector?.close()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // 손 모델을 켜고 끌 때는 카메라·검출기를 새로 만든다
            key(handsOn) {
                PoseHandCameraView(
                    modifier = Modifier.fillMaxSize(),
                    withHands = handsOn,
                    onResult = { b ->
                        bundle = b
                        val now = SystemClock.uptimeMillis()
                        fpsTimes.addLast(now)
                        while (fpsTimes.isNotEmpty() && fpsTimes.first() < now - 1000) fpsTimes.removeFirst()
                        fps = fpsTimes.size

                        // 미니게임 중에는 판정을 멈춘다 (잡초 뽑기의 팔 상하 반복이 조건에 걸림)
                        if (!running || MinigameGate.active) return@PoseHandCameraView

                        val dl = mlDetector?.update(b.timestampMs, b.pose.landmarks().firstOrNull())?.score?.toDouble()
                        val o = engine.process(b.timestampMs, b.toFrameInput(), dl)
                        out = o
                        val tick = o.tick ?: return@PoseHandCameraView
                        if (o.ticked) {
                            recorder.add(o, fps)
                            if (recorder.recording) recordRows = recorder.rowCount
                            WatchHeartRate.onVision(tick.d)
                            DetectionSender.send(tick)
                            if (tick.alert.fire) alarmSound.beep()
                        }
                    },
                    onError = { status = it },
                )
            }

            RuleV3Overlay(
                frame = out?.frame,
                imageWidth = bundle?.inputImageWidth ?: 0,
                imageHeight = bundle?.inputImageHeight ?: 0,
                modifier = Modifier.fillMaxSize(),
            )

            // 대시보드 상단 '뒤로'로 아동 홈에 돌아간다.
            RuleV3Dashboard(
                out = out,
                fps = fps,
                inferenceMs = bundle?.inferenceTimeMs,
                running = running,
                gameActive = gameActive,
                handsOn = handsOn,
                onStart = {
                    engine.reset()
                    mlDetector?.reset()
                    out = null
                    running = true
                },
                onStop = { running = false },
                onToggleGame = {
                    gameActive = !gameActive
                    engine.setGameActive(gameActive)
                },
                onToggleHands = { handsOn = !handsOn },
                onCycleSensitivity = {
                    // 보통 → 민감 → 매우 민감 → 보통 … (이 화면에서만 바뀜. 아동 화면 뒤 감지는 기본값 '민감')
                    val all = Sensitivity.entries
                    val cur = engine.sensitivity
                    engine.applySensitivity(if (cur == null) DEFAULT_SENSITIVITY else all[(cur.ordinal + 1) % all.size])
                },
                record = RecordUi(
                    recording = recording,
                    label = labelOn,
                    rows = recordRows,
                    savedPath = savedFile?.let { "Android/data/${context.packageName}/files/records/${it.name}" },
                    onToggleRecord = {
                        if (recorder.recording) recorder.stop() else { recorder.start(); savedFile = null; recordRows = 0 }
                        recording = recorder.recording
                    },
                    onToggleLabel = {
                        labelOn = !labelOn
                        recorder.label = if (labelOn) 1 else 0
                    },
                    onSave = {
                        recorder.stop(); recording = false
                        savedFile = recorder.save(context)
                        status = savedFile?.let { "저장됨: ${it.name}" } ?: "저장할 기록이 없습니다"
                    },
                    onShare = { savedFile?.let { RuleV3Recorder.share(context, it) } },
                ),
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )

            AlarmBanner(
                show = running && out?.tick?.inEpisode == true,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )

            // 모델 로드 실패 등은 하단에 조용히 표시만 한다.
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    color = Color(0xFFFF5A3C),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                )
            }
        } else {
            // 권한이 없으면 인식 자체가 불가능하다. 홈으로 돌아갈 길만 남긴다.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("카메라 권한이 필요합니다", color = Color.White, fontSize = 16.sp)

                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF34C6FF))
                        .clickable { onBack() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "돌아가기",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
package com.example.aion_app.minigame

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aion_app.monitor.pose.PoseLandmarkerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 손 아이콘용 Paint. 프레임마다 새로 만들면 낭비라 한 번만 만들어 재사용한다.
 * 그리기는 UI 스레드에서만 일어나므로 공유해도 안전하다.
 */
private val handPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
}

private val PillBackground = Color(0xE6E0E0E0)
private val PillText = Color(0xFF1A1A1A)
private val TrackColor = Color(0x66FFFFFF)
private val FillColor = Color(0xFFB0B0B0)

/**
 * 포즈 기반 미니게임의 공통 껍데기.
 *
 * 카메라 권한 · CameraX 바인딩 · PoseLandmarker 연결 · 진행바 · 안내 문구 ·
 * 종료 버튼 · 완료 화면처럼 게임마다 똑같은 부분을 여기서 다 처리한다.
 * 게임은 [update] 와 [draw] 만 채우면 된다.
 *
 * 기존 monitor.pose.PoseLandmarkerHelper 를 그대로 재사용한다.
 * 헬퍼가 이미 전면 카메라 미러링을 처리하므로 좌표를 다시 뒤집지 않는다.
 *
 * @param introMessage 시작 전 안내창에 띄울 문구.
 * @param update 매 프레임 호출. **MediaPipe 결과 콜백(백그라운드 스레드)에서 돌아간다.**
 * @param draw 게임 그래픽. Canvas 안에서 호출된다.
 * @param onExit X 버튼을 눌렀을 때 (홈으로 복귀)
 * @param onForceClear 좌상단 구석 길게 누르기 — 인식이 안 될 때 쓰는 탈출구
 * @param onGameStateChanged 게임 진입/종료 알림. 상동행동 판정을 일시정지시키는 데 쓴다.
 *                           놀이 동작이 팔의 반복 운동이라 감지기에 그대로 걸리기 때문.
 * @param showDebug 손목 위치를 원으로 표시. 배포 시 false.
 */
@Composable
fun <S : MinigameStatus> PoseGameHost(
    title: String,
    // 시작 전 안내창 문구. 두 줄 기준으로 쓴다.
    //   1줄 = 무엇을 하는지, 2줄 = 부담 덜어주는 말
    introMessage: String,
    clearedMessage: String,
    onExit: () -> Unit,
    onForceClear: () -> Unit,
    onGameStateChanged: (Boolean) -> Unit,
    update: (PoseInput, Long) -> S,
    draw: (DrawScope, S) -> Unit,
    showDebug: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 게임 중에는 상동행동 판정을 멈춘다.
    DisposableEffect(Unit) {
        onGameStateChanged(true)
        onDispose { onGameStateChanged(false) }
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Text("카메라 권한이 필요해요", color = Color.White, fontSize = 18.sp)
        }
        return
    }

    var snapshot by remember { mutableStateOf<S?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 안내창을 닫기 전에는 게임 판정을 멈춘다.
    //
    // 카메라 바인딩과 MediaPipe 모델 로딩은 안내창 뒤에서 그대로 진행시킨다.
    // 아이가 문구를 읽는 동안 준비가 끝나므로 '시작할게요'를 누르면 바로 반응한다.
    //
    // ⚠ started 는 MediaPipe 결과 콜백(백그라운드 스레드)에서 읽는다.
    //   Compose State 를 그대로 읽으면 스레드 안전하지 않아 AtomicBoolean 을 따로 둔다.
    var introVisible by remember { mutableStateOf(true) }
    val started = remember { AtomicBoolean(false) }

    // 오버레이 크기는 추론 콜백(백그라운드 스레드)에서 읽으므로 원자적으로 보관한다.
    val overlaySize = remember { AtomicReference(IntSize.Zero) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val helper = remember {
        PoseLandmarkerHelper(
            context = context,
            onResult = { bundle ->
                val size = overlaySize.get()
                // 안내창이 떠 있는 동안에는 엔진을 건드리지 않는다.
                // 그냥 두면 아이가 문구를 읽는 사이에 잡초가 뽑혀 있다.
                if (started.get() && size.width > 0 && size.height > 0) {
                    val pose = PoseAdapter.toGameInput(
                        bundle = bundle,
                        viewWidth = size.width.toFloat(),
                        viewHeight = size.height.toFloat(),
                    )
                    val next = update(pose, System.currentTimeMillis())
                    mainHandler.post { snapshot = next }
                }
            },
            onError = { message ->
                mainHandler.post { errorMessage = message }
            },
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(Unit) {
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(analysisExecutor) { proxy ->
                    helper.detect(proxy, isFrontCamera = true)
                }
            }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            analysis,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            helper.close()
            analysisExecutor.shutdown()
        }
    }

    val state = snapshot

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize.set(it) }
        ) {
            if (state != null) {
                draw(this, state)
                drawHands(state)
            }
        }

        Pill(
            text = title,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            bold = true,
        )

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(48.dp)
                .background(Color.Black, CircleShape)
                .clickable { onExit() },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = Color.White, fontSize = 22.sp)
        }

        // 비상구: 좌상단 구석을 길게 누르면 즉시 클리어. 아이가 막혔을 때 교사가 쓴다.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(72.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onForceClear() })
                }
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                // 안내창이 떠 있는 동안에는 하단 안내 문구를 겹쳐 띄우지 않는다.
                introVisible -> Unit
                state == null -> Unit
                !state.poseVisible -> Pill("화면에 몸이 보이도록 앉아 줘")
                state.nudge == Nudge.KEEP_GOING -> Pill("아직 남았어! 조금만 더 해볼까?")
                state.nudge == Nudge.HOW_TO_QUIT -> Pill("그만두고 싶으면 X 버튼을 눌러 줘")
            }

            ProgressBar(
                progress = state?.progress ?: 0f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            )
        }

        errorMessage?.let { message ->
            if (showDebug) {
                Text(
                    text = message,
                    color = Color(0xFFFFAB91),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        }

        if (state?.cleared == true) {
            ClearedOverlay(message = clearedMessage, onExit = onExit)
        }

        // 시작 안내. 매번 띄운다. (아이가 매번 새로 접한다고 보는 편이 안전하다)
        if (introVisible) {
            IntroOverlay(
                title = title,
                message = introMessage,
                onStart = {
                    started.set(true)
                    introVisible = false
                },
            )
        }
    }
}

/**
 * 게임 시작 전 안내창.
 *
 * 버튼 말고 아무 데나 눌러도 시작된다. 아이가 버튼을 정확히 못 눌러
 * 화면 앞에서 멈춰 있는 상황을 막기 위해서다.
 * X 버튼은 이 오버레이에 가려지므로, 그만두려면 시작한 뒤에 누르면 된다.
 */
@Composable
private fun BoxScope.IntroOverlay(
    title: String,
    message: String,
    onStart: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onStart() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = message,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp).padding(horizontal = 32.dp),
            )

            Box(
                Modifier
                    .padding(top = 4.dp)
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .clickable { onStart() }
                    .padding(horizontal = 40.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "시작할게요",
                    color = PillText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * 손목 위치에 손바닥 아이콘을 그린다.
 * 아이가 "내 손이 저기구나"를 한눈에 알아야 해서 크게 그리고, 배경이 밝든 어둡든
 * 보이도록 그림자를 깐다. 크기는 어깨폭 기준이라 아이가 가까이 오면 함께 커진다.
 */
private fun DrawScope.drawHands(state: MinigameStatus) {
    val handSize = state.handSize
    if (handSize <= 1f) return

    handPaint.textSize = handSize
    handPaint.setShadowLayer(handSize * 0.12f, 0f, handSize * 0.03f, android.graphics.Color.argb(140, 0, 0, 0))
    val metrics = handPaint.fontMetrics
    val baselineOffset = -(metrics.ascent + metrics.descent) / 2f

    drawIntoCanvas { canvas ->
        state.wrists.forEach { wrist ->
            canvas.nativeCanvas.drawText(HAND_ICON, wrist.x, wrist.y + baselineOffset, handPaint)
        }
    }
}

private const val HAND_ICON = "✋"

@Composable
private fun BoxScope.ClearedOverlay(message: String, onExit: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 28.sp)
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .clickable { onExit() }
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Text("종료", color = PillText, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Box(
        modifier
            .background(PillBackground, RoundedCornerShape(28.dp))
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            color = PillText,
            fontSize = 18.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(20.dp).background(TrackColor, RoundedCornerShape(10.dp))) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(20.dp)
                .background(FillColor, RoundedCornerShape(10.dp))
        )
    }
}
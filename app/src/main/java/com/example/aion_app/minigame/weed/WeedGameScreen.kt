package com.example.aion_app.minigame.weed

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.util.concurrent.atomic.AtomicReference

private val PillBackground = Color(0xE6E0E0E0)
private val PillText = Color(0xFF1A1A1A)
private val WeedGreen = Color(0xFF7FC97F)
private val WeedGrabbed = Color(0xFFB8E986)
private val TrackColor = Color(0x66FFFFFF)
private val FillColor = Color(0xFFB0B0B0)

/**
 * 잡초 뽑기 미니게임 화면.
 *
 * 기존 monitor.pose.PoseLandmarkerHelper를 그대로 재사용한다.
 * 헬퍼가 이미 전면 카메라 미러링을 처리하므로 좌표를 다시 뒤집지 않는다.
 *
 * @param onExit X 버튼을 눌렀을 때 (홈으로 복귀)
 * @param onGameStateChanged 게임 진입/종료 알림. 상동행동 판정을 일시정지시키는 데 쓴다.
 *                           뽑는 동작이 팔 상하 반복이라 감지기에 그대로 걸리기 때문.
 * @param showDebug 손목 위치를 원으로 표시. 배포 시 false.
 */
@Composable
fun WeedGameScreen(
    onExit: () -> Unit,
    onGameStateChanged: (Boolean) -> Unit = {},
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

    val engine = remember { WeedGameEngine() }
    var snapshot by remember { mutableStateOf(GameSnapshot()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 오버레이 크기는 추론 콜백(백그라운드 스레드)에서 읽으므로 원자적으로 보관한다.
    val overlaySize = remember { AtomicReference(IntSize.Zero) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val helper = remember {
        PoseLandmarkerHelper(
            context = context,
            onResult = { bundle ->
                val size = overlaySize.get()
                if (size.width > 0 && size.height > 0) {
                    val pose = PoseAdapter.toGameInput(
                        bundle = bundle,
                        viewWidth = size.width.toFloat(),
                        viewHeight = size.height.toFloat(),
                    )
                    val next = engine.update(pose, System.currentTimeMillis())
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize.set(it) }
        ) {
            snapshot.weeds.forEach { weed ->
                if (weed.state != WeedState.PULLED) drawWeed(weed)
            }
            if (showDebug) {
                snapshot.wrists.forEach { wrist ->
                    drawCircle(
                        color = Color(0x99FF7043),
                        radius = 22f,
                        center = Offset(wrist.x, wrist.y),
                        style = Stroke(width = 4f),
                    )
                }
            }
        }

        Pill(
            text = "잡초를 전부 뽑아보자!",
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

        // 시연용 비상구: 좌상단 구석을 길게 누르면 즉시 클리어.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(72.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { engine.forceClear() })
                }
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !snapshot.poseVisible -> Pill("화면에 몸이 보이도록 앉아 줘")
                snapshot.nudge == Nudge.KEEP_GOING -> Pill("아직 잡초가 남아 있어! 더 뽑아 볼까?")
                snapshot.nudge == Nudge.HOW_TO_QUIT -> Pill("그만두고 싶으면 X 버튼을 눌러 줘")
            }

            ProgressBar(
                progress = snapshot.progress,
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

        if (snapshot.cleared) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("다 뽑았어! 잘했어", color = Color.White, fontSize = 28.sp)
                    Box(
                        Modifier
                            .padding(top = 24.dp)
                            .background(Color.White, RoundedCornerShape(24.dp))
                            .clickable { engine.reset() }
                            .padding(horizontal = 32.dp, vertical = 14.dp)
                    ) {
                        Text("다시 하기", color = PillText, fontSize = 18.sp)
                    }
                }
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

/** 잡초 한 포기를 잎 세 장으로 그린다. */
private fun DrawScope.drawWeed(weed: WeedView) {
    val height = weed.height
    if (height <= 1f) return

    val color = if (weed.state == WeedState.GRABBED) WeedGrabbed else WeedGreen
    val baseX = weed.pos.x
    val baseY = weed.pos.y
    val blades = listOf(-0.45f to -0.82f, 0f to -1f, 0.45f to -0.82f)

    blades.forEach { (dx, dy) ->
        val path = Path().apply {
            moveTo(baseX, baseY)
            quadraticBezierTo(
                baseX + dx * height * 0.15f,
                baseY + dy * height * 0.5f,
                baseX + dx * height,
                baseY + dy * height,
            )
        }
        drawPath(path, color, style = Stroke(width = height * 0.13f, cap = StrokeCap.Round))
    }
}

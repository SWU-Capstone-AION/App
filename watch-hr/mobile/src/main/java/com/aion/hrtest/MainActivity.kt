package com.aion.hrtest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    // 알림 권한은 거부해도 서비스는 돈다 (알림만 안 보임). 결과와 상관없이 수신 서비스를 띄운다
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ReceiverService.start(this) }

    private fun startReceiving() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ReceiverService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = HrRepository.get(this)
        // 앱을 열면 수업 세션이 시작된 것으로 보고 수신 서비스를 띄운다 (AION 앱에서는 수업 시작 시점에)
        startReceiving()
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

        setContent {
            MaterialTheme {
                val received by repo.latest.collectAsState()
                val bufferSize by repo.bufferSize.collectAsState()
                val dbCount by repo.dbCount.collectAsState(initial = 0)
                val recent by remember { repo.recent(10) }.collectAsState(initial = emptyList())
                val hr by repo.hrState.collectAsState()
                val rejected by repo.rejected.collectAsState()
                val pending by repo.pending.collectAsState()
                val behavior by repo.behaviorActive.collectAsState()
                val receiving by ReceiverService.running.collectAsState()
                var verdict by remember { mutableStateOf<Verdict?>(null) }
                val uploader = remember { PcUploader.get(applicationContext) }
                var pcAddress by remember { mutableStateOf(uploader.address) }
                val uploadStatus by uploader.status.collectAsState()
                // 값이 끊겨도 "연결 끊김" 판단이 갱신되도록 1초마다 시계를 돌린다
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                // 버퍼가 바뀔 때마다 다시 계산
                val medianGap = remember(bufferSize, received) { repo.medianGapMs() }
                val b = hr.baseline
                val risk = hr.risk(now)

                Row(Modifier.fillMaxSize().padding(24.dp)) {
                    // 왼쪽: 실시간 값 + 기준선
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(received?.bpm?.toString() ?: "--", fontSize = 72.sp)
                        Text("bpm", fontSize = 18.sp)
                        Text(
                            text = received?.let { "지연 ${it.delayMs}ms" }
                                ?: "워치에서 오는 값을 기다리는 중",
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (receiving) "● 수신 서비스 실행 중" else "○ 수신 서비스 꺼짐 (백그라운드에서 끊길 수 있음)",
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            if (receiving) {
                                OutlinedButton(onClick = { ReceiverService.stop(this@MainActivity) }) { Text("수신 중지") }
                            } else {
                                Button(onClick = { startReceiving() }) { Text("수신 시작") }
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        // 4단계: 기준선
                        Text("기준선 M %.1f · S %.1f".format(b.m, b.s), fontSize = 20.sp)
                        Text(
                            "샘플 ${b.count}개 · %.1f분".format(b.spanMs / 60_000.0) +
                                (if (b.ready) "" else " (수집 중 · ${MIN_SAMPLES}개·${MIN_SPAN_MS / 60_000}분 필요)") +
                                " · 보류 중 $pending",
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                risk != null -> "심박 위험도 %.2f".format(risk)
                                !b.ready -> "심박 위험도 - (기준선 수집 중)"
                                else -> "심박 위험도 - (워치 연결 끊김)"
                            },
                            fontSize = 22.sp
                        )
                        LinearProgressIndicator(
                            progress = { (risk ?: 0.0).toFloat() },
                            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 6.dp)
                        )
                        Text("필터 제외 ${rejected}건 (40~180 밖 · 급변)", fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))

                        // 비전 AI 모의 입력
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("상동행동 감지 중 (모의)", fontSize = 15.sp)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = behavior, onCheckedChange = { repo.behaviorActive.value = it })
                        }
                        Text(
                            if (behavior) "→ 지금 들어오는 값은 기준선에 넣지 않음" else "→ 조용한 구간: 기준선에 반영",
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { verdict = repo.onVisionAlert() }) {
                            Text("비전 AI 1차 신호 (모의)")
                        }
                        Text(verdict?.let { "판정: ${it.label}" } ?: "판정: -", fontSize = 18.sp)
                        Spacer(Modifier.height(16.dp))

                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("버퍼 $bufferSize / $BUFFER_SIZE · DB ${dbCount}건", fontSize = 14.sp)
                        Text(
                            "도착 간격 중앙값 " + (medianGap?.let { "${it}ms" } ?: "-"),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 워치 없이 저장 경로만 확인하는 용도
                            Button(onClick = {
                                val t = System.currentTimeMillis()
                                repo.onReceived(
                                    HrReceived(bpm = Random.nextInt(75, 95), at = t, receivedAt = t),
                                    sourceNode = "fake"
                                )
                            }) { Text("가짜 값") }
                            OutlinedButton(onClick = { repo.resetBaseline(); verdict = null }) { Text("기준선 재수집") }
                            OutlinedButton(onClick = { repo.clearAll(); verdict = null }) { Text("전부 비우기") }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        // PC 자동 저장: PC에서 tools/hr_server.py 를 켜고, 화면에 나온 주소를 넣는다
                        Text("PC 자동 저장", fontSize = 15.sp)
                        // 폰처럼 좁은 화면에서도 버튼이 밀려나지 않게 세로로 둔다
                        OutlinedTextField(
                            value = pcAddress,
                            onValueChange = { pcAddress = it.trim() },
                            label = { Text("PC 주소 (예: 172.19.69.233:8765)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { uploader.setAddress(pcAddress) }) { Text("연결") }
                        Text(uploadStatus, fontSize = 12.sp)
                    }

                    // 오른쪽: DB 최근 기록
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp)) {
                        Text("DB 최근 10건", fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn {
                            items(recent, key = { it.id }) { r ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(timeFmt.format(Date(r.receivedAt)), Modifier.weight(1.2f), fontSize = 12.sp)
                                    Text("${r.bpm}", Modifier.weight(0.6f), fontSize = 12.sp)
                                    Text(
                                        when {
                                            !r.valid -> "제외"
                                            !r.quiet -> "행동중"
                                            else -> "기준선"
                                        },
                                        Modifier.weight(0.8f), fontSize = 12.sp
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

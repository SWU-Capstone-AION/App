package com.example.aion_app

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.example.aion_app.navigation.AionNavHost
import com.example.aion_app.ui.theme.AionTheme

// ============================================
// 기기 판별 기준
// ============================================
// 안드로이드 표준인 sw600dp 를 그대로 쓴다.
// smallestScreenWidthDp 는 '화면의 짧은 변' 이라 기기를 어떻게 돌려도 값이 바뀌지 않는다.
// → 회전 고정을 걸어둔 뒤에 읽어도 결과가 흔들리지 않는다.
const val TabletSmallestWidthDp = 600

fun isTabletDevice(context: Context): Boolean =
    context.resources.configuration.smallestScreenWidthDp >= TabletSmallestWidthDp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("AION", "swDp=${resources.configuration.smallestScreenWidthDp} / isTablet=${isTabletDevice(this)}")

        requestedOrientation = if (isTabletDevice(this)) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContent {
            AionTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    AionNavHost()
                }
            }
        }
    }
}
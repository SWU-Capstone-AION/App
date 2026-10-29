package com.example.aion_app.ui.screen.kids.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.aion_app.ui.theme.AionTextDark
import com.example.aion_app.ui.theme.AionTextValue
import com.example.aion_app.ui.theme.Dark
import com.example.aion_app.ui.theme.DarkActive
import com.example.aion_app.ui.theme.Green
import com.example.aion_app.ui.theme.GreyBlack
import com.example.aion_app.ui.theme.LightActive
import com.example.aion_app.ui.theme.LightHover
import com.example.aion_app.ui.theme.Normal
import com.example.aion_app.ui.theme.White

// ============================================================
// AR 게임 튜토리얼 그림
// ============================================================
// 홈 튜토리얼(5장)과 게임 시작 전 안내(게임마다 3단계)에 쓰는 그림이다.
// 이미지 파일 대신 코드로 그린다. 태블릿에서 크게 늘려도 선이 뭉개지지 않고,
// 색을 앱 팔레트(Color.kt)에서 그대로 가져와 톤이 어긋나지 않는다.
//
// 좌표는 시안 SVG 와 같다.
//   홈 튜토리얼 장면  300 x 150
//   게임 단계 장면    140 x 120
// 부품(아이·손·주먹·잡초·칠판…)은 각자 자기 좌표계로 그리고, [part] 가
// 시안의 <use x y width height> 처럼 원하는 자리·크기로 옮겨 준다.

// 앱 팔레트에 없는 그림 전용 색
private val Skin = Color(0xFFF6D7C3)
private val SkinShade = Color(0xFFF2C9B0)
private val SkinLine = Color(0xFFE0B49A)
private val FistThumb = Color(0xFFEFC6AE)
private val Blush = Color(0xFFF4B9A8)
private val LeafLight = Color(0xFF7DB894)
private val Stem = Color(0xFF4F8A68)
private val Soil = Color(0xFFD9C2A3)
private val SoilBand = Color(0xFFE5D3B8)
private val SoilHole = Color(0xFFC9AE8A)
private val Root = Color(0xFFB08B62)
private val Wood = Color(0xFFC9A27E)
private val BoardGreen = Color(0xFF3F5F57)
private val SoftBlue = Color(0xFF85A9EF)
private val Sun = Color(0xFFF2D48A)
private val SunRay = Color(0xFFE5BE5C)
private val HeartBack = Color(0xFFF7DAD5)
private val Heart = Color(0xFFE8998A)

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val KidBody by lazy { svgPath("M22,120 C24,92 38,80 60,80 C82,80 96,92 98,120 Z") }
private val KidHair by lazy {
    svgPath("M36,44 C36,26 48,18 60,18 C74,18 85,27 84,44 C78,34 70,30 58,31 C50,32 42,36 36,44 Z")
}
private val KidSmile by lazy { svgPath("M53,58 Q60,64 67,58") }
private val KidHappyEyes by lazy { svgPath("M48,50 Q51,47 54,50 M66,50 Q69,47 72,50") }
private val KidBigSmile by lazy { svgPath("M52,58 Q60,66 68,58") }
private val PalmCrease by lazy { svgPath("M22,48 Q30,52 38,47") }
private val FistLines by lazy { svgPath("M21.5,28 L21.5,38 M30.5,26 L30.5,38 M39.5,28 L39.5,38") }
private val WeedStem by lazy { svgPath("M30,62 C30,50 30,40 31,28") }
private val WeedLeafLeft by lazy { svgPath("M30,46 C18,44 12,34 12,26 C22,28 29,36 30,46 Z") }
private val WeedLeafRight by lazy { svgPath("M31,40 C42,37 48,28 48,18 C38,21 32,30 31,40 Z") }
private val WeedLeafTop by lazy { svgPath("M31,30 C27,22 28,12 32,6 C36,13 35,22 31,30 Z") }
private val WeedRoots by lazy { svgPath("M30,62 L23,70 M30,62 L31,73 M30,62 L37,69") }
private val ChalkScribble by lazy { svgPath("M24,54 Q34,44 44,54 Q54,64 64,54") }
private val Spark by lazy { svgPath("M0,-8 L2,-2 L8,0 L2,2 L0,8 L-2,2 L-8,0 L-2,-2 Z") }
private val Star by lazy {
    svgPath("M70,44 L76.5,57.2 L91,59.3 L80.5,69.6 L83,84 L70,77.2 L57,84 L59.5,69.6 L49,59.3 L63.5,57.2 Z")
}
private val HeartOuter by lazy {
    svgPath("M62,52 C56,44 42,48 44,58 C45,66 62,76 62,76 C62,76 79,66 80,58 C82,48 68,44 62,52 Z")
}
private val HeartInner by lazy {
    svgPath("M62,58 C59,54 52,56 53,61 C54,65 62,70 62,70 C62,70 70,65 71,61 C72,56 65,54 62,58 Z")
}

// ------------------------------------------------------------
// 그리기 도우미
// ------------------------------------------------------------

/** 시안 좌표계(viewW x viewH)로 그린다. 폭에 맞춰 비율 그대로 늘린다. */
@Composable
private fun ArtCanvas(
    viewW: Float,
    viewH: Float,
    modifier: Modifier,
    draw: DrawScope.(TextMeasurer) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(viewW / viewH)) {
        val s = size.width / viewW
        withTransform({ scale(s, s, pivot = Offset.Zero) }) { draw(measurer) }
    }
}

/**
 * 부품 하나를 (x, y) 에 w x h 크기로 놓는다. 시안의 <use> 와 같다.
 * 비율이 다르면 작은 쪽에 맞추고 가운데에 둔다 (SVG 기본값 xMidYMid meet).
 */
private inline fun DrawScope.part(
    x: Float, y: Float, w: Float, h: Float,
    viewW: Float, viewH: Float,
    viewX: Float = 0f, viewY: Float = 0f,
    block: DrawScope.() -> Unit,
) {
    val s = minOf(w / viewW, h / viewH)
    val dx = x + (w - viewW * s) / 2f
    val dy = y + (h - viewH * s) / 2f
    withTransform({
        translate(dx, dy)
        scale(s, s, pivot = Offset.Zero)
        translate(-viewX, -viewY)
    }) { block() }
}

private fun DrawScope.box(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 0f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = alpha)

private fun DrawScope.dot(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(color, r, Offset(cx, cy))

private fun DrawScope.line(
    d: String, color: Color, width: Float, dash: FloatArray? = null, alpha: Float = 1f,
) = drawPath(
    svgPath(d), color, alpha = alpha,
    style = Stroke(
        width = width,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
    ),
)

private fun DrawScope.line(path: Path, color: Color, width: Float, alpha: Float = 1f) =
    drawPath(path, color, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

/** 그림 속 글자. (cx, cy) 를 가운데로 둔다. [end] 면 cx 가 오른쪽 끝이 된다. */
private fun DrawScope.label(
    measurer: TextMeasurer, text: String, cx: Float, cy: Float, fontPx: Float, color: Color,
    end: Boolean = false,
) {
    // 그림 좌표 단위 그대로의 글자 크기. 캔버스 배율이 그림과 같이 키운다.
    val layout = measurer.measure(
        text,
        TextStyle(fontSize = fontPx.toSp(), fontWeight = FontWeight.Bold, color = color),
    )
    val left = if (end) cx - layout.size.width else cx - layout.size.width / 2f
    drawText(layout, topLeft = Offset(left, cy - layout.size.height / 2f))
}

// ------------------------------------------------------------
// 부품
// ------------------------------------------------------------

/** 앉아 있는 아이 (120 x 120). [happy] 면 눈을 감고 크게 웃는다. */
private fun DrawScope.kidFace(happy: Boolean) {
    drawPath(KidBody, Normal)
    box(SkinShade, 52f, 64f, 16f, 18f, 6f)
    dot(Skin, 60f, 46f, 24f)
    drawPath(KidHair, DarkActive)
    if (happy) {
        line(KidHappyEyes, AionTextDark, 2.4f)
        line(KidBigSmile, AionTextDark, 2.4f)
    } else {
        dot(AionTextDark, 51f, 50f, 2.6f)
        dot(AionTextDark, 69f, 50f, 2.6f)
        line(KidSmile, AionTextDark, 2.4f)
    }
    dot(Blush, 45f, 57f, 3.5f)
    dot(Blush, 75f, 57f, 3.5f)
}

private fun DrawScope.kid(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 120f, 120f) { kidFace(happy = false) }

/** 두 팔을 쭉 뻗은 아이 (120 x 130) */
private fun DrawScope.kidArmsUp(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 120f, 130f) {
        line("M34,104 L20,50", Normal, 13f)
        line("M86,104 L100,50", Normal, 13f)
        dot(Skin, 19f, 44f, 8f)
        dot(Skin, 101f, 44f, 8f)
        withTransform({ translate(0f, 10f) }) { kidFace(happy = true) }
    }

/** 편 손 (60 x 74) */
private fun DrawScope.palm(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 60f, 74f) {
        box(Skin, 13f, 12f, 8f, 30f, 4f)
        box(Skin, 22.5f, 5f, 8f, 34f, 4f)
        box(Skin, 32f, 7f, 8f, 32f, 4f)
        box(Skin, 41f, 15f, 7.5f, 26f, 3.75f)
        box(Skin, 12f, 30f, 37f, 32f, 14f)
        rotate(-32f, pivot = Offset(7f, 45f)) { box(Skin, 3f, 34f, 8f, 22f, 4f) }
        line(PalmCrease, SkinLine, 1.8f)
        box(Normal, 19f, 60f, 23f, 13f, 4f)
    }

/** 쥔 주먹 (60 x 74) */
private fun DrawScope.fist(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 60f, 74f) {
        box(Skin, 10f, 22f, 40f, 38f, 14f)
        dot(Skin, 17f, 26f, 6.5f)
        dot(Skin, 26f, 23f, 6.5f)
        dot(Skin, 35f, 23f, 6.5f)
        dot(Skin, 44f, 26f, 6.5f)
        line(FistLines, SkinLine, 2f)
        box(FistThumb, 11f, 40f, 28f, 11f, 5.5f)
        box(Normal, 19f, 58f, 23f, 14f, 4f)
    }

private fun DrawScope.weedLeaves() {
    line(WeedStem, Stem, 3f)
    drawPath(WeedLeafLeft, Green)
    drawPath(WeedLeafRight, LeafLight)
    drawPath(WeedLeafTop, Green)
}

/** 흙에 박힌 잡초 (60 x 70) */
private fun DrawScope.weed(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 60f, 70f) {
        oval(Soil, 30f, 62f, 24f, 6.5f)
        weedLeaves()
    }

/** 뿌리째 뽑힌 잡초 (60 x 76) */
private fun DrawScope.weedPulled(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 60f, 76f) {
        weedLeaves()
        line(WeedRoots, Root, 2.2f)
    }

/** 칠판 (160 x 104). [dirty] 면 분필 얼룩이 있다. */
private fun DrawScope.board(x: Float, y: Float, w: Float, h: Float, dirty: Boolean) =
    part(x, y, w, h, 160f, 104f) {
        box(Wood, 0f, 0f, 160f, 104f, 10f)
        box(BoardGreen, 7f, 7f, 146f, 90f, 6f)
        box(Root, 50f, 96f, 60f, 5f, 2.5f)
        if (dirty) {
            oval(White, 40f, 34f, 18f, 9f, alpha = 0.78f)
            oval(White, 98f, 28f, 22f, 8f, alpha = 0.78f)
            oval(White, 122f, 64f, 16f, 10f, alpha = 0.78f)
            oval(White, 58f, 70f, 24f, 9f, alpha = 0.78f)
            oval(White, 90f, 56f, 10f, 6f, alpha = 0.78f)
            line(ChalkScribble, White, 3f, alpha = 0.8f)
        }
    }

/** 반짝이. (x, y) 가 왼쪽 위, [size] 가 한 변 */
private fun DrawScope.spark(x: Float, y: Float, size: Float, color: Color) =
    part(x, y, size, size, 16f, 16f, viewX = -8f, viewY = -8f) { drawPath(Spark, color) }

/** 구슬 상자 (80 x 80) */
private fun DrawScope.gift(x: Float, y: Float, w: Float, h: Float) =
    part(x, y, w, h, 80f, 80f) {
        box(Normal, 12f, 34f, 56f, 38f, 6f)
        box(Dark, 8f, 24f, 64f, 14f, 5f)
        box(LightActive, 36f, 24f, 8f, 48f)
        rotate(-20f, pivot = Offset(31f, 19f)) { oval(LightActive, 31f, 19f, 10f, 6f) }
        rotate(20f, pivot = Offset(49f, 19f)) { oval(LightActive, 49f, 19f, 10f, 6f) }
        dot(LightHover, 40f, 23f, 4.5f)
    }

/** 게임 단계 그림 왼쪽 위의 번호 */
private fun DrawScope.stepBadge(measurer: TextMeasurer, number: Int) {
    dot(Normal, 20f, 20f, 12f)
    label(measurer, number.toString(), 20f, 20f, 13f, White)
}

// ------------------------------------------------------------
// 홈 튜토리얼 장면 (300 x 150)
// ------------------------------------------------------------

/** 1장. 태블릿 화면 속 아이와 주변에 생긴 잡초·칠판 */
@Composable
fun ArtWhatIsAr(modifier: Modifier = Modifier) = ArtCanvas(300f, 150f, modifier) { m ->
    box(DarkActive, 60f, 8f, 180f, 134f, 14f)
    box(LightHover, 68f, 16f, 164f, 118f, 8f)
    board(170f, 22f, 54f, 35f, dirty = true)
    kid(110f, 54f, 80f, 80f)
    weed(78f, 72f, 34f, 40f)
    weed(194f, 84f, 30f, 35f)
    palm(86f, 36f, 26f, 32f)
    box(Normal, 14f, 16f, 38f, 22f, 11f)
    label(m, "AR", 33f, 27f, 12f, White)
    spark(252f, 20f, 14f, SoftBlue)
    spark(266f, 44f, 9f, SoftBlue)
    spark(30f, 104f, 11f, SoftBlue)
}

/** 2장. 팔을 쭉 뻗은 아이, 편안한 마음(하트)과 집중(과녁) */
@Composable
fun ArtWhyPlay(modifier: Modifier = Modifier) = ArtCanvas(300f, 150f, modifier) {
    dot(LightHover, 150f, 84f, 62f)
    kidArmsUp(104f, 28f, 92f, 100f)
    line("M100,46 Q92,50 90,58 M200,46 Q208,50 210,58", SoftBlue, 3f)
    drawPath(HeartOuter, HeartBack)
    drawPath(HeartInner, Heart)
    dot(LightActive, 240f, 62f, 20f)
    dot(White, 240f, 62f, 12f)
    dot(Normal, 240f, 62f, 5f)
    box(LightHover, 0f, 134f, 300f, 16f)
}

/** 3장. 의자에 앉기 · 얼굴과 어깨가 보이게 · 밝은 곳에서 */
@Composable
fun ArtGetReady(modifier: Modifier = Modifier) = ArtCanvas(300f, 150f, modifier) { m ->
    dot(LightHover, 58f, 62f, 40f)
    box(Wood, 74f, 44f, 8f, 38f, 4f)
    kid(30f, 22f, 56f, 56f)
    box(Wood, 28f, 78f, 60f, 9f, 4f)
    box(Root, 34f, 86f, 6f, 16f, 3f)
    box(Root, 76f, 86f, 6f, 16f, 3f)

    dot(LightHover, 150f, 62f, 40f)
    kid(122f, 30f, 56f, 56f)
    line(
        "M120,38 L120,30 L130,30 M180,38 L180,30 L170,30 M120,86 L120,94 L130,94 M180,86 L180,94 L170,94",
        Normal, 3.5f,
    )

    dot(LightHover, 242f, 62f, 40f)
    dot(Sun, 242f, 62f, 15f)
    line(
        "M242,34 L242,28 M242,96 L242,90 M214,62 L208,62 M276,62 L270,62 " +
            "M222,42 L218,38 M262,82 L266,86 M262,42 L266,38 M222,82 L218,86",
        SunRay, 3.5f,
    )

    label(m, "의자에 앉기", 58f, 122f, 12f, AionTextDark)
    label(m, "얼굴·어깨 보이게", 150f, 122f, 12f, AionTextDark)
    label(m, "밝은 곳에서", 242f, 122f, 12f, AionTextDark)
}

/** 4장. 두 가지 놀이 카드 */
@Composable
fun ArtTwoGames(modifier: Modifier = Modifier) = ArtCanvas(300f, 150f, modifier) { m ->
    box(White, 22f, 14f, 122f, 122f, 14f)
    weed(44f, 26f, 56f, 64f)
    palm(90f, 22f, 34f, 42f)
    box(Normal, 32f, 98f, 102f, 28f, 8f)
    label(m, "잡초 뽑기", 83f, 112f, 13f, White)

    box(White, 156f, 14f, 122f, 122f, 14f)
    board(170f, 28f, 94f, 61f, dirty = true)
    line("M196,58 Q208,44 220,58 Q232,72 244,58", BoardGreen, 10f)
    palm(226f, 40f, 30f, 37f)
    box(Normal, 166f, 98f, 102f, 28f, 8f)
    label(m, "칠판 지우기", 217f, 112f, 13f, White)
}

/** 5장. 별 → 구슬 상자, 그만하고 싶으면 X */
@Composable
fun ArtReward(modifier: Modifier = Modifier) = ArtCanvas(300f, 150f, modifier) { m ->
    dot(LightActive, 70f, 66f, 34f)
    drawPath(Star, Normal)
    line("M116,66 L150,66 M142,56 L152,66 L142,76", SoftBlue, 4f)
    gift(164f, 26f, 80f, 80f)
    spark(236f, 22f, 14f, SoftBlue)
    spark(160f, 30f, 9f, SoftBlue)
    dot(GreyBlack, 272f, 122f, 13f)
    line("M267,117 L277,127 M277,117 L267,127", White, 2.4f)
    label(m, "그만하고 싶으면", 252f, 122f, 11f, AionTextValue, end = true)
}

// ------------------------------------------------------------
// 게임 단계 장면 (140 x 120)
// ------------------------------------------------------------

/** 잡초 1. 손바닥을 잡초에 가져간다 */
@Composable
fun ArtWeedReach(modifier: Modifier = Modifier) = ArtCanvas(140f, 120f, modifier) { m ->
    box(SoilBand, 10f, 92f, 120f, 20f, 10f)
    weed(34f, 42f, 52f, 60f)
    line("M96,58 Q86,66 76,66", Normal, 2.5f, dash = floatArrayOf(4f, 4f))
    palm(88f, 14f, 40f, 49f)
    stepBadge(m, 1)
}

/** 잡초 2. 손을 꼭 쥔다 */
@Composable
fun ArtWeedGrip(modifier: Modifier = Modifier) = ArtCanvas(140f, 120f, modifier) { m ->
    box(SoilBand, 10f, 92f, 120f, 20f, 10f)
    weed(34f, 42f, 52f, 60f)
    fist(40f, 34f, 40f, 49f)
    line("M34,46 L26,42 M34,58 L25,58 M86,46 L94,42 M86,58 L95,58", Normal, 2.5f)
    stepBadge(m, 2)
}

/** 잡초 3. 쥔 채로 위로 쑥 */
@Composable
fun ArtWeedPull(modifier: Modifier = Modifier) = ArtCanvas(140f, 120f, modifier) { m ->
    box(SoilBand, 10f, 92f, 120f, 20f, 10f)
    oval(SoilHole, 60f, 98f, 14f, 4f)
    weedPulled(38f, 12f, 44f, 56f)
    fist(40f, 10f, 40f, 49f)
    dot(Root, 50f, 84f, 2f)
    dot(Root, 68f, 80f, 1.6f)
    dot(Root, 58f, 88f, 1.4f)
    line("M108,84 L108,36 M97,47 L108,35 L119,47", Normal, 5f)
    spark(20f, 44f, 12f, SoftBlue)
    stepBadge(m, 3)
}

/** 칠판 1. 손을 칠판 쪽으로 든다 */
@Composable
fun ArtBoardRaise(modifier: Modifier = Modifier) = ArtCanvas(140f, 120f, modifier) { m ->
    board(15f, 10f, 110f, 72f, dirty = true)
    palm(54f, 72f, 32f, 40f)
    line("M98,104 L98,84 M90,91 L98,83 L106,91", Normal, 4f)
    stepBadge(m, 1)
}

/** 칠판 2. 손바닥으로 쓱쓱 문지른다 */
@Composable
fun ArtBoardRub(modifier: Modifier = Modifier) = ArtCanvas(140f, 120f, modifier) { m ->
    board(15f, 10f, 110f, 72f, dirty = true)
    line("M34,46 Q45,32 56,46 Q67,60 78,46 Q89,32 100,44", BoardGreen, 13f)
    palm(74f, 22f, 32f, 40f)
    line("M28,98 Q40,106 52,98 M88,98 Q100,106 112,98", Normal, 3f)
    line("M60,100 L80,100", Normal, 3f, dash = floatArrayOf(3f, 5f))
    stepBadge(m, 2)
}

/** 칠판 3. 얼룩이 사라지면 반짝 */
@Composable
fun ArtBoardClean(modifier: Modifier = Modifier) = ArtCanvas(140f, 120f, modifier) { m ->
    board(15f, 10f, 110f, 72f, dirty = false)
    spark(36f, 26f, 16f, White)
    spark(84f, 44f, 12f, White)
    spark(96f, 20f, 9f, White)
    spark(60f, 52f, 10f, Sun)
    dot(Green, 70f, 100f, 11f)
    line("M65,100 L69,104 L76,96", White, 2.6f)
    stepBadge(m, 3)
}

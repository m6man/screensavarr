package app.screensavarr

import android.service.dreams.DreamService
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun screensaverHost(service: DreamService) {
    val presenter = remember { DreamPresenter(service) }
    val content by presenter.content.collectAsState()
    DisposableEffect(presenter) {
        presenter.start()
        onDispose { presenter.stop() }
    }
    dreamView(content)
}

@Composable
private fun dreamView(content: DreamContent) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(1_000)) togetherWith fadeOut(tween(1_000)) },
            label = "screensaver content",
        ) { screen ->
            when (screen) {
                DreamContent.Brand -> centeredText("screensavarr", 42.sp)
                DreamContent.Unconfigured -> centeredText("Configure Sonarr or Radarr in screensavarr.", 26.sp)
                is DreamContent.Showcase -> showcase(screen)
            }
        }
        dreamClock(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(56.dp),
        )
    }
}

@Composable
private fun dreamClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            timestamp = System.currentTimeMillis()
        }
    }
    BasicText(
        text = android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp)),
        style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium),
        modifier = modifier,
    )
}

@Composable
private fun showcase(content: DreamContent.Showcase) {
    val zoom = rememberInfiniteTransition(label = "backdrop zoom")
    val scale by zoom.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 30_000,
                delayMillis = 1_000,
                easing = CubicBezierEasing(0f, 0f, 0.58f, 1f),
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "backdrop scale",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = content.backdrop.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
        )
        if (content.logo != null) {
            Image(
                bitmap = content.logo.asImageBitmap(),
                contentDescription = content.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(56.dp)
                    .sizeIn(maxWidth = 440.dp, maxHeight = 90.dp),
            )
        } else {
            dreamTitle(content.title)
        }
    }
}

@Composable
private fun BoxScope.dreamTitle(title: String) {
    BasicText(
        text = title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = Color.White, fontSize = 30.sp),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(0.65f)
            .padding(56.dp),
    )
}

@Composable
private fun centeredText(
    text: String,
    size: TextUnit,
    alignment: Alignment = Alignment.Center,
) {
    BasicText(
        text = text,
        style = TextStyle(color = Color.White, fontSize = size),
        modifier = Modifier.fillMaxSize().padding(56.dp).wrapContentSize(alignment),
    )
}
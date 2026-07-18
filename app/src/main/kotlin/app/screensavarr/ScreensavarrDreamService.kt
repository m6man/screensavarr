package app.screensavarr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class ScreensavarrDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this).apply {
        performAttach()
    }

    override val lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScreensavarrDreamService)
            setViewTreeSavedStateRegistryOwner(this@ScreensavarrDreamService)
            setViewTreeViewModelStoreOwner(this@ScreensavarrDreamService)
            setContent { screensaverHost(this@ScreensavarrDreamService) }
        })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}

private sealed interface DreamContent {
    data object Brand : DreamContent
    data object Unconfigured : DreamContent
    data class Showcase(val title: String, val backdrop: Bitmap, val logo: Bitmap?) : DreamContent
}

private class DreamPresenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store: ConfigurationStore
    private val client = ArrApiClient()
    private val _content = MutableStateFlow<DreamContent>(DreamContent.Brand)
    val content = _content.asStateFlow()

    constructor(service: DreamService) {
        store = ConfigurationStore(service.applicationContext)
    }

    fun start() = scope.launch {
        delay(2.seconds)
        while (isActive) {
            val config = store.load()
            val items = supervisorScope {
                listOf(
                    async { runCatching { client.loadSonarr(config.sonarr) }.getOrDefault(emptyList()) },
                    async { runCatching { client.loadRadarr(config.radarr) }.getOrDefault(emptyList()) },
                ).flatMap { it.await() }.shuffled(Random.Default)
            }
            if (items.isEmpty()) {
                _content.value = DreamContent.Unconfigured
                delay(60.seconds)
                continue
            }
            for (item in items) {
                if (!isActive) break
                val backdrop = loadImage(item.backdrop, config) ?: continue
                _content.value = DreamContent.Showcase(item.title, backdrop, item.logo?.let { loadImage(it, config) })
                delay(30.seconds)
            }
        }
    }

    fun stop() = scope.cancel()

    private suspend fun loadImage(artwork: ArtworkUrl, config: ScreensavarrConfig): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = when {
                    !artwork.requiresApiKey -> null
                    artwork.url.startsWith(config.sonarr.baseUrl) -> config.sonarr.apiKey
                    artwork.url.startsWith(config.radarr.baseUrl) -> config.radarr.apiKey
                    else -> null
                }
                val connection = (URL(artwork.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    if (apiKey != null) setRequestProperty("X-Api-Key", apiKey)
                }
                try {
                    if (connection.responseCode !in 200..299) null
                    else connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}

@Composable
private fun screensaverHost(service: DreamService) {
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
    size: androidx.compose.ui.unit.TextUnit,
    alignment: Alignment = Alignment.Center
) {
    BasicText(
        text = text,
        style = TextStyle(color = Color.White, fontSize = size),
        modifier = Modifier.fillMaxSize().padding(56.dp).wrapContentSize(alignment),
    )
}
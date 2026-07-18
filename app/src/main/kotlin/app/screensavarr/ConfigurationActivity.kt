package app.screensavarr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class ConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { configurationScreen(ConfigurationStore(applicationContext), ArrApiClient()) }
    }
}

@Composable
private fun configurationScreen(store: ConfigurationStore, client: ArrApiClient) {
    var savedConfig by remember { mutableStateOf(store.load()) }
    var sonarr by remember { mutableStateOf(savedConfig.sonarr) }
    var radarr by remember { mutableStateOf(savedConfig.radarr) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101815))
            .padding(horizontal = 44.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("screensavarr", style = TextStyle(color = Color(0xFFF1F5EF), fontSize = 36.sp))
        BasicText(
            "A screensaver for the media history in Sonarr and Radarr.",
            style = TextStyle(color = Color(0xFFB7C5BB), fontSize = 16.sp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            sourceEditor(
                name = "Sonarr",
                source = sonarr,
                onChange = { sonarr = it },
                modifier = Modifier.weight(1f),
            )
            sourceEditor(
                name = "Radarr",
                source = radarr,
                onChange = { radarr = it },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            actionButton("Save") {
                savedConfig = ScreensavarrConfig(sonarr, radarr)
                store.save(savedConfig)
                status = "Saved. Select screensavarr in your device's screensaver settings."
            }
            actionButton("Test Sonarr") {
                scope.launch {
                    status = "Testing Sonarr..."
                    status = connectionStatus("Sonarr", client.testConnection(sonarr))
                }
            }
            actionButton("Test Radarr") {
                scope.launch {
                    status = "Testing Radarr..."
                    status = connectionStatus("Radarr", client.testConnection(radarr))
                }
            }
        }
        Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.CenterStart) {
            status?.let { BasicText(it, style = TextStyle(color = Color(0xFF90D7B0), fontSize = 16.sp)) }
        }
    }
}

private fun connectionStatus(name: String, result: Result<Unit>): String = result.fold(
    onSuccess = { "$name connection succeeded." },
    onFailure = { "$name connection failed: ${it.message ?: "Unknown error"}" },
)

@Composable
private fun sourceEditor(
    name: String,
    source: ArrInstanceConfig,
    onChange: (ArrInstanceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, Color(0xFF365043), RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(name, style = TextStyle(color = Color(0xFFF1F5EF), fontSize = 22.sp))
        labeledField("Server URL", source.baseUrl, false) { onChange(source.copy(baseUrl = it)) }
        labeledField("API key", source.apiKey, true) { onChange(source.copy(apiKey = it)) }
    }
}

@Composable
private fun labeledField(label: String, value: String, password: Boolean, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color(0xFFB7C5BB), fontSize = 15.sp),
            modifier = Modifier.width(88.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(if (focused) Color(0xFF233A2D) else Color(0xFF17231D), RoundedCornerShape(4.dp))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Color(0xFFB7FFD1) else Color(0xFF587060),
                    RoundedCornerShape(4.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun actionButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicText(
        text = label,
        style = TextStyle(color = Color(0xFF101815), fontSize = 17.sp),
        modifier = Modifier
            .height(48.dp)
            .background(if (focused) Color(0xFFCAFFE0) else Color(0xFF90D7B0), RoundedCornerShape(4.dp))
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color(0xFF90D7B0),
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}
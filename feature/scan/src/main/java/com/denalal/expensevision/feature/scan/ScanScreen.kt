package com.denalal.expensevision.feature.scan

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.denalal.expensevision.core.ocr.OcrResult
import com.denalal.expensevision.core.ocr.PaddleOcrEngine
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    data class Done(val bitmap: Bitmap, val result: OcrResult) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ocrEngine = remember { PaddleOcrEngine(context) }
    DisposableEffect(Unit) {
        onDispose { ocrEngine.close() }
    }

    var state by remember { mutableStateOf<ScanUiState>(ScanUiState.Idle) }

    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val page = scanResult?.pages?.firstOrNull()
            val uri = page?.imageUri
            if (uri != null) {
                scope.launch {
                    state = ScanUiState.Scanning
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }
                        if (bitmap == null) {
                            state = ScanUiState.Error("Не удалось открыть изображение")
                        } else {
                            val ocr = withContext(Dispatchers.Default) {
                                ocrEngine.recognize(bitmap)
                            }
                            state = ScanUiState.Done(bitmap, ocr)
                        }
                    } catch (e: Exception) {
                        state = ScanUiState.Error(e.message ?: "Ошибка распознавания")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = {
                val activity = context as? Activity
                if (activity != null) {
                    scanner.getStartScanIntent(activity)
                        .addOnSuccessListener { intentSender ->
                            scanLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        }
                        .addOnFailureListener { e ->
                            scope.launch { state = ScanUiState.Error(e.message ?: "Ошибка сканера") }
                        }
                }
            },
        ) {
            Text("Сканировать чек")
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            ScanUiState.Idle -> Text("Нажмите кнопку, чтобы отсканировать чек")
            ScanUiState.Scanning -> CircularProgressIndicator()
            is ScanUiState.Error -> Text(
                text = "Ошибка: ${s.message}",
                color = MaterialTheme.colorScheme.error,
            )
            is ScanUiState.Done -> {
                Image(
                    bitmap = s.bitmap.asImageBitmap(),
                    contentDescription = "Отсканированный чек",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = s.result.text.ifBlank { "(текст не распознан)" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

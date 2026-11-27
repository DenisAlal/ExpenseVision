package com.denalal.expensevision.feature.scan

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.denalal.expensevision.core.model.CategoryType
import com.denalal.expensevision.core.model.ReceiptDraftModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScanScreen() {
    val vm: ScanViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
            if (uri != null) {
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.use { BitmapFactory.decodeStream(it) }
                    }
                    if (bitmap != null) vm.onImageScanned(bitmap)
                }
            }
        }
    }

    val launchScanner: () -> Unit = {
        val activity = context as? Activity
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            ScanUiState.Idle -> Centered {
                Text("Отсканируйте чек, чтобы добавить расход")
                Spacer(Modifier.height(16.dp))
                Button(onClick = launchScanner) { Text("Сканировать чек") }
            }

            ScanUiState.Scanning -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Распознаём текст…")
            }

            is ScanUiState.Editing -> EditingContent(
                draft = s.draft,
                onDraftChanged = vm::onDraftChanged,
                onSave = vm::onSave,
            )

            ScanUiState.Saving -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Сохраняем…")
            }

            ScanUiState.Saved -> Centered {
                Text("Чек сохранён")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.onReset() }) { Text("Сканировать ещё") }
            }

            is ScanUiState.Error -> Centered {
                Text("Ошибка: ${s.message}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.onReset() }) { Text("Повторить") }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun EditingContent(
    draft: ReceiptDraftModel,
    onDraftChanged: (ReceiptDraftModel) -> Unit,
    onSave: () -> Unit,
) {
    var storeText by remember { mutableStateOf(draft.storeName ?: "") }
    var sumText by remember { mutableStateOf(formatRub(draft.sum)) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val selectedCategory = draft.category

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Подтвердите данные", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Text("Дата: ${draft.date ?: "не распознана"}")
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = storeText,
            onValueChange = {
                storeText = it
                onDraftChanged(draft.copy(storeName = it.ifBlank { null }))
            },
            label = { Text("Магазин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = sumText,
            onValueChange = {
                sumText = it
                onDraftChanged(draft.copy(sum = parseRub(it)))
            },
            label = { Text("Сумма, ₽") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Box {
            OutlinedButton(
                onClick = { categoryExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Категория: ${selectedCategory.labelRu()}")
            }
            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false },
            ) {
                CategoryType.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.labelRu()) },
                        onClick = {
                            onDraftChanged(draft.copy(category = category))
                            categoryExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { showRaw = !showRaw }) {
            Text(if (showRaw) "Скрыть распознанный текст" else "Показать распознанный текст")
        }
        if (showRaw) {
            Text(draft.rawText, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Сохранить")
        }
    }
}

private fun CategoryType.labelRu(): String = when (this) {
    CategoryType.GROCERY -> "Продукты"
    CategoryType.PHARMACY -> "Аптека"
    CategoryType.CLOTHING -> "Одежда и обувь"
    CategoryType.BEAUTY -> "Красота и косметика"
    CategoryType.ELECTRONICS -> "Электроника"
    CategoryType.HOME -> "Дом и ремонт"
    CategoryType.BOOKS -> "Книги и канцелярия"
    CategoryType.ENTERTAINMENT -> "Развлечения и подарки"
    CategoryType.SPORTS -> "Спорт и туризм"
    CategoryType.PETS -> "Зоотовары"
    CategoryType.JEWELRY -> "Украшения и часы"
    CategoryType.FLOWERS -> "Цветы"
    CategoryType.AUTOMOTIVE -> "Автотовары"
    CategoryType.BABY -> "Детские товары"
    CategoryType.ALCOHOL -> "Алкоголь"
    CategoryType.OTHER -> "Прочее"
}

private fun formatRub(minor: Long?): String {
    if (minor == null) return ""
    val rub = minor / 100
    val kop = minor % 100
    return "$rub,${kop.toString().padStart(2, '0')}"
}

private fun parseRub(text: String): Long? {
    val cleaned = text.replace(" ", "").replace(',', '.').trim()
    return cleaned.toBigDecimalOrNull()
        ?.multiply(java.math.BigDecimal(100))
        ?.toLong()
}

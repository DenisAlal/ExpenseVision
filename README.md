# Expense Vision

Android-приложение: сканирование чеков, распознавание текста (OCR) **на устройстве** и категоризация трат в локальной базе.

## Статус
- **Фаза 0** — многомодульная структура Gradle: готово.
- **OCR-движок** (PaddleOCR PP-OCRv5, кириллица) — собран и проверен на реальном устройстве.
- Доменные модели, Room, экран подтверждения и история — в разработке.

## Модули
| Модуль | Назначение |
|---|---|
| `:app` | точка входа, DI, навигация |
| `:core:model` | доменные модели, парсер полей, категоризатор (чистый Kotlin) |
| `:core:database` | Room: сущности, DAO, репозитории |
| `:core:ocr` | OCR-движок: PaddleOCR (ONNX Runtime) + OpenCV + Clipper |
| `:core:designsystem` | тема и общие UI-компоненты |
| `:feature:scan` | сканирование чека → OCR → подтверждение |
| `:feature:receipts` | история, фильтр по категориям |

## Стек
- Kotlin, Jetpack Compose
- ONNX Runtime (on-device инференс)
- OpenCV (обработка изображений)
- ML Kit Document Scanner (захват и кадрирование чека)
- Room, Hilt, Navigation — в планах

## Vendored-зависимости

### Clipper (`de.lighti.clipper` 6.4.2)
Используется в `:core:ocr` для операции `unclip()` (полигональное раздувание рамок) на этапе детекции текста.

Библиотека **не опубликована в Maven Central**, поэтому подключается локальным jar-файлом:
`core/ocr/libs/Clipper-6.4.2.jar`

- **SHA-256:** `DF1020524CC2CAD78D57A6928CF94D00E8558397CCF8D227D2C8F4AF1023B970`
- **Источник:** репозиторий RapidOCR (`OcrLibrary/libs`), лицензия Apache-2.0.

## Сборка
```bash
./gradlew :app:assembleDebug
```
Требуется Android SDK и JDK 17. Для работы сканера — устройство с Google Play Services.

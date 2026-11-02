# David Live 3D Mesh Sphere Visualizer (Issue #56) — SESSION HANDOFF

## 1. Задача и цель
Реализация Issue #56: Полный редизайн визуализатора **David Live** в стиле Obsidian Neo-Bank на основе приложенного дизайн-макета:
- Замена старого радиального эквалайзера узнаваемой анимированной 3D mesh-сферой (прозрачная объёмная оболочка, светящиеся параллели и меридианы, плавающие узлы/частицы, обсидиановая сердцевина);
- Централизованная модель визуальных состояний `DavidLiveVisualState` (13 состояний);
- Интеграция с реальными событиями приложения (PCM/FFT спектр, активный спикер, затухание пауз 380 мс, drain аудиодорожки, вызовы финансовых инструментов);
- Интерактивность сферы: прямое нажатие на сферу в `OFFLINE` запускает reconnect, в `API_MISSING` открывает экран ключа, в обычных режимах переключает микрофон;
- Облачко статуса с контекстными действиями («Повторить», «Добавить API», «Настроить API»);
- Встроенная панель отладочного переключения состояний (`DavidLiveVisualStateDebugBar`) без необходимости подключения к Gemini Live.

## 2. Что реализовано
1. **`DavidLiveMeshSphereVisualizer.kt`**:
   - Математическая 3D-проекция сетки сферы на Canvas:
     - 7 широтных параллелей с гармонической деформацией от реальной энергии звука;
     - 8 долготных меридианов с перспективой;
     - Передняя/задняя полусферы с depth-attenuation (яркий фронт, приглушённый тыл);
     - Обсидиановое ядро (`#060D17` / `#02060C`) с атмосферным неоновым ореолом (Halo);
     - Специфические анимированные слои для состояний:
       - `FINANCE_LOOKUP`: планетарное сканирующее кольцо с 4 микро-акцентами (документ, график, монета, календарь);
       - `ANALYZING`: перекрёстные 3D-орбиты (+34° и -34°) с вращающимися маяками;
       - `USER_SPEAKING`: нижний 20-полосный эквалайзер речи под сферой;
       - `DAVID_SPEAKING`: акустические расширяющиеся дуги давления по бокам сферы;
       - `CONNECTING` / `RECONNECTING`: сегментированные вращающиеся дуги рукопожатия;
       - `OFFLINE`, `API_MISSING`, `ERROR`, `SUCCESS`: центральные светящиеся бейджи с иконками («!», ключ, «✓»).
   - Плавное затухание волн (380 мс `FastOutSlowInEasing`) при окончании речи — сфера никогда не исчезает и не обрывается резко.
   - Панель ручного переключения всех 13 состояний (`DavidLiveVisualStateDebugBar`).

2. **`DavidLiveCircularVisualizer.kt`**:
   - Полная обратная совместимость: делегирует отрисовку в `DavidLiveMeshSphereVisualizer`, сохраняя семантические `testTag("david_live_circular_visualizer")` и `testTag("david_live_mic_button")`.

3. **`DavidLiveModels.kt`**:
   - Единая модель `DavidLiveVisualState` (13 состояний);
   - Классификатор причин сбоя `DavidLiveConnectionFailureKind` (различает отсутствие интернета, отсутствие API-ключа и неверный ключ);
   - Детерминированный резолвер `DavidLiveVisualStateResolver` со строгим приоритетом (Offline > Error > Reconnecting > Connecting > David > User > Finance Lookup > Analyzing > Preparing > Thinking > Success > Idle);
   - Генератор метаданных `resolveMetadata` с русскими заголовками и типами действий.

4. **`DavidLiveAudioEngine.kt`**:
   - Защита от сброса анимации на коротких паузах между словами (`_isWithinUserSpeechHold`, `_isWithinDavidSpeechHold`, окно 420 мс);
   - Точный контроль завершения воспроизведения (`isPlaybackDrained`).

5. **`DavidLiveSessionController.kt`**:
   - Управление фазами жизненного цикла финансового анализа: `isToolLookupActive`, `activeToolName`, `isAnalyzingToolData`, `isPreparingResponse`, `isModelThinking`, `isSuccessPulseActive`;
   - Классификация сетевых ошибок и ошибок ключа;
   - Методы `triggerSuccessPulse()` и `setDebugVisualStateOverride()`.

6. **`DavidLiveSheet.kt`**:
   - Полноэкранный голосовой экран David Live с новым визуализатором `DavidLiveMeshSphereVisualizer`;
   - Контекстное облачко статуса с кнопками («Повторить», «Добавить API», «Настроить API»);
   - Интерактивное нажатие на сферу;
   - Поддержка отладочного переключателя состояний.

7. **`DavidViewModel.kt` & `DavidChatScreen.kt` & `MainActivity.kt`**:
   - Проброс StateFlow `liveVisualState`, `liveActiveToolName`, `liveFailureKind`, `liveDebugOverrideVisualState`;
   - Вызовы `setDebugLiveVisualStateOverride` и `triggerLiveSuccessPulse`.

8. **Тесты**:
   - `DavidLiveUnitTest.kt`: unit-тесты для матрицы приоритетов `DavidLiveVisualStateResolver`, формирования метаданных и переходов контроллера;
   - `DavidLiveUiTest.kt`: UI-тесты переключения микрофона, отмены сессии, прямого нажатия на сферу в `OFFLINE` (reconnect) и `API_MISSING` (settings), а также отладочного чипа переключения состояний.

## 3. Изменённые файлы
- `app/src/main/java/com/example/davidapp/live/DavidLiveMeshSphereVisualizer.kt` (новый)
- `app/src/main/java/com/example/davidapp/live/DavidLiveCircularVisualizer.kt`
- `app/src/main/java/com/example/davidapp/live/DavidLiveModels.kt`
- `app/src/main/java/com/example/davidapp/live/DavidLiveAudioEngine.kt`
- `app/src/main/java/com/example/davidapp/live/DavidLiveSessionController.kt`
- `app/src/main/java/com/example/davidapp/live/DavidLiveSheet.kt`
- `app/src/main/java/com/example/davidapp/DavidViewModel.kt`
- `app/src/main/java/com/example/davidapp/DavidChatScreen.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/test/java/com/example/davidapp/live/DavidLiveUnitTest.kt`
- `app/src/androidTest/java/com/example/davidapp/live/DavidLiveUiTest.kt`

## 4. Состояние Git и CI/CD (PR #84)
- **Рабочая ветка**: `feat/issue-56-david-live-mesh-visualizer`
- **Pull Request**: [#84 (`https://github.com/smaks190-crypto/-/pull/84`)](https://github.com/smaks190-crypto/-/pull/84)
- **Базовый коммит исправления кода в PR #84**: `ed78b80f2c926d13134991f6a3618e3a17e93505`
- **Исправлено в PR #84 (после Run #319)**:
  - Реализована функция `resolveVisualStateColors(visualState: DavidLiveVisualState, isMicMuted: Boolean): Pair<Color, Color>` в `DavidLiveSheet.kt` для всех 13 визуальных состояний (устраняет `Unresolved reference: resolveVisualStateColors` на строках 239, 366, 713, 1528 и каскадные ошибки деструктуризации `component1`/`component2`/`copy`);
  - Добавлена строгая защита `isDebugModeEnabled = BuildConfig.DEBUG` в `DavidLiveVisualStateResolver.resolve`, `DavidLiveSessionController` и `DavidLiveSheet`, чтобы `debugOverrideState` никогда не подменял реальные состояния (включая приоритетный `OFFLINE`) в Release-сборках;
  - Дополнены `DavidLiveUnitTest.kt` и `DavidLiveUiTest.kt` проверками цветов всех 13 состояний и защиты Release-режима.

## 5. Инструкция для ручной проверки на устройстве
1. **Ожидание (Idle / Listening)**:
   - Открыть чат Давида, запустить David Live.
   - Сфера отображается глубоким изумрудным цветом с мягким дыханием и плавающими частицами.
2. **Речь пользователя (User Speaking)**:
   - Произнести фразу.
   - Сфера моментально реагирует волнами бирюзового цвета, под сферой анимируются полосы эквалайзера.
   - При паузе между словами сфера не обрывается резко (плавное затухание 380 мс).
3. **Поиск финансов (Finance Lookup) и Анализ (Analyzing)**:
   - Спросить «Сколько я потратил на кофе?».
   - Во время обращения к БД появляется наклонное кольцо сканирования с микро-иконками финансов.
   - Во время сопоставления активируются перекрёстные орбиты с маяками.
4. **Речь Давида (David Speaking)**:
   - Давид отвечает голосом.
   - По бокам сферы пульсируют концентрические звуковые волны, сфера переливается изумрудно-бирюзовыми волнами.
5. **Потеря связи (Offline)**:
   - Отключить сеть. Сфера становится красной с иконкой «!».
   - Нажатие непосредственно на сферу запускает попытку переподключения.
6. **Отсутствие ключа (API Missing)**:
   - Удалить ключ из настроек. Сфера становится золотистой с иконкой ключа.
   - Нажатие на сферу или кнопку «Добавить API» открывает экран ввода ключа.
7. **Тестовый режим (Debug Bar)**:
   - Нажать на плашку «David Live • LIVE» в шапке — открывается горизонтальный ряд чипов для проверки всех 13 состояний.

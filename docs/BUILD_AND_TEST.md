# 建置、測試與發佈指南

## 1. 必要環境

- JDK 17 或相容的 JDK 21，供 Gradle／Android 建置使用。
- Android SDK Platform 36、Build Tools 及 Platform Tools。
- Android Studio 為建議工具；本專案也可使用命令列。
- Android 16 的 Robolectric 案例需 Java 21 以上的測試 JVM。

Gradle Wrapper 已固定 8.13，不需要另外安裝系統 Gradle。首次建置需下載 Gradle、Android 建置與測試相依套件；APP 執行本身離線。

## 2. 取得專案

```sh
git clone https://github.com/mark216tw/android-minipiano-01.git
```

在專案根目錄執行後續指令。將 `ANDROID_HOME` 指向 SDK，或建立 `local.properties`：

```properties
sdk.dir=C:/path/to/Android/Sdk
```

macOS／Linux 可改為對應的絕對 SDK 路徑。該檔案不提交至 Git。

## 3. Debug 建置及安裝

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug --console=plain
adb devices -l
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

macOS／Linux：

```sh
./gradlew assembleDebug --console=plain
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

若從不保留執行權限的壓縮檔取得專案，可先執行 `chmod +x gradlew`。USB 安裝需在手機啟用開發人員選項／USB 偵錯並允許連線。

## 4. 單元測試與 Lint

若 Gradle 本身使用 JDK 21：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

若 Gradle 使用 JDK 17，另外指定測試 JVM：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug "-PtestJavaHome=C:/path/to/jdk-21" --console=plain
```

`testJavaHome` 只設定測試程序的 Java 執行檔，不改專案 Java 17 位元碼設定。請使用與 Gradle 相容的建置 JVM；不代表 Gradle 8.13 可直接以任何較新 JDK 執行。

### 外部舒曼附件

附件不隨檔案庫散布；可自行提供對應檔案：

```powershell
.\gradlew.bat testDebugUnitTest "-PtestJavaHome=C:/path/to/jdk-21" "-PattachmentTestFile=C:/path/to/little-song-in-canon-form-op-68-no-27.musicxml" --console=plain
```

目前有 78 個案例。未提供附件時兩個案例略過，其餘照常執行；提供附件的完整回歸為 78／78。報告在：

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/test-results/testDebugUnitTest/`

### 測試群組

| 類別 | 案例數 |
|---|---:|
| MusicXmlTest | 11 |
| AdvancedMusicXmlTest | 16 |
| WrappedMusicXmlTest | 8，其中 2 個外部附件 |
| MusicXmlStorageUiTest | 6 |
| PianoSampleTest／SampleVoiceTest | 2／5 |
| SampleBankTest／SampleBankStoreTest | 8／2 |
| StartupTest | 3 |
| ThemeSettingsTest | 14 |
| TimbreBalanceTest | 3 |

部分 Android 框架測試在 API 26／36 各執行一次，啟動測試涵蓋 API 26／29／36。

## 5. 文件與 APK 資源檢查

以下工具僅需 Python 標準函式庫：

```powershell
python tools/check_docs.py
python tools/verify_piano_apk.py
git diff --check
```

APK 檢查驗證兩套音色、88 鍵覆蓋、53 個採樣雜湊、作者及完整授權，並輸出實際容量。

## 6. 音色重建與響度量測

一般建置不需要重建音色。要重建時，建議使用 Python 3.12 以上及獨立虛擬環境：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r tools/requirements-audio.txt
.\.venv\Scripts\python.exe tools/prepare_piano.py --cache "C:/path/to/cache/salamander"
.\.venv\Scripts\python.exe tools/prepare_upright.py --cache "C:/path/to/cache/vsco"
.\.venv\Scripts\python.exe tools/measure_piano_loudness.py --report docs/audio/before.json
.\.venv\Scripts\python.exe tools/measure_piano_loudness.py --calibrate --report docs/audio/calibrated.json
```

macOS／Linux 使用 `.venv/bin/python`。素材來源固定於腳本中的 Git revision；快取原檔不需提交。重建會更新 APP assets 的 WAV／對照表，量測檔對應 `TimbreBalance` 的補償值。

## 7. 真機驗收

每次音訊或互動版本至少記錄：

- 裝置型號、Android、輸出方式及音量。
- 多指、同音連按、滑奏、延音釋放。
- 三音色切換、載入、音量與和弦聽感。
- 音樂速度變化、跳轉、暫停恢復與聲部靜音。
- 系統返回、背景／前景、檔案選擇器及主題。
- 長曲／大型檔案的解析時間與記憶體。

框架測試不能量測觸控到喇叭的端到端延遲；應另以一致的實機方法記錄。

## 8. 發佈

本檔案庫包含來源、文件及必要音色，`build/` 產物不提交。Debug APK 適合測試；正式發佈需自行設定 release 簽章與發佈流程。簽章金鑰及本機設定不納入檔案庫。

發佈前同步版本號、CHANGELOG、測試結果及 [第三方授權](../THIRD_PARTY_NOTICES.md)。若建立 GitHub Release，可附上經驗證的 APK 與版本說明。

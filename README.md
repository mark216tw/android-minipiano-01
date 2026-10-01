# mini 鋼琴 · Android

一款離線、多指操作的 Android 電子琴 APP，提供真實鋼琴採樣、演奏錄製、MusicXML 匯入／匯出與自動彈奏。介面以琴鍵提示呈現音樂，不顯示五線譜。

![版本](https://img.shields.io/badge/版本-1.0.0--prerelease-167C70)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![專案授權](https://img.shields.io/badge/程式碼授權-MIT-blue)

## 專案資訊

| 項目 | 內容 |
|---|---|
| APP 名稱 | mini 鋼琴 |
| 套件名稱 | `com.minipiano.app` |
| 測試發行版本 | `1.0.0-prerelease`，版本代碼 `11`；Pre-release，非正式上線版本 |
| 最低系統 | Android 8.0，API 26 |
| 編譯／目標 SDK | API 36，Android 16 |
| 開發技術 | Java 17、原生 Android View、AudioTrack |
| 維護者 | [mark216tw](https://github.com/mark216tw) |
| 公開檔案庫 | [mark216tw/android-minipiano-01](https://github.com/mark216tw/android-minipiano-01) |

## 主要功能

- **彈奏**：多指和弦、滑奏、音域切換、8／16／24 個白鍵的鍵寬（預設 16）、延音踏板。
- **音色**：Salamander 大鋼琴、VSCO 直立鋼琴、合成鋼琴；64 個發聲槽與獨立鋼琴音量。
- **反應與音量**：持續運作的低延遲音訊串流、背景採樣載入、三種音色響度補償。
- **錄製**：音符與踏板事件、BPM、4/4／3/4／6/8 拍、錄製倒數與節拍器。
- **MusicXML**：XML、MXL、JSON 包裝匯入，支援力度、漸強／漸弱、變速、反覆、跳房子、跳轉、跨譜表延音與聲部靜音。
- **播放**：共用播放／暫停圖示、獨立停止圖示、進度跳轉、0.5–1.5 倍速選單、起音閃亮回饋、目前 BPM 與原譜小節位置。
- **背景播放**：切換頁面、Home 與鎖屏時持續播放；前景服務、播放通知與鎖屏媒體控制，音訊焦點中斷時暫停。
- **音譜庫**：匯入與錄製自動新增獨立曲目，提供播放、改名、刪除與上次選取還原。
- **匯出**：保存原 XML 的原譜轉存，以及量化後重新建立鋼琴譜的演奏資料匯出。
- **外觀**：預設全螢幕、系統／淺色／深色模式、六款主題色、連動 Hue 滑桿、自適應鋼琴鍵圖示。
- **離線使用**：無需帳號、網路或麥克風權限；音譜庫保存於 APP 私有空間。

## 開始使用

1. 建置並安裝 APK，開啟 APP。
2. 直接試彈，或匯入／錄製曲目後按 ▶ 播放。
3. 按右上角齒輪選擇音色、音量、顯示模式及錄製設定。
4. 按「匯入」選擇樂譜，查看匯入摘要後播放。
5. 使用「聲部」選擇要聽的聲部，或按「錄製」保存自己的演奏。
6. 按「匯出」選擇原譜轉存或演奏資料；按「音譜庫」管理已保存曲目。

詳細步驟請見 [使用指南](docs/USER_GUIDE.md)。主畫面按鈕順序為「匯入 → 匯出 → 音譜庫」。設定與音譜庫固定直向，返回主畫面恢復橫向。既有單份演奏會自動移入音譜庫。

## 快速建置

需要 JDK 17 或相容的 JDK 21、Android SDK Platform 36。設定 `ANDROID_HOME`，或建立不納入 Git 的 `local.properties`：

```properties
sdk.dir=C:/path/to/Android/Sdk
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

macOS／Linux：

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

產物為 `app/build/outputs/apk/debug/app-debug.apk`，不納入原始碼版本控制。完整環境、測試及發佈步驟請見 [建置與測試指南](docs/BUILD_AND_TEST.md)。

### Pre-release 測試發行版

```powershell
.\gradlew.bat assemblePrerelease
```

產物為 `app/build/outputs/apk/prerelease/app-prerelease.apk`。`prerelease` Build Type 啟用 R8 程式碼壓縮、最佳化及混淆與資源縮減，關閉 debuggable，使用 Debug 金鑰簽署。版本為 `1.0.0-prerelease`，僅供測試，不是正式上線版本；APK 透過 GitHub **Pre-release** 提供下載。

## 文件索引

| 文件 | 用途 |
|---|---|
| [文件首頁](docs/README.md) | 依讀者角色找到所需文件 |
| [專案說明](docs/PROJECT_OVERVIEW.md) | 目標、功能範圍、限制與發展方向 |
| [使用指南](docs/USER_GUIDE.md) | 安裝、彈奏、錄製、播放、設定與匯出 |
| [系統架構](docs/ARCHITECTURE.md) | 模組、執行緒、資料流與元件關係 |
| [技術參考](docs/TECHNICAL_REFERENCE.md) | 版本、音訊演算法、主題、圖示與工具 |
| [系統設計](docs/SYSTEM_DESIGN.md) | 使用情境、狀態、設計決策與取捨 |
| [資料格式](docs/DATA_FORMATS.md) | 曲目 JSON、偏好設定、音色對照表 |
| [MusicXML 支援](docs/MUSICXML_SUPPORT.md) | 記號支援、解讀規則及匯出差異 |
| [建置與測試](docs/BUILD_AND_TEST.md) | 環境、指令、測試與真機驗收 |
| [疑難排解](docs/TROUBLESHOOTING.md) | 音訊、匯入、資料與 Android 問題 |
| [響度量測](docs/audio/README.md) | 三種音色補償方法與量測資料 |
| [版本紀錄](CHANGELOG.md) | 各版本的功能與修正 |
| [貢獻指南](CONTRIBUTING.md) | 開發、測試、問題回報與提交方式 |
| [第三方聲明](THIRD_PARTY_NOTICES.md) | 音色、Gradle Wrapper 與相依套件來源 |

## 驗證與目前限制

未提供外部舒曼附件時，兩個附件案例會略過。已完成 MusicXML、採樣、響度補償、保存／匯出、主題、音譜庫、背景播放狀態及 Android 8／10／16 的框架回歸測試。背景播放與鎖屏控制仍需真機驗收。

框架測試不取代手機實測。端到端音訊延遲、喇叭／耳機聽感與不同裝置的觸控表現仍需真機驗收。採樣為單一力度層，長音到素材尾端會自然停止；純文字 `ritard.`／`accel.` 不猜測變速，其他簡化內容會顯示於匯入摘要。

## 授權

本專案自行開發的程式碼、原創文件與原創圖示採用 **MIT License**：

- [MIT 授權原文](LICENSE)：提供標準授權條款與 GitHub 授權辨識。
- [MIT 繁體中文譯文](LICENSE.zh-TW.md)：供閱讀參考，正式條款以原文為準。
- Salamander 採樣為 **CC BY 3.0**；VSCO 採樣為 **CC0 1.0**；Gradle Wrapper 為 **Apache-2.0**。

第三方素材各自保留原授權。完整範圍及檔案連結請見 [第三方聲明](THIRD_PARTY_NOTICES.md)。

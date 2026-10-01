# 系統架構與技術參考

## 1. 工具與版本

| 項目 | 版本／設定 |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| Java 語言／位元碼 | 17 |
| compileSdk／targetSdk | 36／36 |
| minSdk | 26 |
| JUnit | 4.13.2 |
| Robolectric | 4.16.1 |
| 測試 JVM | Android 16 案例需要 Java 21 以上 |

APP 執行時沒有第三方播放引擎或 NDK 相依套件。`android.useAndroidX=true` 配合測試相依套件，不表示主畫面使用 AndroidX UI。

## 2. 音訊輸出

PianoAudio 使用 `ENCODING_PCM_FLOAT`、立體聲、串流模式及低延遲效能模式。裝置取樣率從 AudioManager 取得，無有效數值時採 48 kHz；區塊框數依裝置屬性限制在 64–512，預設 192。

立體聲 float 每框 8 bytes，緩衝至少滿足 AudioTrack 最小要求及兩個區塊。音訊執行緒使用 `THREAD_PRIORITY_AUDIO`，阻塞寫入負責與裝置輸出速度同步。

### 發聲槽

- 預先配置 64 個 Voice。
- 手動群組與自動群組分開。
- 自動音符附帶 part／聲部索引，踏板依 part 管理。
- 沒有可用槽時，依現有槽的 level 選擇取代對象。
- 放音使用衰減，停止／跳轉使用較快淡出。

### 採樣變調

```text
步進量 = 原採樣取樣率 / 輸出取樣率 × 2^((目標音高 − 根音高) / 12)
```

每個發聲槽持有獨立位置，左右聲道以線性插值讀取。起音有約 2 ms 的短斜坡；一般放鍵的衰減常數為 0.10 秒，高音為 0.6 秒，快速清理為 0.025 秒。這些值是衰減常數，不代表到該時刻立即歸零。

### 合成音色

使用 2,048 點正弦查表，加入第 2、3、5 泛音，搭配依音高調整的衰減與快速消退的起音成分。備用合成聲與手動選用的合成聲使用相同響度補償。

### 音量與限幅

1. 起音時依音色／音高查表補償，並套用力度。
2. 混合所有音符。
3. 套用共用音量與 1.5 倍主增益。
4. 加入獨立節拍器。
5. 每聲道使用 `x / (1 + abs(x))` 軟限幅。

音量目標透過每框平滑靠近，避免突然切換。補償錨點為 MIDI 24、48、72、96、108，詳見 [響度量測](audio/README.md)。

## 3. 音色資源

| 音色 | 採樣數 | 格式 | WAV 容量 | 解碼 float PCM |
|---|---:|---|---:|---:|
| Salamander | 30 | 24 kHz、立體聲、PCM16 | 約 21.67 MiB | 約 43.33 MiB |
| VSCO Upright | 23 | 24 kHz、立體聲、PCM16 | 約 14.29 MiB | 約 28.58 MiB |

兩套音色映射 MIDI 21–108，沒有循環。銀行解碼上限 64 MiB，切換使用單包快取與載入代次。`PianoSample` 支援 16-bit PCM WAV、1／2 聲道與 8–96 kHz；實際打包素材統一為上述格式。

採樣層與素材處理依 [第三方聲明](../THIRD_PARTY_NOTICES.md) 保留記錄。APK 雜湊與來源可用 `tools/verify_piano_apk.py` 檢查。

## 4. 音樂時間

- PPQ：每四分音符 480 ticks。
- TempoMap：保存速度段的起始 tick、累積秒數及 BPM，以二分搜尋換算。
- PlaybackProgram：將音符／踏板預編譯為秒制事件。
- 同時事件順序：放音 → 原順序踏板事件 → 起音。
- PlaybackClock：分開保存樂曲秒數與實際經過秒數。
- 倍率改變樂曲時間的推進，不改採樣音高。
- 暫停恢復使用已演奏年齡；跳轉建立新的年齡基準，並短暫淡入。

相關解讀規則見 [MusicXML 支援](MUSICXML_SUPPORT.md)。

## 5. 觸控與動畫

PianoView 使用 SparseIntArray 保存 pointer ID 到音高的關係。移動到新鍵時先放舊音再起新音；取消觸控會放開所有手指。

自動起音時間以 AtomicLongArray 發佈至 UI，搭配亮鍵 bit mask。動畫期間約 140 ms；主畫面以約 33 ms 的更新間隔重繪，並不負責音訊起音排程。

## 6. 主題與系統列

UiTheme 將顯示模式、主題 Hue 轉為 primary、onPrimary、background、surface、text、muted。Hue 固定飽和度 70%、明度 90%，文字依主色選黑／白以取得較高對比。

預設 Hue 為 160°，其他預設為 205、265、330、25、48°。錄製核取方塊列不使用帶主題色的 surface，而使用固定白色／中性深藍灰。

API 30 以上使用 WindowInsetsController；較舊版本使用系統 UI flags。存取控制器前初始化 DecorView，避免 Android 16 啟動空指標例外。主畫面隱藏系統列，設定頁顯示系統列，保留挖孔及輸入法 Insets。

## 7. 圖示

- 前景／背景畫布：108 × 108 dp。
- 中央裁切參考：72 × 72 dp。
- 安全區參考：66 dp。
- 核心輪廓含描邊：59.4 × 59.4 dp，約安全區的 90%。
- 鋼琴黑白鍵、大圓角、粗線條與薄荷綠背景。
- Android 13 以上另提供 monochrome 主題圖層。

原始向量位於 `app/src/main/res/drawable/`；啟動器決定實際裁切形狀。

## 8. 開發工具

| 工具 | 工作 |
|---|---|
| `prepare_piano.py` | 固定來源 Salamander 音色重建 |
| `prepare_upright.py` | 固定來源 VSCO 音色重建 |
| `verify_piano_apk.py` | APK 音色、雜湊、音域與授權檢查 |
| `measure_piano_loudness.py` | 離線音色響度量測與曲線擬合 |
| `check_docs.py` | 專案 Markdown 本地連結檢查 |

音色重建／量測需要 Python 套件；一般 Android 建置不需要 Python。流程見 [建置指南](BUILD_AND_TEST.md)。

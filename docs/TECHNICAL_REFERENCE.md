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

PianoAudio 使用 `ENCODING_PCM_FLOAT`、立體聲、48 kHz、串流模式及低延遲效能模式；若裝置原生率不同，由 Android AudioFlinger 重取樣。區塊框數依裝置屬性限制在 64–512，預設 192。

立體聲 float 每框 8 bytes，緩衝至少滿足 AudioTrack 最小要求及兩個區塊。音訊執行緒使用 `THREAD_PRIORITY_AUDIO`，阻塞寫入負責與裝置輸出速度同步。

### 發聲槽

- 預先配置 72 個 Voice，正常同時發聲上限 64，其餘保留給搶聲部淡出尾巴。
- 手動群組與自動群組分開。
- 自動音符附帶 part／聲部索引，踏板依 part 管理。
- 沒有可用槽時，優先搶已放開且無踏板的最舊聲部，避免直接切斷按住音；被搶聲部以 6 ms 淡出。
- 放音依音高使用制音器時間，停止／跳轉使用較快淡出；無制音器的高音延長至 1.8 秒。

### 採樣變調

```text
步進量 = 原採樣取樣率 / 輸出取樣率 × 2^((目標音高 − 根音高) / 12)
```

每個音高含柔（soft）、中（medium）、亮（loud）三個力度層，在層邊界前後 6 個 MIDI 力度單位內交叉淡化，其餘時間只播放單層。每個發聲槽持有獨立位置，左右聲道以 4 點 Hermite 插值讀取；取樣率高於輸出時另受低通保護。起音有約 2 ms 的短斜坡；一般放鍵依音高使用 0.045–0.225 秒衰減常數，無制音器高音為 1.8 秒，快速清理為 0.025 秒。

### 合成音色

以 2,048 點正弦查表合成發聲（加入第 2、3、5 泛音、依音高調整的衰減與快速消退的起音成分），可作為可選音色，亦在取樣載入中或載入失敗時自動備援，並套用相同的響度補償。

### 音量與限幅

1. 起音時套用力度曲線與逐鍵／逐層增益。
2. 混合所有音符。
3. 套用共用音量與 1.5 倍主增益；可選空間殘響以乾／濕並聯加入。
4. 加入獨立節拍器（不經殘響）。
5. 以 2 ms look-ahead、−1 dBFS 門檻、100 ms release 的立體聲連動限幅器保護輸出；低於門檻時完全線性。

音量目標透過每框平滑靠近，避免突然切換。取樣音色使用 manifest 的逐鍵 RMS 校正；合成音色沿用 MIDI 24、48、72、96、108 錨點，詳見 [響度量測](audio/README.md)。

## 3. 音色資源

| 音色 | 採樣數 | 格式 | WAV 容量 | 解碼 16-bit PCM |
|---|---|---:|---|---:|---:|
| Salamander | 30 音高 × 3 力度層 | 低／中 32 kHz、高 48 kHz，立體聲 PCM16 WAV | 約 59.29 MiB | 約 59.29 MiB |
| 合成 | 程式產生 | 2,048 點正弦查表 | 無素材 | 無素材 |

Salamander 映射 MIDI 21–108，沒有循環。銀行解碼上限 64 MiB，切換使用單包快取與載入代次。`PianoSample` 支援 16-bit PCM WAV、1／2 聲道與 8–96 kHz；素材在載入執行緒解碼，音訊執行緒只讀取 `short[]`。`FlacSampleReader` 保留為未來可選格式，目前打包素材為 WAV，不經過 MediaCodec。

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

UiTheme 將顯示模式、主題 Hue 轉為 primary、onPrimary、background、surface、text、muted。Hue 固定飽和度 45%、明度 96%，文字依主色選黑／白以取得較高對比。

預設 Hue 為 158°，其他預設為 205、270、340、20、48°。錄製核取方塊列不使用帶主題色的 surface，而使用固定白色／中性深藍灰。

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
| `verify_piano_apk.py` | APK 音色、雜湊、音域與授權檢查 |
| `measure_piano_loudness.py` | 離線音色響度量測與曲線擬合 |
| `check_docs.py` | 專案 Markdown 本地連結檢查 |

音色重建／量測需要 Python 套件；一般 Android 建置不需要 Python。流程見 [建置指南](BUILD_AND_TEST.md)。

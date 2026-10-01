# 資料格式與相容性

## 1. 時間與音高

音高採 MIDI 編號，鋼琴支援 21–108。曲目時間採 PPQ 480，即一個四分音符為 480 ticks；分段速度由 TempoMap 換算為秒。

音符資料包括：音高、起音 tick、持續 ticks、力度、part、voice、起始 staff 與 staff mask。part／voice／staff 是不同概念，不用音高代替。

## 2. 目前曲目保存檔

檔案為 APP 私有空間的 `performance.json`，透過 AtomicFile 原子寫入。第 2 版欄位：

| 欄位 | 型別 | 說明 |
|---|---|---|
| `schemaVersion` | 整數 | 目前為 2 |
| `title` | 字串 | 曲目名稱 |
| `bpm` | 整數 | 起始／錄製 BPM；精確變速另存 tempos |
| `beats`／`beatType` | 整數 | 起始拍號 |
| `endTick` | 整數 | 包含尾端休止符的演奏終點 |
| `originalMeasureCount` | 整數 | 原譜小節數 |
| `originalXml` | 可選字串 | 解包後原始 XML |
| `notes` | 陣列 | 音符資料 |
| `pedals` | 陣列 | 踏板資料 |
| `tempos` | 陣列 | `[tick, bpm]` |
| `measures` | 陣列 | `[tick, duration, number, visit]` |
| `partNames` | 物件 | part ID 到樂器名稱 |
| `mutedVoices` | 字串陣列 | 靜音聲部鍵 |
| `features`／`warnings` | 字串陣列 | 匯入摘要內容 |

### 音符與踏板

```text
notes = [pitch, start, duration, velocity, part, voice, staff, staffMask]
pedals = [tick, down, part]
```

`staffMask` 第 `staff - 1` 個 bit 表示經過該譜表。例如 3 表示譜表 1、2。聲部鍵為 `part + "\u001f" + voice`，JSON 會以跳脫字元保存控制字元。

示意資料：

```json
{
  "schemaVersion": 2,
  "title": "我的演奏",
  "bpm": 120,
  "beats": 4,
  "beatType": 4,
  "endTick": 480,
  "originalMeasureCount": 0,
  "notes": [[60, 0, 480, 90, "P1", "1", 1, 1]],
  "pedals": [],
  "tempos": [],
  "measures": [],
  "partNames": {"P1": "Piano"},
  "mutedVoices": [],
  "features": [],
  "warnings": []
}
```

### 舊格式

舊音符只有四欄、踏板只有兩欄，仍可讀取：

```text
notes = [pitch, start, duration, velocity]
pedals = [tick, down]
```

缺少 part／voice 時預設 `P1`／`1`，staff 依既有錄製預設規則建立。缺少原 XML、速度或摘要時保留空值，不偽造遺失的樂譜記號。

## 3. SharedPreferences

偏好檔名稱為 `MainActivity`。

| 鍵 | 值與預設 |
|---|---|
| `displayMode` | 0 系統、1 淺色、2 深色；預設 0 |
| `themeHue` | 0–359；預設 160 |
| `customTheme` | 是否為自訂色，預設 false |
| `pianoSound` | 0 Salamander、1 VSCO、2 合成 |
| `sampledPiano` | 舊布林設定；遷移時 true→0、false→2 |
| `pianoVolume` | 0–100，預設 100 |
| `bpm` | 錄製 BPM，預設 120 |
| `beats`／`beatType` | 預設 4／4 |
| `countIn` | 預設 true |
| `metro` | 預設 false |

PianoSound 首次讀取舊偏好時建立新選項；新選擇仍同步舊布林值。

## 4. 音色包對照表

每個銀行的 `instrument.json` 位於 `assets/piano/` 或 `assets/upright/`，包括作者、授權、固定來源版本、取樣率、聲道、修改說明與採樣清單。

單個採樣的主要欄位：

| 欄位 | 說明 |
|---|---|
| `file` | `note_NNN.wav` |
| `rootMidi` | 素材基準音高 |
| `lowMidi`／`highMidi` | 適用音域，含端點 |
| `frames` | PCM 框數 |
| `sourceFile` | 原始素材名稱 |
| `sourceSha256` | 原檔雜湊 |
| `sha256` | 手機素材雜湊 |

VSCO 另記原取樣率與裁切起音框數。音域不重疊且覆蓋 88 鍵，銀行解碼時會驗證格式及長度。

## 5. MusicXML 輸入包裝

- XML：`score-partwise` 主文件。
- MXL：ZIP 內 `META-INF/container.xml` 指定的 rootfile。
- JSON：根物件的非空字串 `musicxml`，解碼後再解析 XML。

JSON 外部的 title、tempo 等 metadata 不覆寫內層 XML。原譜轉存亦保存內層 XML；詳見 [MusicXML 支援](MUSICXML_SUPPORT.md)。

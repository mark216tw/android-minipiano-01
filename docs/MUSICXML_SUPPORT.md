# MusicXML 支援與解讀規則

## 1. 輸入與限制

| 項目 | 規則 |
|---|---|
| 主格式 | `score-partwise` |
| 副檔名 | `.musicxml`、`.xml`、`.mxl`；實際內容也可為 JSON 包裝 |
| JSON | 非空字串 `musicxml`；支援 BOM、換行、引號與 Unicode 跳脫 |
| MXL | 依 container 的 rootfile 路徑，非任意第一個 XML |
| 檔案大小 | 輸入及 MXL 解壓總量上限 8 MiB |
| MXL 項目 | 最多 128 個 ZIP 項目 |
| 原譜 | 最多 10,000 小節、50,000 可播放音符 |
| 展開後 | 最多 10,000 演奏小節、100,000 音符、兩小時 |
| 音高 | MIDI 21–108，超出範圍略過並提示 |
| 速度 | 10–400 四分音符 BPM |

標準外部 DOCTYPE 可保留於原譜，但解析時不載入外部 DTD。自訂 XML 實體不接受。目前使用 UTF-8。

## 2. 音符與時間

- 音符、休止符、和弦、divisions、backup／forward。
- 弱起與非完整小節依 `implicit="yes"` 使用實際時值。
- 多個 part 依小節索引對齊；小節數不一致時提示。
- 分數時值先以原時間累計，再轉 PPQ，避免逐音符四捨五入漂移。
- pitch alter、chromatic 及 octave-change 移調。
- 相同 part／voice／音高、時間連續的延音線合併，staff 可改變。
- 不連續或缺少配對的延音線會提示，跳轉邊界不強行合併。

## 3. 力度

### 數值與記號

| 記號 | MIDI 力度 |
|---|---:|
| pppp／ppp／pp | 10／16／28 |
| p／mp／mf | 40／56／72 |
| f／ff／fff／ffff | 90／110／124／127 |
| sf／sfz／fz／rfz／fp | 瞬間 108 |
| sffz | 瞬間 124 |

未標示預設 90。`sound dynamics` 及 `note dynamics` 依百分比換算為 `round(percent × 0.9)`，限制在 1–127。note 層值優先。

方向力度依 part、可選 staff／voice 範圍套用。sfz 等瞬間力度只影響同位置的新起音；fp 在瞬間強音後回到 p。accent 加 12、strong-accent 加 20，仍受範圍限制。

### 漸強與漸弱

- 以 wedge 的 number、範圍與起止位置配對。
- 起點與終點間，線性改變後續音符的起音力度。
- 終點有對應力度時採該值，否則以起點 ±24 為目標。
- 不對已起音的鋼琴長音重新施加持續漸強。
- 缺少終點或編號衝突會提示。

採樣為單一力度層，因此強弱改變振幅，不切換多力度素材。

## 4. 速度

- 支援 `sound tempo`、metronome per-minute、拍值及附點。
- whole／half／quarter／eighth／16th／32nd／64th 換算為四分音符 BPM。
- 支援 direction offset 與 sound offset；sound 自身 offset 可覆寫方向 offset。
- 同一 part 的同位置後標記優先；不同 part 衝突採第一個樂器並提示。
- TempoMap 換算 tick／秒，總長、進度、倍率與恢復使用同一時間基準。
- 純文字 ritard.／accel. 不猜測減速幅度，會提示未解讀。

## 5. 反覆、房次與跳轉

支援前／後反覆、times 1–32、巢狀反覆、ending 編號清單／範圍及 stop／discontinue。

明確 sound 或標準文字 D.C.／D.S.／Da Capo／Dal Segno、Fine、To Coda 可展開為有限路徑。命名 Segno／Coda 優先；文字不覆寫 sound 的命名目標。

- sound 跳轉可在小節起點或終點執行。
- 中途跳轉暫於小節結尾執行，摘要會提示。
- 回跳預設不再執行反覆，明確 forward-repeat／after-jump 可要求再反覆。
- 反覆／跳轉恢復作者當地的速度、力度及踏板。
- 目標缺失、重複位置或未支援 time-only 條件會提示。

## 6. 聲部與踏板

聲部依 part＋voice 建立，記錄經過的譜表。靜音只影響自動播放，恢復播放時重建當地長音；手動跟彈不受影響。

支援踏板 start、stop、change、resume、discontinue 及 sound damper-pedal。踏板依 part 管理，同位置保留原踏板事件順序。

## 7. 保留與簡化內容

| 內容 | 目前處理 |
|---|---|
| 原始版面、歌詞、樂器、其他記號 | 原譜轉存保留 XML |
| 裝飾音、無固定音高打擊樂、微分音 | 略過並提示 |
| 琶音 | 同時起音並提示 |
| 斷奏／持音 | 保留原時值並提示 |
| 純文字 cresc.／dim. | 不代替 wedge，提示未解讀 |
| 變拍號 | 播放小節長度依資料，重新建立的譜採起始拍號 |
| 複合拍號多組拍值 | 採第一組並提示 |
| score-timewise | 不支援 |

## 8. 匯出模式

**原譜轉存**：直接輸出保存的內層 XML，靜音、量化及演奏順序展開不改寫它。MXL 只轉存主 XML，不包含其他壓縮資源。

**演奏資料**：量化音符並重新建立單一鋼琴譜，將展開後事件寫出；高低音域以 MIDI 60 分譜表，重疊時值分配不同聲部，跨小節用延音線。輸出包含音符力度、速度及踏板，不保留原始版面／歌詞／配置。

## 9. 範例與測試

- [和弦與延音線](../examples/chords-and-ties.musicxml)
- [表情、速度與跳房子](../examples/expression-repeat-and-tempo.musicxml)：順序 1、2、1、3。
- `MusicXmlTest`、`AdvancedMusicXmlTest`、`WrappedMusicXmlTest` 與 `MusicXmlStorageUiTest` 驗證時間、順序、保存及匯出。

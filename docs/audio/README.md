# 鋼琴音色響度平衡

Salamander 銀行在 `tools/prepare_piano.py` 重建時，會以每個力度層前段 0.5 秒的 RMS 計算逐鍵校正增益，寫入 `instrument.json` 的 `gain`；播放時只套用力度曲線 `(v/127)^0.85` 與該校正值。相鄰力度層只在邊界前後 6 個 MIDI 力度單位交叉淡化，避免大範圍混音造成相位抵消。

合成音色沿用 `TimbreBalance` 的五個音高錨點（MIDI 24、48、72、96、108）。合成曲線以 Salamander 為基準，離線比較 A0–C8、固定力度 90、前 0.5 秒持續音與短旋律／和弦段落；使用 BS.1770 的 48 kHz K-weighting 係數量測相對數位響度，不是手機 SPL。

`tools/measure_piano_loudness.py` 目前輸出參考報告與合成曲線，會讀取專案內的音色包；重建音色後應重新產生報告。舊的 `before.json`、`calibrated.json` 對應 32 kHz 雙層版本，僅供歷史比較。

重現（需 numpy、scipy、soundfile）：

```powershell
python tools/measure_piano_loudness.py --report docs/audio/before.json
python tools/measure_piano_loudness.py --calibrate --report docs/audio/calibrated.json
```

共用音量滑桿作用於補償後的鋼琴與殘響；節拍器保持獨立增益，且不經殘響。音色未完成載入而暫用合成時，使用合成音色曲線。仍須在手機喇叭、耳機與藍牙裝置比較實際聽感。

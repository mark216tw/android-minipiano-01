# 三種鋼琴音色響度平衡（0.2.2）

以 Salamander 為基準，離線模擬現有採樣／合成播放、音符包絡、1.5 倍主增益與軟限幅路徑。
比較 A0–C8 的 88 個音高、固定力度 90、前 0.5 秒持續音，以及相同的短旋律／連按／和弦段落。
採用 BS.1770 的 48 kHz K-weighting 濾波係數量測相對數位響度；不是手機 SPL，也不是完整節目整合 LUFS。

依每個音高解出限幅後的匹配增益，再以 robust least-squares 擬合五個音高錨點：MIDI 24、48、72、96、108。
錨點之間線性插值 dB，換算振幅後預先建立查表；保留音色、力度及自然衰減差異，不逐個採樣峰值正規化。
Salamander 全音域增益維持 1。VSCO 補足偏小音量；合成鋼琴低音提高、中高音降低。

`before.json` 是未補償結果；`calibrated.json` 是擬合後結果，dbProfiles 對應 `TimbreBalance.java` 的 DB。
共同段落的 K-weighted 平均值約為：Salamander -20.325 dB、VSCO -19.834 dB、合成 -20.681 dB；最大差異約 0.85 dB。
各低／中／高音域的單音差異中位數均在 ±0.7 dB 內；個別音符仍有差異。

重現（需 numpy、scipy、soundfile）：

```powershell
python tools/measure_piano_loudness.py --report docs/audio/before.json
python tools/measure_piano_loudness.py --calibrate --report docs/audio/calibrated.json
```

共用音量滑桿作用於補償後的鋼琴；節拍器保持獨立增益。音色未完成載入而暫用合成時，使用合成音色曲線。
授權採樣本身沒有重新編碼或修改。仍須在手機喇叭及耳機比較實際聽感。

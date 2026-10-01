# 第三方素材與授權聲明

本專案的 MIT License 適用於自行開發的程式碼、原創文件及原創圖示。以下素材與工具依其原授權提供；發佈原始碼或 APK 時，請保留相應聲明。

## 1. Salamander Grand Piano v3

| 項目 | 內容 |
|---|---|
| 原採樣作者 | Alexander Holm，Yamaha C5 |
| 採用版本 | kinwie 整理的 SFZ／FLAC 版本 |
| 授權 | Creative Commons Attribution 3.0 Unported，CC BY 3.0 |
| 原始來源 | [sfzinstruments/SalamanderGrandPiano](https://github.com/sfzinstruments/SalamanderGrandPiano) |
| 固定來源版本 | `3382bf9496bba2486f5ab0de55a264d1dfc38404` |

本專案選取 v10 的 30 個採樣，使用原起音偏移、抗混疊降採樣至 24 kHz 立體聲、共同增益調整、尾音裁切／淡出及 PCM16 WAV 轉換。衍生素材仍採 CC BY 3.0；不表示原作者為本 APP 背書。

- [完整授權](app/src/main/assets/piano/LICENSE.txt)
- [作者與修改聲明](app/src/main/assets/piano/ATTRIBUTION.txt)
- [來源與每檔雜湊](app/src/main/assets/piano/instrument.json)
- [CC BY 3.0 說明](https://creativecommons.org/licenses/by/3.0/)

## 2. VSCO 2 Community Edition — Upright Piano

| 項目 | 內容 |
|---|---|
| 原採樣作者 | Simon Dalzell／Ivy Audio，2015–16 年冬季 |
| 散布者 | Versilian Studios LLC／Sam Gossner |
| 授權 | CC0 1.0 Universal |
| 原始來源 | [sgossner/VSCO-2-CE](https://github.com/sgossner/VSCO-2-CE) |
| 固定來源版本 | `440300901dfe9275fd84e0b7763af1f8443ae62e` |

本專案選取 dyn2／rr1 的 23 個採樣，依官方音高表映射、修整起音、轉為 24 kHz 立體聲、共同增益調整及尾音淡出。保留作者與散布者來源說明。

- [完整授權](app/src/main/assets/upright/LICENSE.txt)
- [作者與修改聲明](app/src/main/assets/upright/ATTRIBUTION.txt)
- [原素材說明](app/src/main/assets/upright/SOURCE_INFO.txt)
- [原 README](app/src/main/assets/upright/SOURCE_README.txt)
- [來源與每檔雜湊](app/src/main/assets/upright/instrument.json)
- [CC0 1.0 說明](https://creativecommons.org/publicdomain/zero/1.0/)

## 3. Gradle Wrapper

`gradlew`、`gradlew.bat` 與 `gradle/wrapper/gradle-wrapper.jar` 為 Gradle 提供的建置啟動元件，採 Apache License 2.0。啟動腳本中的原著作權與 SPDX 聲明保留不變。

- [Apache-2.0 完整授權](docs/licenses/Apache-2.0.txt)
- [Gradle Wrapper 文件](https://docs.gradle.org/8.13/userguide/gradle_wrapper.html)
- [Gradle 專案](https://github.com/gradle/gradle)

## 4. 開發與測試相依套件

以下由套件管理工具下載，未將套件原始檔或 Python 環境複製進檔案庫：

| 套件 | 用途 | 上游授權／來源 |
|---|---|---|
| Android Gradle Plugin | Android 建置 | [Android 工具原始碼](https://android.googlesource.com/platform/tools/base/)，Apache-2.0 |
| JUnit 4 | 單元測試 | [JUnit 4](https://github.com/junit-team/junit4)，EPL-1.0 |
| Robolectric | Android 框架回歸測試 | [Robolectric](https://github.com/robolectric/robolectric)，MIT |
| NumPy | 離線採樣處理／量測 | [NumPy](https://github.com/numpy/numpy)，BSD-3-Clause |
| SciPy | 降採樣與濾波／擬合 | [SciPy](https://github.com/scipy/scipy)，BSD-3-Clause |
| SoundFile | 音訊檔解碼／寫入 | [python-soundfile](https://github.com/bastibe/python-soundfile)，BSD-3-Clause；底層 libsndfile 另採 LGPL |

打包或重新散布上述工具及其轉接套件時，應依實際取得版本的授權處理。一般 APP 建置不會把這些測試與 Python 套件打包進 APK。

## 5. 樂譜與原創資源

- `examples/` 的小型測試樂譜與測試中的自製譜例屬本專案原創示例，採 MIT。
- 內建小星星示範使用公有領域旋律，程式中的簡單配置由本專案實作。
- 外部舒曼測試附件未納入本檔案庫。
- 鋼琴鍵圖示及單色圖層為本專案原創資源，採 MIT。

APP 設定頁「音色來源與授權」亦可查看目前所選採樣的來源與完整授權。

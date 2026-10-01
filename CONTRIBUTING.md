# 貢獻指南

感謝你協助改善 mini 鋼琴。開始前請閱讀 [專案說明](docs/PROJECT_OVERVIEW.md)、[系統架構](docs/ARCHITECTURE.md) 與 [建置指南](docs/BUILD_AND_TEST.md)。

## 回報問題

請透過 [GitHub Issues](https://github.com/mark216tw/android-minipiano-01/issues) 提供：

1. APP 版本、手機型號、Android 版本。
2. 重現步驟、預期結果及實際結果。
3. 音訊輸出方式：內建喇叭、有線耳機或藍牙。
4. 與問題相關的錯誤堆疊、畫面或最小樂譜範例。
5. MusicXML 問題的原小節編號、記號及播放順序。

## 開發流程

1. Fork 檔案庫並建立用途明確的分支。
2. 依現有 Java／原生 View 架構修改，維持繁體中文介面與文件。
3. 保持音訊執行緒的即時處理特性，檔案讀取及解碼放在背景。
4. 音樂解析或播放邏輯變更，加入能驗證音樂意義的回歸案例。
5. 更新相關文件與 [版本紀錄](CHANGELOG.md)。
6. 執行檢查並提交 Pull Request，說明功能、驗證方式及實機結果。

## 提交前檢查

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug "-PtestJavaHome=C:/path/to/jdk-21"
python tools/verify_piano_apk.py
python tools/check_docs.py
git diff --check
```

未提供外部舒曼附件時，附件測試會略過；請在 PR 註明測試執行範圍。若修改音色或音量補償，請附上音訊量測與手機比較結果。

## 編碼與提交原則

- 新元件採用途明確的名稱，與現有元件責任一致。
- 避免在音訊迴圈配置物件、讀檔或等待耗時鎖定。
- 尊重樂器、聲部、譜表及時間範圍，避免以音高猜測匯入樂譜的左右手。
- 分開處理原譜資料與演奏資料，勿讓量化覆寫原始演奏。
- 提交訊息簡潔描述完成的工作，例如「修正跨譜表延音恢復」或「補充建置文件」。
- 原始碼與文件採 UTF-8；行尾依 `.gitattributes`，原第三方檔案保留原格式。

## 授權

提交自行撰寫的程式碼與文件時，請確認可依 [MIT License](LICENSE) 提供。加入第三方素材時，保留作者、來源、完整授權及修改說明，並更新 [第三方聲明](THIRD_PARTY_NOTICES.md)。

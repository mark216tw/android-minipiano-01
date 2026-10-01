# 專案文件首頁

本文件集以 **mini 鋼琴 1.0.0-prerelease** 的實作為準；此為 Pre-release 測試發行版，不是正式上線版本。主要內容使用繁體中文；第三方授權原文、程式識別名稱及量測 JSON 保留原格式。

## 建議閱讀順序

### APP 使用者

1. [專案說明](PROJECT_OVERVIEW.md)：了解功能與範圍。
2. [使用指南](USER_GUIDE.md)：安裝與日常操作。
3. [MusicXML 支援](MUSICXML_SUPPORT.md)：確認樂譜可播放的內容。
4. [疑難排解](TROUBLESHOOTING.md)：處理匯入、音訊或設定問題。

### 開發者

1. [建置與測試](BUILD_AND_TEST.md)：準備環境與執行檢查。
2. [系統架構](ARCHITECTURE.md)：認識模組及執行緒。
3. [系統設計](SYSTEM_DESIGN.md)：理解狀態、設計決策與取捨。
4. [技術參考](TECHNICAL_REFERENCE.md)：音訊、外觀與工具細節。
5. [資料格式](DATA_FORMATS.md)：保存格式及相容性。
6. [MusicXML 支援](MUSICXML_SUPPORT.md)：解析與演奏解讀規則。
7. [響度量測](audio/README.md)：音色補償方法與數據。

## 根目錄文件

- [專案首頁](../README.md)
- [版本紀錄](../CHANGELOG.md)
- [貢獻指南](../CONTRIBUTING.md)
- [MIT License](../LICENSE)
- [MIT 繁體中文譯文](../LICENSE.zh-TW.md)
- [第三方素材與授權](../THIRD_PARTY_NOTICES.md)

## 文件維護

功能變更時，同步更新相關文件、範例及回歸測試。版本號以 `app/build.gradle` 為準，支援能力以實作與測試為準。框架／離線量測結果與手機實測結果應分別記錄。

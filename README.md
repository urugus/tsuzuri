# 綴り (Tsuzuri) — AI Diary for Android

AIと会話しながら1日単位の日記を作る Android ネイティブアプリ。日記は「完成文」ではなく
**出来事(イベント)ベース** でローカルの **Markdown(vault)** に蓄積し、要求時にAIが
都度 **再構成** して提示する（Obsidian 流のローカルファースト設計）。

アプリ名「綴り」は「日記を綴る／断片を綴じる」に由来。

> 詳細な設計・フェーズ計画は `/Users/urugus/.claude/plans/ai-android-cuddly-glade.md` を参照。

## 現在のステータス

- **Phase 0（プロジェクト雛形）**: 完了 ✅（`assembleDebug` / `testDebugUnitTest` 成功を確認）
  - Compose + Material 3 + Hilt + Room + Navigation の空アプリ
  - 下部ナビ3画面（会話 / ふり返り / 設定）のプレースホルダ
  - Android SDK は `~/Library/Android/sdk`（cmdline-tools 経由で導入済み）
- **Phase 1 ①（データモデル & Markdown⇄Event, lossless）**: 完了 ✅（テスト17件パス、Codec静的レビュー2巡反映済み）
  - `core/model`: `Event` / `EventSource` / `DiaryDay`（Android非依存・JVMテスト可能、id はフルUUID）
  - `data/markdown`: `DiaryDocument` — **ロスレス文書モデル**。`render()` は未編集領域をバイト単位で原文再現、`upsertEvent`/`removeEvent` で id一致イベントのみパッチ。手書き散文/画像/未知frontmatter/コードフェンスを破壊しない
  - イベント判定は厳格（`## ` 直下に `tsuzuri:event` メタ行がある場合のみ）、時刻はメタ `time=`（秒精度保持）、メタ値はパーセントエンコード、本文中 `## ` は `\## ` 退避で round-trip 安全、追記セパレータは末尾走査で O(1)
- **Phase 1 ②（Vault フォルダ I/O, SAF）**: 完了 ✅（リポジトリテスト6件パス、APKビルド可）
  - `data/vault`: `VaultPaths`（`YYYY-MM-DD.md`⇄date）/ `VaultStorage`(IF) / `SafVaultStorage`(DocumentFile) / `FakeVaultStorage`(test) / `DiaryRepository`（loadDay/saveDay/upsertEvent/removeEvent/listDays）/ `VaultLocationStore`(Uri永続化) / `VaultManager`(Hilt)
  - `feature/settings`: SAF（ACTION_OPEN_DOCUMENT_TREE）でvaultフォルダ選択・永続権限取得・日記ファイル数表示
  - イベント順は文書（挿入）順を保持（時刻ソートは表示層の責務）
- **Phase 1 ③（チャットUI骨格）**: 完了 ✅（Stubテスト2件パス、APKビルド可）
  - `core/llm`: `LlmProvider`(IF) / `ChatMessage` / `StubLlmProvider`（ダミー：固定質問＋素朴な出来事抽出、Clock注入で決定的）
  - `feature/chat`: `ChatViewModel` + `ChatScreen`（会話→AI応答→「保存」で当日vaultへupsert）。Vault未設定の案内、スナックバー通知
  - `di/AppModule`: `LlmProvider`→`StubLlmProvider` バインド、`Clock` 提供
  - 次: ④ オンデバイスLLM接続（MediaPipe/LiteRT-LM、`LlmProvider` 差し替え）→ ⑤ 再構成表示 → ⑥ ふり返りタイムラインUI

## 技術スタック

- Kotlin 2.0 / Jetpack Compose / Material 3
- Hilt（DI）、Room（派生インデックス）、Coroutines/Flow
- 設計: MVVM + 単方向データフロー
- LLM: オンデバイス既定（MediaPipe LLM Inference 予定、Phase 1）＋プロバイダ抽象化
- パッケージ: `com.urugus.tsuzuri`

## ビルド前提

- JDK 17（このリポジトリは Java 21 でも可）
- **Android SDK が必要**（compileSdk 35）。`local.properties` に `sdk.dir` を設定すること。
- 初回ビルド:

  ```sh
  ./gradlew :app:assembleDebug      # APK ビルド
  ./gradlew :app:testDebugUnitTest  # ユニットテスト
  ```

## ロードマップ（要約）

| Phase | 内容 |
|---|---|
| 0 | プロジェクト雛形（完了） |
| 1 (MVP) | ローカルMarkdown日記 ＋ AIテキストチャット記録 ＋ 再構成 ＋ ふり返りUI |
| 2 | LLMプロバイダ切替（Gemini Nano / BYOKクラウド） |
| 3 | Google Calendar 連携 |
| 4 | 位置情報（端末GPS / 手動チェックイン） |
| 5 | 音声入力（STT/TTS） |
| 6 | クラウド同期（GitHub 等） |

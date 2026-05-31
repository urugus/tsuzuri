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
- **Phase 1 ③〜⑥**: 完了 ✅ — **MVP コアループ一周**（会話→保存→ふり返り→再構成）。テスト28件パス、APKビルド可
  - ③ `core/llm`(LlmProvider/ChatMessage/StubLlmProvider) + `feature/chat`(Chat画面)：会話→出来事抽出→当日vaultへupsert
  - ④ オンデバイスLLM接続：`MediaPipeLlmProvider`(MediaPipe LLM Inference) / `ModelStore`(.task をSAF取込) / `LlmSettings` / `RoutingLlmProvider`(設定+モデル有無でStub⇄オンデバイス切替、フォールバック)。設定画面でモデル読込・トグル
  - ⑤ 再構成：`LlmProvider.reconstruct`、日詳細で「この日を再構成」
  - ⑥ ふり返り：`feature/timeline`(日付一覧) → `feature/diary`(出来事一覧/再構成/生Markdown表示)
- **Phase 2（LLM品質とプロバイダ切替）**: 着手中
  - プロンプトを `LlmPromptBuilder` に分離（質問生成 / 出来事抽出 / 再構成）
  - `LlmProviderMode` を追加し、設定画面を「簡易 / 端末内 / クラウド」の選択式へ変更
  - クラウドAIは設定上の受け皿のみ準備。実Provider/APIキー管理は次ステップ

## MVP の使い方（実機/エミュレータ）
1. 「設定」タブで Vault フォルダを選ぶ（クラウド同期フォルダ可）
2. 「会話」タブで出来事を入力 → 「保存」で当日 `YYYY-MM-DD.md` に追記
3. 「ふり返り」タブで日付を開き、出来事一覧・再構成・生Markdownを確認
4. （任意）「設定」でオンデバイスモデル(.task)を読み込み、トグルONで端末内AIに切替
   - 未読込時は簡易モード（定型）でオフライン動作。実推論は対応端末＋モデルが必要

## 技術スタック

- Kotlin 2.0 / Jetpack Compose / Material 3
- Hilt（DI）、Room（派生インデックス）、Coroutines/Flow
- 設計: MVVM + 単方向データフロー
- LLM: `LlmProvider` 抽象（既定Stub、設定でオンデバイス MediaPipe LLM Inference に切替）
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
| 0 | プロジェクト雛形（完了 ✅） |
| 1 (MVP) | ローカルMarkdown日記 ＋ AIチャット記録 ＋ 再構成 ＋ ふり返りUI（完了 ✅） |
| 2 | LLM強化（プロンプト分離・プロバイダ選択は着手中 / Gemini Nano / BYOKクラウド、実機での実モデル検証） |
| 3 | Google Calendar 連携 |
| 4 | 位置情報（端末GPS / 手動チェックイン） |
| 5 | 音声入力（STT/TTS） |
| 6 | クラウド同期（GitHub 等） |

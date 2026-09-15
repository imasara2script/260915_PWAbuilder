# Project Plan

An Android app for creating PWAs on a smartphone. The app uses Gemini API (agents) for code editing. The created PWAs can be installed on the device and receive images via the share menu from other apps for editing and other tasks.

## Project Brief

# プロジェクト概要: PWA Builder

スマートフォン上でPWA（Progressive Web Apps）を作成・管理するためのAndroidアプリケーションです。Gemini APIを活用したインテリジェントなコード編集機能と、デバイスの共有機能を活用したシームレスなワークフローを提供し、モバイルファーストな開発体験を実現します。

## 機能 (Features)

1.  **AIアシストによるPWAコード生成**: Gemini API（エージェント）を利用し、自然言語による指示でPWAの構造やロジックを自動生成・編集します。
2.  **PWAプレビューとインストール**: 作成したWebアプリを即座にプレビューし、Androidの標準機能を利用してホーム画面へPWAとしてインストールできます。
3.  **共有メニュー経由の画像受信**: 他のアプリから画像が共有された際、それを受け取ってPWA内での編集やタスク（AIによる画像解析など）に直接利用できます。
4.  **レスポンシブなプロジェクト管理**: モバイルからタブレットまで対応したUIで、複数のPWAプロジェクトを効率的に管理できます。

## 高レベル技術スタック (High-Level Tech Stack)

*   **言語**: Kotlin
*   **UIフレームワーク**: Jetpack Compose
*   **ナビゲーション**: **Jetpack Navigation 3** (State-driven)
*   **アダプティブ・レイアウト**: **Compose Material Adaptive Library**
*   **非同期処理**: Kotlin Coroutines
*   **AI連携**: Google Gemini API (Vertex AI SDK for Firebase 等)
*   **Web実行環境**: Android WebView (PWAのレンダリングおよび動作確認用)

---
> [!NOTE]
> 現在のツールセットに `generate_image` が含まれていないため、UI Design Image セクションは省略されています。

## Implementation Steps
**Total Duration:** 53m 56s

### Task_1_ProjectSetupAndGemini: Set up local project storage and integrate Google Gemini API/Vertex AI SDK for Firebase to enable AI-assisted PWA code generation and editing.
- **Status:** COMPLETED
- **Updates:** Gemini API integration and local storage setup completed. Google AI SDK (generativeai) added. PwaStorage for file management and GeminiService for AI prompts implemented. API key setup via local.properties configured.
- **Acceptance Criteria:**
  - Gemini API key integrated securely
  - Gemini API client can send prompts and receive generated PWA code
  - Local storage for PWA projects is implemented
- **Duration:** 44m 23s

### Task_2_PWAExecutionAndPreview: Implement a WebView-based execution and preview environment for the generated PWAs, allowing them to run locally on the device.
- **Status:** COMPLETED
- **Updates:** WebView-based preview environment implemented. Secure local file serving via 'https://pwa.local/' origin using WebViewClient interception. Integrated into the UI with a Preview button and navigation support. JavaScript and DOM storage enabled.
- **Acceptance Criteria:**
  - WebView correctly loads local HTML/JS/CSS PWA assets
  - WebView handles basic PWA capabilities or local serving simulations
- **Duration:** 2m 24s

### Task_3_ShareTargetImageReceiver: Implement Android Intent filters to receive shared images from other apps, processing and feeding them into the PWA environment or Gemini context.
- **Status:** COMPLETED
- **Updates:** Intent filters for ACTION_SEND and ACTION_SEND_MULTIPLE (images) implemented. MainActivity handles incoming intents and passes URIs to PwaViewModel. Shared images are copied to internal cache for secure, persistent access. UI now displays a 'Shared Images' indicator.
- **Acceptance Criteria:**
  - App appears in the system share sheet for images
  - Shared images are successfully received and accessible within the app/PWA context
- **Duration:** 1m 54s

### Task_4_AdaptiveUIAndNavigation: Build the responsive user interface using Jetpack Navigation 3 and Compose Material Adaptive Library for project management, AI code editing, and previews.
- **Status:** COMPLETED
- **Updates:** Adaptive UI implemented using ListDetailSceneStrategy and Navigation 3. Supports single-pane (phone) and side-by-side (tablet/foldable) layouts. Type-safe destinations (Dashboard, AI Editor, Preview) integrated. Maintained shared image handling. Edge-to-edge and Material 3 styles applied.
- **Acceptance Criteria:**
  - Responsive UI adapting across phone/tablet layouts
  - Navigation 3 correctly handles screen transitions between project list, editor, and preview
- **Duration:** 5m 15s

### Task_5_RunAndVerify: Final end-to-end integration run. Instruct critic_agent to verify application stability, confirm alignment with user requirements, and check UI issues.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Build passes successfully
  - App does not crash during end-to-end usage
  - All existing tests pass
  - Verify Gemini code generation, WebView preview, and image sharing work seamlessly
- **StartTime:** 2026-09-14 18:33:01 JST


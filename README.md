# ソブリン4/20 — Android自動操作・第一波プロトタイプ

FNaF 1 Android版の **最初の約5秒の操作** を検証する試作アプリです。GitHub ActionsでデバッグAPKをビルドできます。

**ビルド成功:** [GitHub Actions #2（2026-10-10 JST）](https://github.com/tara-sooo/FNaF/actions/runs/37969133141) — `SovereignFirstWave-debug-apk` に `app-debug.apk` を保存済み。

## APKを入手する

1. [成功済みActions実行](https://github.com/tara-sooo/FNaF/actions/runs/37969133141)を開く。
2. ページ下部の **Artifacts → SovereignFirstWave-debug-apk** を選択。
3. ダウンロードしたZIPを展開し、`app-debug.apk` をAndroidへインストールする。

今後の再ビルドは **Actions → Build Android debug APK → Run workflow** で実行できます。ソースを変更してmainへプッシュした場合も自動ビルドされます。

## 試作品の内容

Java製Androidアプリ1つにアクセシビリティサービスを実装しました。ゲーム実行中のADB、Termux、画像認識、ルート権限は使用しません。

- 約0.05秒：モニターUP
- 約0.60秒：CAM 4Bを選択
- 約0.95秒：モニターDOWN
- 約3.45秒：左へパン
- 約3.883秒：モニターUP
- 約4.417秒：モニターDOWN
- 約4.483秒：左ドア閉鎖
- 約4.517秒：右へパン
- 約4.767秒：右ドア閉鎖

**まだ実装していないもの:** 初回のドア再開放、2波目以降、ゲーム開始時刻との自動同期、4/20の全夜クリア。現段階はAndroidのタッチ注入と座標を試すためのものです。


## v0.2：音量＋が効かないとき

[v0.2 ビルド成功済み（Actions）](https://github.com/tara-sooo/FNaF/actions/runs/37970774409)。Artifacts の `SovereignFirstWave-debug-apk` から最新版の `app-debug.apk` を取得できます。

旧版をインストール済みなら、GitHub Actionsのデバッグ署名が変わって上書きに失敗することがあります。その場合、**旧版をアンインストールしてから**新版を入れ、アクセシビリティを再度有効にしてください。

1. Android設定で **Sovereign First Wave** アクセシビリティを有効化し、アプリに **サービス：接続済み** と表示されるか確認
2. アプリ内の **実行を許可** をチェック
3. **5秒後にモニターを1回タップ** ボタンを押し、5秒以内にFNaF 1へ切り替える
4. 終了後アプリへ戻り **動作状態を更新** でログを確認する
5. 単発が動けば **5秒後に最初の9操作を開始** で一連の入力を確認する

`dispatchGesture受理` や `完了通知` が出るのにゲームが動かなければ座標設定を調整します。`サービス未接続` ならアクセシビリティの設定、`実行許可OFF` ならチェックを確認します。音量＋キーが届いているかどうかも動作ログに表示されます。

## スマホでの操作

1. `app-debug.apk` をインストールして起動。
2. Androidのアクセシビリティ設定で **Sovereign First Wave** を有効にする。Android 13以降で制限された設定と表示されたら、アプリ情報画面から明示的に許可する。
3. アプリ内でタップ座標と画面矩形を設定・保存し、`音量＋で実行を許可` をオンにする。
4. FNaF 1の警備室へ移動して、**音量＋**を押すと一連の操作を開始する。**音量−**で中断する。

`音量＋` を押した時刻を0秒としています。**ゲーム内部の開始タイマーと自動同期するわけではありません。** 初回の位置合わせ・入力確認用途で使ってください。

## 開発

Android Studioから `app/` とGradleファイルをそのまま読み込めます。JDK 17、Android SDK 35、Gradle 8.11.1、Android Gradle Plugin 8.9.2でActionsビルドを確認済み。

ゲーム本体のAPKやアセットはこのリポジトリに含めていません。

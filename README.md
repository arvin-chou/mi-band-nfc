# Mi Band NFC Manager

Manage NFC cards on your Xiaomi Smart Band 8 — switch between door access, transit, and payment cards with one tap.

管理小米手環 8 上的 NFC 卡片 — 一鍵切換門禁卡、交通卡、支付卡。

<p align="center">
  <a href="https://github.com/arvin-chou/mi-band-nfc/releases/latest"><img src="https://img.shields.io/github/v/release/arvin-chou/mi-band-nfc?style=for-the-badge" alt="Release"></a>
  <a href="https://github.com/arvin-chou/mi-band-nfc/blob/main/LICENSE"><img src="https://img.shields.io/github/license/arvin-chou/mi-band-nfc?style=for-the-badge" alt="License"></a>
  <a href="https://ko-fi.com/arvinchou"><img src="https://img.shields.io/badge/Ko--fi-Support%20this%20project-ff5e5b?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
</p>

---

## Features | 功能

| | English | 中文 |
|---|---|---|
| 📋 | View all NFC cards on your band | 查看手環上所有 NFC 卡片 |
| ⚡ | One-tap card switching | 一鍵切換預設卡片 |
| ⏰ | Auto-switch rules by time & day | 按時間/星期自動切換規則 |
| 🎨 | Material Design 3 UI | Material Design 3 介面 |
| 🔒 | BLE direct communication — no cloud | BLE 直連手環，無需雲端 |
| 📖 | Free & open source | 免費且開源 |

## Requirements | 需求

- Android 8.0+ (API 26)
- Xiaomi Smart Band 8 NFC edition | 小米手環 8 NFC 版
- Auth Key (see below) | 認證金鑰（見下方說明）

## Getting Started | 開始使用

### 1. Install | 安裝

Download the latest APK from [Releases](https://github.com/arvin-chou/mi-band-nfc/releases/latest), or install from Google Play (coming soon).

從 [Releases](https://github.com/arvin-chou/mi-band-nfc/releases/latest) 下載最新 APK，或從 Google Play 安裝（即將上架）。

### 2. Get your Auth Key | 取得認證金鑰

The Auth Key is a 16-byte hex string required to authenticate with your band via BLE.

Auth Key 是一組 16 位元組的十六進位字串，用於透過 BLE 與手環認證。

**Method | 方法：** Extract from [Notify for Xiaomi](https://play.google.com/store/apps/details?id=com.mc.xiaomi1) app database (requires root).

從 [Notify for Xiaomi](https://play.google.com/store/apps/details?id=com.mc.xiaomi1) 的資料庫中提取（需要 root）。

```bash
# On rooted device | 在已 root 的裝置上
adb shell su -c "cat /data/data/com.mc.xiaomi1/databases/notify_data.db" > notify_data.db
sqlite3 notify_data.db "SELECT hex(profile) FROM profile WHERE key='auth_key'"
```

### 3. Configure | 設定

1. Open the app → Settings tab | 開啟 App → 設定分頁
2. Enter your band's MAC address | 輸入手環 MAC 地址
3. Enter the Auth Key (32 hex chars) | 輸入 Auth Key（32 個十六進位字元）
4. Go to Home → tap "Connect" | 回到首頁 → 點擊「連接」

## Architecture | 架構

```
┌─────────────┐     BLE (FE95)     ┌──────────────────┐
│  Android App │ ◄──── 0051 ────► │ Xiaomi Smart Band │
│              │   Channel Proto   │   8 NFC (SE)      │
└──────┬───────┘                   └──────────────────┘
       │
  ┌────┴────────────────────────────┐
  │  Compose UI                     │
  │  ├─ HomeScreen (quick switch)   │
  │  ├─ CardsScreen (manage)        │
  │  └─ SettingsScreen (config)     │
  │                                 │
  │  BLE Layer                      │
  │  ├─ XiaomiBleUuids (FE95)      │
  │  ├─ ChunkedTransfer (framing)   │
  │  ├─ XiaomiAuth (AES-ECB)       │
  │  └─ NfcProto (protobuf)        │
  │                                 │
  │  Data Layer                     │
  │  ├─ Room (cards, rules)         │
  │  ├─ DataStore (prefs)           │
  │  └─ WorkManager (auto-switch)   │
  └─────────────────────────────────┘
```

### BLE Protocol | BLE 協議

Communication with the band uses Xiaomi's proprietary BLE protocol over the `FE95` GATT service:

與手環的通訊使用小米私有 BLE 協議，透過 `FE95` GATT service：

| Component | Detail |
|---|---|
| Service | `0000FE95-0000-1000-8000-00805F9B34FB` |
| Command Channel | Characteristic `0051` (write + notify) |
| Auth | Module 128, AES/ECB with 16-byte Auth Key |
| NFC | Module 36, protobuf `command_type=5` |
| Framing | Chunked transfer with handle + sequence numbers |

## Tech Stack | 技術棧

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **DI:** Hilt
- **Database:** Room
- **Preferences:** DataStore
- **Background:** WorkManager
- **BLE:** Android BluetoothGatt API
- **Ads:** Google AdMob (removable via supporter mode)

## Build | 建置

```bash
# Clone
git clone https://github.com/arvin-chou/mi-band-nfc.git
cd mi-band-nfc

# Create local.properties with your Android SDK path
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires release.jks)
./gradlew assembleRelease
```

## Support | 支持

This app is free and open source. If you find it useful:

此 App 免費且開源。如果覺得有用：

- ☕ [Ko-fi](https://ko-fi.com/arvinchou) — Buy me a coffee | 請我喝杯咖啡
- ⭐ Star this repo | 給個星星

Supporters can remove ads in Settings → "Remove Ads".

贊助者可在設定 →「移除廣告」關閉廣告。

## Privacy | 隱私

See [Privacy Policy](PRIVACY_POLICY.md). All data stays on your device. No tracking, no cloud.

詳見[隱私權政策](PRIVACY_POLICY.md)。所有資料留在裝置上，無追蹤、無雲端。

## License

MIT License — see [LICENSE](LICENSE) for details.

## Disclaimer | 免責聲明

This project is not affiliated with, endorsed by, or associated with Xiaomi. "Xiaomi" and "Mi Band" are trademarks of Xiaomi Inc. Use at your own risk.

本專案與小米公司無任何關聯。「小米」和「小米手環」是小米公司的商標。使用風險自負。

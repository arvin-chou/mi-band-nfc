# Privacy Policy — Mi Band NFC Manager

**Last updated:** March 9, 2026

## Overview

Mi Band NFC Manager ("the App") is an open-source Android application that manages NFC cards on Xiaomi Smart Band devices via Bluetooth Low Energy (BLE). Your privacy is important to us.

## Data Collection

### Data stored locally on your device

| Data | Purpose | Stored where |
|---|---|---|
| Band MAC address | Connect to your specific band | App preferences (on-device) |
| Authentication key | Authenticate with your band | App preferences (on-device) |
| NFC card info (AID, name, type) | Display and manage your cards | Local SQLite database |
| Auto-switch rules | Schedule card switching | Local SQLite database |
| Supporter status | Hide/show advertisements | App preferences (on-device) |

**All of the above data stays on your device.** We do not collect, transmit, or store any of this data on external servers.

### Data collected by third-party SDKs

#### Google AdMob

The App uses Google AdMob to display banner advertisements. AdMob may collect:

- Device identifiers (Advertising ID)
- IP address
- General location (country/region level)
- App usage data (ad interaction)

This data is collected and processed by Google according to [Google's Privacy Policy](https://policies.google.com/privacy). You can opt out of personalized ads in your device's Google Settings.

**Supporters who enable "Remove Ads" will not see any ads, and the AdMob SDK will not load advertisements.**

## Bluetooth & Permissions

The App requires Bluetooth permissions to communicate with your Xiaomi Smart Band. This communication happens directly between your phone and band over BLE. No Bluetooth data is transmitted to the internet.

| Permission | Purpose |
|---|---|
| BLUETOOTH_SCAN | Discover nearby Xiaomi Smart Band devices |
| BLUETOOTH_CONNECT | Establish BLE connection to your band |
| ACCESS_FINE_LOCATION | Required by Android for BLE scanning (pre-Android 12) |
| INTERNET | Load advertisements (AdMob) and check for updates |

## Data Sharing

We do **not** sell, trade, or transfer your personal data to third parties, except as described in the AdMob section above.

## Data Security

All sensitive data (authentication key, MAC address) is stored locally using Android's DataStore with app-private storage, accessible only to this App.

## Children's Privacy

This App is not directed at children under 13. We do not knowingly collect personal information from children.

## Open Source

This App is open source. You can review the complete source code at [GitHub](https://github.com/arvin-chou/mi-band-nfc) to verify our privacy practices.

## Changes to This Policy

We may update this Privacy Policy from time to time. Changes will be posted in the App's GitHub repository with an updated date.

## Contact

If you have questions about this Privacy Policy, please open an issue on our [GitHub repository](https://github.com/arvin-chou/mi-band-nfc/issues).

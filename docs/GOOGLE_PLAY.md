# Publishing on Google Play

This app sells its plans through **Google Play Billing (Play Billing Library 8)**. The app
shows the prices it gets from Google Play. The prices below are only suggestions to enter in
Play Console.

## 1. Plans and feature limits

| Plan | Product ID (type) | Suggested price (IDR) | Suggested price (USD) |
|---|---|---|---|
| Free | – | Free | Free |
| Basic | `ndicam_basic` (subscription) | Rp29.000 / month, Rp249.000 / year | $1.99 / $16.99 |
| Pro | `ndicam_pro` (subscription) | Rp69.000 / month, Rp599.000 / year | $4.49 / $39.99 |
| Ultra | `ndicam_ultra` (subscription) | Rp119.000 / month, Rp999.000 / year | $7.99 / $69.99 |
| Max | `ndicam_max_lifetime` (one-time product) | Rp1.499.000 one time | $99.99 |

Each plan includes everything in the plan below it. The rules are defined in
`app/src/main/java/com/ndicamera/professional/billing/Plans.kt`.

| Feature | Free | Basic | Pro | Ultra / Max |
|---|---|---|---|---|
| Maximum resolution / frame rate | 720p30 | 1080p30 | 1080p60 | 4K60 (if the camera supports it) |
| Session length | 15 minutes | Unlimited | Unlimited | Unlimited |
| Watermark on the NDI output | Yes | No | No | No |
| Manual ISO / shutter / EV / AE lock | – | ✓ | ✓ | ✓ |
| Manual focus, focus lock, manual WB (Kelvin/tint) | – | – | ✓ | ✓ |
| Zebra, peaking, histogram, safe areas | – | – | ✓ | ✓ |
| Audio | Mono, internal mic | Stereo | + USB / external | + A/V offset |
| Custom NDI source name | – | ✓ | ✓ | ✓ |
| Background / screen-off streaming | – | ✓ | ✓ | ✓ |
| All lenses (ultra-wide, tele, physical) | Main front/rear only | Main only | ✓ | ✓ |
| NDI groups, tally | – | – | – | ✓ |

## 2. Play Console setup

1. **Create the app** with package `com.ndicamera.professional`. Debug builds use `.debug` and cannot be published.
2. **Upload a signed AAB to the Internal testing track.** Billing products can only be created after a bundle containing the billing library has been uploaded.
   - Set up the release keystore with these repository secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
   - CI then builds `NDICameraPro-NDI-release-play.aab`.
   - Its versionCode is the GitHub Actions run number.
   - Use **Play App Signing**.
3. **Monetize › Products › Subscriptions.** Create `ndicam_basic`, `ndicam_pro` and `ndicam_ultra`.
   - Give each one two auto-renewing base plans: `monthly` (P1M) and `yearly` (P1Y).
   - The app shows every active base plan. A free-trial offer is optional and is shown automatically.
4. **Monetize › Products › In-app products.** Create `ndicam_max_lifetime` as a one-time, non-consumable product.
5. **Settings › License testing.** Add tester Gmail accounts so they can buy without being charged.
6. Install the app from the internal-testing link. Billing only works for apps installed from Google Play with the same signing key.

## 3. App content declarations

- **Foreground service types:** `camera` and `microphone`.
  - Justification: "Streams live camera video and microphone audio to NDI receivers on the local network while the user has started a stream. A persistent notification with a Stop action is shown."
  - Provide a short video of starting a stream, then locking the screen.
- **Permissions:** CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS, plus network and Wi-Fi multicast (needed for NDI mDNS discovery).
- **Data safety:**
  - The app does not collect or share personal data.
  - Video and audio are sent only over the local network to the NDI receivers the user chooses.
  - Purchases are handled by Google Play.
  - Answer "No data collected".
- **Privacy policy URL:** publish `docs/PRIVACY_POLICY.md`, for example with GitHub Pages or any public page.
- **Target audience:** 18+ / professionals. Content rating questionnaire: utility, no user-generated content.
- **Target API:** the app targets API 36 (Android 16), as Google Play has required for new apps and updates since 31 August 2026.
- **16 KB page size:** native code is built with `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES`. CI checks the LOAD alignment of the 64-bit libraries, including NDI's `libndi.so`, and warns when a library is below 16 KB.

## 4. Trademark

The product name contains "NDI". NDI's brand guidelines require **written approval from NDI (Vizrt NDI AB)** before you use "NDI" in an app name on a store. Apply through ndi.video before your production release, or choose a name without "NDI" (for example "… Camera for NDI®", if NDI allows that form). The About screen shows the required trademark notice.

## 5. Known limitations

Entitlements are checked on the device only, against Google Play's purchase records. There is no server-side receipt validation (Play Developer API / RTDN), so a rooted device can bypass the plan checks. For stronger protection, add a backend that verifies purchase tokens.

## 6. Uploading from GitHub Actions

The **Android CI** workflow can upload the signed AAB to Google Play. It always creates a
**draft** release, so nothing reaches users until you roll it out in Play Console.

1. **Upload the first bundle by hand.** Google Play only accepts API uploads for an app that
   already has one bundle. Download `NDICameraPro-NDI-release-play.aab` from the `ndi-apk`
   artifact of a signed CI run and upload it in Play Console › Testing › Internal testing.
2. **Create a service account.**
   - In Google Cloud Console, create a project (or reuse one) and enable the
     *Google Play Android Developer API*.
   - Create a service account and download a JSON key for it.
   - In Play Console › Users and permissions, invite the service account's email address.
     Give it access to this app with *Release apps to testing tracks* (add *Release to
     production* only if you want to upload production drafts).
3. **Add the repository secret** `PLAY_SERVICE_ACCOUNT_JSON` with the full contents of the JSON key.
4. **Run the workflow.** Actions › Android CI › Run workflow, choose `main` and a `play_track`
   (`internal` first). The `Upload AAB to Google Play (draft)` job uploads the bundle.
5. In Play Console, open the draft release, add release notes and roll it out.

# aKlima

<img src="assets/icon.png" width="104" align="right" alt="aKlima icon">

**A lightweight replacement for the ConnectLife / Hisense app.** Control a Hisense split air
conditioner from your phone — the same cloud the vendor app uses, without the vendor app.

- Nine screens' worth of settings reduced to one: power, temperature, and a `⋯` sheet for the rest
- ~10 MB installed — the ConnectLife app is hundreds of megabytes for the same job
- Android 8.0+ (API 26)
- Works with accounts created **with Google** as well as ones with an email and password
- Two units on one account are one tap apart (`Vogosca` / `Svrake` chips at the top)
- No analytics, no ads, no third-party servers: it talks only to Hisense's cloud

## What it does

- **Power**, **target temperature**, **mode** (Heat / Cool / Dry / Fan / Auto), **fan speed**
  (Auto → Super low → Low → Mid → High → Turbo)
- **Quiet, Swing, Eco, Turbo, Sleep** as switches in the `⋯` sheet
- **Room temperature** from the unit's own sensor, and fault flags if the unit reports one
- Refreshes every 20 s while it is in the foreground, and re-reads the real state after every command
- Keeps its session: you sign in once per phone

## Sign in

Two routes, because ConnectLife accounts are created two different ways.

**Email + password accounts:** type them into the app. They go straight to ConnectLife's own login
endpoint (the password is MD5'd and RSA-encrypted exactly the way their web page does it). Nothing is
stored on the phone, nothing passes through anyone else.

**Google-linked accounts:** Google refuses to run its sign-in inside another app's embedded page, and
ConnectLife's return URL is fixed to a host that cannot resolve — so the sign-in happens once in your
normal browser and the app receives the code from it:

1. aKlima → **Sign in** → **Sign in with Google (opens a browser)**
2. **Open in browser** → **SIGN IN BY GOOGLE**
3. You land on a page that cannot load (`homeassistant.local`) — expected
4. **Copy** that address from the address bar (long-press it → Copy)
5. Back in aKlima → **Paste code** → paste → done

No browser on the phone? **Copy link**, open it on a laptop, sign in, then send the address to the
phone and paste it in. Each phone keeps its own session, so signing in on one does not sign out
another. `docs/sign-in.html` is the same instructions as a printable sheet.

## How it works

The air conditioner's WiFi module exposes **no local API at all** — a full port scan of the module
finds zero listening sockets — so all control goes through Hisense's cloud, exactly as the vendor app
does. aKlima speaks that cloud API directly:

- OAuth2 against `oauth.hijuconn.com` (or `login_pwd` for password accounts)
- HMAC-SHA256-signed requests to `juapi-3rd.hijuconn.com`
- the OAuth client id/secret are the ones **Hisense's own Home Assistant plugin publishes publicly**,
  so they are not secrets in any meaningful sense
- the only credentials kept on the phone are the OAuth tokens from your own sign-in

## Build

```bash
./gradlew :app:assembleRelease
```

Needs a JDK 17 and an Android SDK (set `sdk.dir` in `local.properties` or `ANDROID_HOME`); every push
to `main` also builds an APK artifact in Actions.

## Limits, honestly

- **Cloud only.** If Hisense's cloud is down or the unit's module is offline, nothing here can reach it.
- **Unofficial.** Not affiliated with Hisense or ConnectLife. It uses the same API their own
  integration uses; a change on their side can break it.
- The cloud reports a *last known* state; a command being accepted is not proof the unit acted on it.
  The app re-reads after each command so what you see self-corrects.
- Tested on the author's two units (split systems, `009` type code). Other appliance types on the same
  account are not handled.

## Layout

```
app/src/main/java/com/example/aklima/
  ConnectLife.kt     OAuth + signed API client, password login, device model
  TokenStore.kt      session storage
  AppViewModel.kt    state, polling, optimistic writes
  BrowserSignIn.kt   the Google/browser route, with the paste-code step
  NativeLogin.kt     the email + password route
  LoginScreen.kt     the in-app page (for phones where it renders), browser launcher
  MainActivity.kt    the single screen + the ⋯ sheet
  CrashLog.kt        crash capture: shows a copyable trace instead of crashing twice
docs/                sign-in sheet + icon sources
```

## License

MIT — see [LICENSE](LICENSE).

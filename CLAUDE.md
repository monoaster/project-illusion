# Installing Project Illusion with Claude

This file tells Claude Code how to put the Project Illusion Home on an Android TV / Google TV. The
person running it only needs the TV on the same Wi‑Fi and the remote in hand. Do the steps in order,
say in plain words what each one does, and stop to ask whenever the TV needs someone to press a button.

## What gets installed

- `launcher/`: a small Android launcher app (`com.projectillusion.home`, under 300 KB). Its Home is a web
  page in `launcher/app/src/main/assets/home/`: greeting, clock, weather, Continue watching, New for you,
  a three-to-five-app dock with the night drive (`nightdrive.js`), and a hold‑OK dial. The Java side
  (`HomeActivity` and a few helpers) only feeds the page what the TV knows and launches what it asks for.
- `themes/project-illusion/config.json`: the owner's choices (name, brand, weather place, dock apps).
- `tools/`: a small ADB-over-Wi‑Fi client (no adb binary needed), a TV finder, and the theme script.
- Nothing on the TV is uninstalled or debloated. The TV's own Home app is only switched off.

**Running commands on the TV.** Every TV command in this file is written `TV_HOST=<ip> tools/tv '...'`.
`tools/tv` runs `tools/tv.py` with the repo's own Python (`tools/venv`, made in step 1); plain
`tools/tv.py` uses the system Python, which lacks its library. Run them from the repo root.

## Steps

1. **Computer tools.** Check for a JDK, version 17 or newer (17 and 21 are tested; the install script
   says which one it uses and stops on an older one), an Android SDK (Android Studio's, or Homebrew's
   `android-commandlinetools` with `platforms;android-34` and `build-tools`), and Python 3.
   Install only what is missing, and ask before installing anything. Then:
   `python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`
   On Windows, run everything from Git Bash (it comes with Git for Windows); the venv's pip is then
   `tools/venv/Scripts/pip`.
2. **Turn on debugging on the TV, then wait.** Walk the person through it, one screen at a time:
   - Settings → System (or Device Preferences) → About → press OK on *Android TV OS build* (or *Build*)
     seven times, until it says "You are now a developer".
   - Back → Developer options → turn on *USB debugging*, and *Network debugging* / *ADB over network*
     if the TV has one.
   - The TV must be on the same Wi‑Fi as this computer.

   Then stop and ask them to tell you when it's done. Don't search for the TV before they confirm:
   until debugging is on, the TV doesn't answer and a scan finds nothing.
3. **Find and connect to the TV.** Once they confirm, run `python3 tools/find_tv.py`. It scans this
   computer's network for devices answering on the debugging port (5555).
   - One address: use it.
   - Several: probe each one (below), and use the one whose model matches their TV.
   - None: ask them to check the TV is on, on the same Wi‑Fi, and that USB debugging is on. Then ask for
     the address from Settings → Network & Internet → the Wi‑Fi network (some TVs: Advanced options →
     Network status, or About → Status).

   Probe: `TV_HOST=<ip> tools/tv --probe`. On a computer the TV hasn't seen, the TV shows *Allow USB
   debugging?*: ask the person to tick *Always allow* and press OK, then probe again. It prints the
   model and Android version; tell them which TV you found. The key: if this computer already has
   `~/.android/adbkey` (from Android Studio or platform-tools), `tv.py` uses that, and a TV that trusted
   it before shows no prompt. Otherwise it creates `tools/adbkey/`, which must never be committed.
4. **Personalise. Always ask; never assume.** The theme ships blank: no name, no weather place, and a
   suggested dock. Ask these as plain questions in a normal message the person answers by typing.
   Don't use a multiple-choice prompt for them: an option list makes "no name" the first choice and
   hides the name behind "Other", which confuses people. Ask:
   - "What name should Home greet you with? (Say none to leave it out.)"
   - "Which city should the weather be for, and °C or °F? (Say none for no weather.)"
   - "Should the top-left corner show the name project_illusion, or leave it out so the tabs start at
     the left edge?" Explain it's only the small label before the home, continue watching and new for
     you tabs. A two-option prompt is fine here.
   - Then the dock: which three to five apps they want (five fills it best). First find the apps they can open from Home:
     `TV_HOST=<ip> tools/tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER' | grep / | cut -d/ -f1 | tr -d ' ' | sort -u`
     gives a short list of launchable TV apps, pre-installed ones included (`pm list packages -3`
     leaves those out, and plain `pm list packages` prints hundreds of system packages). Show them by
     name and offer the suggested five where installed: Netflix (`com.netflix.ninja`), Prime Video
     (`com.amazon.amazonvideo.livingroom`), YouTube (`com.google.android.youtube.tv`), JioHotstar
     (`in.startv.hotstar`), Crunchyroll (`com.crunchyroll.crunchyroid`). A multiple-choice prompt is
     fine here, since they are picking from a list.

   Then edit `themes/project-illusion/config.json`:
   - `greeting_name`: the name, or `""`.
   - `show_brand`: `true` to show project_illusion at the top left, `false` to leave it out.
   - `weather`: `{"latitude": .., "longitude": ..}` for the city (look up its coordinates), plus
     `"fahrenheit": true` for °F. Leave the key out for no weather line.
   - `dock_apps`: three to five package names; `dock_colors`: a brand colour for each (`package=#RRGGBB`).
     The windows share the dock's width and the road runs under all of them. With one or two apps the
     dock shows plain windows.
     Netflix, Prime Video, YouTube, JioHotstar and Crunchyroll get tuned wordmarks and city colours;
     other apps show their label.
5. **Build and install.** Before running it, tell the person that it turns off the TV's own Home
   app (it stays installed, and Undo below brings it back) and wait for their OK.
   `TV_HOST=<ip> launcher/build-and-install.sh`. It builds the APK, installs it, grants it Watch Next
   and playback access, pushes the theme from step 4, starts it, makes it the Home app, and switches on
   the hold-OK dial. The TV goes straight from its own Home to Project Illusion.
   It disables the stock launcher only after the new one has started, and puts the stock one back if
   Home does not end up on the new launcher. Read its output; it explains every failure.
6. **Changing the theme later.** Edit `config.json`, then from the repo root:
   `TV_HOST=<ip> tools/theme.sh themes/project-illusion`. No rebuild needed.
7. **Check.** Ask the person to press Home on the remote. To see it yourself:
   `tools/tv 'screencap -p /data/local/tmp/h.png'`, then `tools/tv --pull /data/local/tmp/h.png h.png`,
   then `tools/tv 'rm /data/local/tmp/h.png'`. Smoothness, with Home on screen and nobody pressing keys:
   `tools/tv 'dumpsys gfxinfo com.projectillusion.home reset >/dev/null; sleep 5; dumpsys gfxinfo com.projectillusion.home' | grep -E "Total frames|Janky frames:"`.
   Read the `Janky frames:` line, not `Janky frames (legacy)`, which counts almost every frame on a TV.
   `Total frames rendered: 0` means Home wasn't on screen (an app was open): press Home and measure again.
   Under 5% is smooth; around 10% shows only while focus moves between dock tiles (the city behind
   is being drawn). A sample taken while someone moves around will read higher.

## On a different TV

Built and tested on one TV: a Sony BRAVIA 4K VH21 (Android 12, Google TV, WebView 156). On others:

- **Screen size and resolution adapt.** The Home is laid out on a 1280×720 stage scaled to the screen,
  and the night drive draws at the screen's real resolution (720p, 1080p and 4K UIs all work). A screen
  that isn't 16:9 gets thin bars at the edges.
- **Android 8 or newer** (the app's minimum). The night drive needs a 2022-or-newer Android System
  WebView (Chrome 99+). With an older one the dock still works, showing plain coloured windows. Tell
  the person, and suggest updating *Android System WebView* from the Play Store. Check the version with
  `tools/tv 'dumpsys package com.google.android.webview | grep -m1 versionName'`.
- **Apps differ.** Pick three to five dock apps from what step 4's list shows. A dock app that isn't
  installed is left out, and the dock re-flows for the ones that are. The road needs at least three. Apps outside the five tuned ones get their label and
  their `dock_colors` colour.
- **Continue watching depends on the apps.** It fills from what apps publish to Android's Watch Next and
  from what the launcher sees playing. Apps and regions differ in how much they publish.
- **The stock launcher.** The install script knows Google TV, Android TV, the older Leanback launcher
  and Xiaomi's PatchWall. On another brand it stops and says so. Ask the person, then re-run with
  `STOCK_LAUNCHER=<package>` (or `none` to turn nothing off).
- **Remotes whose Home button doesn't reach the launcher** (some Xiaomi sticks send a different key):
  re-run the install with `HOME_KEY_SERVICE=1` added.
- **Slow TVs adapt on their own.** For a few calm seconds after Home appears, the page measures its own
  frame pacing. If more than 15% of frames are missed it steps down: first no city behind the dock and a
  slower background, then the night drive at the lowest resolution and half rate. The step is
  remembered. A smooth TV stops measuring and keeps everything. To try the full version again (for
  example after a WebView update), uninstall and reinstall, or clear the launcher's storage.

## Undo

- Keep Project Illusion but drop the name, weather and dock choices: `TV_HOST=<ip> tools/theme.sh --reset`.
- Back to the TV's own Home (tested on a Sony BRAVIA; the stock launcher is the one the install
  script printed, e.g. `com.google.android.apps.tv.launcherx` on Google TV):
  ```bash
  export TV_HOST=<ip>
  tools/tv 'pm enable --user 0 <stock launcher>; pm uninstall com.projectillusion.home'
  tools/tv 'cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME <stock launcher> | tail -1)'
  ```
  Uninstalling deletes the launcher's own Continue watching history. Rows the apps publish come back.

## Testing on the TV

- `input keyevent` presses keys (ask first). Its `--longpress` is shorter than the Home page's hold,
  so it counts as a plain OK: on the Continue watching card that starts the show. Leave the hold-OK
  dial to a person with the remote.
- `screencap` fails (empty file) while a streaming app plays protected video.
- Holding OK opens the dial inside other apps (`DialService`), not on the Home screen. When the
  person asks how to jump back from a film, that's the answer. On Home they use the tabs and icons.
- When asked to put the TV back to its own Home, follow Undo above.

## Rules for Claude

- Ask before disabling or uninstalling any package, and before anything else that changes the TV
  beyond these steps. Pressing keys on the TV (`input keyevent`) interrupts whoever is watching: ask first.
- Never commit `tools/adbkey/`, `tools/venv/`, `launcher/local.properties` or build outputs.
- The night drive is generated: edit `design/nightdrive/` and regenerate (see its README), not
  `nightdrive.js` by hand.

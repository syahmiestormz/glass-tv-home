# Making an onn. 4K box fast again

The basic onn. 4K box has 2 GB of RAM, a low-power Cortex-A35 chip and only 8 GB of storage. What slows it
down most, in order:

1. **The Google TV home screen.** `launcherx` keeps content rows, trailers and ads loaded in memory, and
   refreshes them in the background. Replacing it with Stormz TV frees the most RAM.
2. **A full disk.** With under about 1 GB free, Android slows down everywhere. Settings → System → Storage
   shows it. Uninstall apps you don't use; Stormz TV clears app caches every 15 days, and Settings →
   Clean TV cache does it now.
3. **Background extras.** Ambient mode, Play Games, telemetry and ad services (see `debloat.txt`).
4. **Animations.** Halving them makes every menu feel quicker.

## Steps (from a computer on the same Wi-Fi)

Follow [AGENTS.md](../../AGENTS.md) steps 1–2 first: turn on developer options, connect, back up.

```sh
# 3. Debloat (after reading and editing the list)
TV_HOST=<ip> tools/disable.sh devices/onn-4k-google-tv/debloat.txt

# Faster animations (undo: set them back to 1)
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'settings put global window_animation_scale 0.5'
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'settings put global transition_animation_scale 0.5'
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'settings put global animator_duration_scale 0.5'

# 5. Install Stormz TV as Home (it checks itself and puts Google TV back if anything fails)
TV_HOST=<ip> launcher/build-and-install.sh

# Stormz TV's screensaver instead of ambient mode
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.syahmie.stormztv/.ClockDream'
```

On the box itself: Settings → Privacy → Usage & diagnostics **off**, and Settings → Privacy → Ads →
**Delete advertising ID**. A restart once a week (Stormz TV: Settings → Restart) clears leaked memory.

`reduced_motion` is already on in this build, which suits the onn.'s Mali-G31 GPU. If the selection still
stutters, also set `hud_grid` to `false` in `launcher/app/src/main/res/values/config.xml` and rebuild.

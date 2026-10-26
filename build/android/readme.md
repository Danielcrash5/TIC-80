# USEFUL ADB COMMANDS

## Android cart directory

On Android 11 and newer, the app asks for the Android **All files access**
special permission at startup. Once granted, TIC-80 stores carts and editable
user files in the shared `Documents/TIC-80` folder. The folder can then be
edited with Android file manager apps, and it is also exposed by TIC-80's
Documents provider. Existing files are copied there on first launch after the
permission is granted; the old files are retained, and name conflicts are not
overwritten.

This is a broad Android permission and is intended here for a personal,
self-built APK; Google Play distribution may not allow it. On Android 10 and
older, TIC-80 keeps using its existing app-specific `Android/data` directory.

## BUILD IN DOCKER

You may need to add exec `sdkmanager "ndk;23.2.8568313"` just before `./gradlew assembleRelease`, the version must match the one shown in `app/build.gradle`.

```
winpty docker exec -it nice_goldwasser bash -c "cd /tic80/build/android && ./gradlew assembleRelease" && adb install -r app/build/outputs/apk/arm8/release/app-arm8-release.apk && adb shell am start -n com.nesbox.tic/com.nesbox.tic.TICStorageAccessActivity
```

## INSTALL APP AND RUN
```
release: adb install -r app/build/outputs/apk/arm8/release/app-arm8-release.apk && adb shell am start -n com.nesbox.tic/com.nesbox.tic.TICStorageAccessActivity

debug: adb install -r app/build/outputs/apk/arm8/debug/app-arm8-debug.apk && adb shell am start -n com.nesbox.tic/com.nesbox.tic.TICStorageAccessActivity
```

## VIEW APP LOG
```
adb logcat --pid `adb shell pidof com.nesbox.tic`
```

## STOP APP
```
adb shell am force-stop com.nesbox.tic
```

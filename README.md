# Schoolday

An unofficial student app for SŠIEŘ Rožnov pod Radhoštěm. It shows class timetables from the school's public Bakaláři timetable and lunches from the school website and iCanteen.

It is not affiliated with the school. iCanteen logins are stored encrypted on the phone and sent only to the school's iCanteen server.

## Install

Download the latest APK from [Releases](https://github.com/rylmo/schoolday/releases/latest) (Android 12 or newer). After that, the app offers new versions itself when you open it.

## Publishing a release

Raise `versionCode` and `versionName` in `app/build.gradle.kts`, push to `main`, then run:

```powershell
.\scripts\publish-release.ps1 -Notes "What changed"
```

Releases are signed with a key kept in `signing/`, which is deliberately not in this repository.

The Geologica font is licensed under the SIL Open Font License (`app/src/main/assets/licenses`).

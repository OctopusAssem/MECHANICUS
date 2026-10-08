# MECHANICUS

**Your Auto Repair Assistant** — an offline-first Android app for a car service center.

Arabic / English UI. Records vehicles, customers, spare parts and staged payments.
Keeps its data in a folder on internal or external storage (monthly SQLite shards +
a small customer index + separate photo files), and is designed to sync to Google
Drive so two users can work on the same data.

## Build

Automated via GitHub Actions (`.github/workflows/build.yml`). Every push to `main`
builds a signed release APK and attaches it to a GitHub release (`build-<run>`).

Local build (needs JDK 21 + Android SDK 37):

```bash
gradle :app:assembleRelease
```

## Signing

Release builds are signed with `app/keystore/mechanicus.p12`
(alias `mechanicus`, store/key password `mechanicus123`).
Certificate SHA-1: `77:96:93:23:38:83:DF:04:43:64:FF:E4:7A:37:9F:FA:A6:75:91:97`
(needed later to register the Android OAuth client for Google Drive).

## Storage layout

```
<internal|external>/MECHANICUS/
├── app.db              # users, change log, car index (fast search)
├── data/YYYY-MM.db     # vehicle shards, one per month
├── photos/             # plate photos (outside the DB)
```

## License

MIT — Assem Hussein.

---

**Maintained by [OctopusAssem](https://github.com/OctopusAssem) (عاصم حسين)** — 🌐 [octopusassem.github.io](https://octopusassem.github.io/)

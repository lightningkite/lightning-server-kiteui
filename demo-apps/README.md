# demo-apps

The KiteUI half of this repo's demo; `demo-server` is its backend and `demo-shared` its models.

```bash
./gradlew :demo-server:run --args="serve"   # API on :8090
./gradlew :demo-apps:jsViteDev              # web app on :8091
./gradlew :demo-server:generateSdk          # after changing a model or endpoint
```

What each page demonstrates, and how to run the other platforms: [docs/demo-apps.md](../docs/demo-apps.md).

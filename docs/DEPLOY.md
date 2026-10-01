# Publishing: backend on Render, app as an Android APK

The API and its database run on [Render](https://render.com) (described in `render.yaml`).
The app is built by [EAS](https://expo.dev/eas) into an APK that anyone can install.

## 1. Backend on Render

1. Merge the code into `main`. Render deploys from `main` (`render.yaml`, `branch: main`).
2. On Render: **New → Blueprint**, pick this GitHub repository and confirm.
   - It creates `supermarket-agent-api` (Docker, free plan) and `supermarket-db` (PostgreSQL 16,
     `basic-256mb`, about US$ 6/month; free databases are deleted after 30 days).
   - It asks for `GEMINI_API_KEY`. `JWT_SECRET` is generated, and the database settings are filled in.
3. Wait for the deploy, then open `https://<service>.onrender.com/actuator/health`. It should
   say `UP`. Note the address: Render adds a suffix when the name is already taken.

The free web instance sleeps after 15 minutes without requests; the next request wakes it in
about a minute.

## 2. Move your local data

Do this once, right after the first deploy. The backup carries the Flyway history, so the API
sees an up-to-date schema and does not migrate again.

1. Make a backup of the local database (Docker running, `docker compose up -d`):

   ```bash
   MSYS_NO_PATHCONV=1 docker exec supermarket-postgres pg_dump -U supermarket -d supermarket -Fc --no-owner --no-privileges -f /tmp/supermarket.dump
   docker cp supermarket-postgres:/tmp/supermarket.dump ../supermarket-backups/supermarket.dump
   ```

   Keep backups **out of the repository**: they hold personal data and password hashes
   (`*.dump` is ignored by git).

2. On Render, **suspend** `supermarket-agent-api` so nothing writes during the restore.
3. Copy the database's **External Database URL** (`supermarket-db` → *Connect*).
4. Restore. `--clean` replaces the empty tables the first deploy created:

   ```bash
   docker run --rm -v "C:/Users/user/Downloads/supermarket-backups:/b" postgres:16 \
     pg_restore --clean --if-exists --no-owner --no-privileges \
     -d "<External Database URL>?sslmode=require" /b/supermarket.dump
   ```

5. **Resume** the API and log in with your usual account.

## 3. Android APK

One-time setup (free Expo account):

```bash
cd mobile
npx eas-cli login
npx eas-cli init        # links the project; commit the projectId it adds to app.json
```

If Render gave the API a different address, update `EXPO_PUBLIC_API_URL` in `mobile/eas.json`.
It must be the `https://` address: Android blocks plain `http` in installed apps.

Build:

```bash
npx eas-cli build -p android --profile preview
```

The build runs on Expo's servers (about 15 minutes on the free tier). At the end it prints a
link and a QR code: open it on the phone, download the APK and allow installing from that source.
Send the same link to anyone else who wants the app.

Rebuild after changing the app or the API address. Backend-only changes need no new APK.

## Later

- **Google Play:** `npx eas-cli build -p android --profile production` makes an `.aab`. A Play
  Console account costs US$ 25 once; new personal accounts must run a closed test with
  12 testers for 14 days before going public.
- **iPhone:** needs an Apple Developer account (US$ 99/year), then TestFlight or the App Store.

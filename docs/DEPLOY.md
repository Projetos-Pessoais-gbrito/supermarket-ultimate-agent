# Publishing: backend on Render, app as an Android APK

Everything runs on free plans:

- the API on [Render](https://render.com) (described in `render.yaml`);
- the database on [Neon](https://neon.tech) (free PostgreSQL, 0.5 GB, scales to zero when idle);
- the app is built by [EAS](https://expo.dev/eas) into an APK that anyone can install.

## 1. Database on Neon

1. Create a Neon project in **AWS US East (N. Virginia)**, next to Render's Virginia region,
   with a database `supermarket`.
2. Open **Connect** with **connection pooling off** and note the host
   (`ep-....us-east-1.aws.neon.tech`), the role and the password.

## 2. Move your local data

The backup carries the Flyway history, so the API sees an up-to-date schema and does not
migrate again.

1. Make a backup of the local database (Docker running, `docker compose up -d`):

   ```bash
   MSYS_NO_PATHCONV=1 docker exec supermarket-postgres pg_dump -U supermarket -d supermarket -Fc --no-owner --no-privileges -f /tmp/supermarket.dump
   docker cp supermarket-postgres:/tmp/supermarket.dump ../supermarket-backups/supermarket.dump
   ```

   Keep backups **out of the repository**: they hold personal data and password hashes
   (`*.dump` is ignored by git).

2. Restore it into Neon (`--clean` replaces anything already there):

   ```bash
   MSYS_NO_PATHCONV=1 docker run --rm -v "C:/Users/user/Downloads/supermarket-backups:/b" postgres:17      pg_restore --clean --if-exists --no-owner --no-privileges      -d "postgresql://<role>:<password>@<host>/supermarket?sslmode=require" /b/supermarket.dump
   ```

   If the API is already deployed, suspend it on Render during the restore and resume it after.

## 3. Backend on Render

1. Merge the code into `main`. Render deploys from `main` (`render.yaml`, `branch: main`).
2. On Render: **New → Blueprint**, pick this GitHub repository and confirm.
   - It creates `supermarket-agent-api` (Docker, free plan).
   - It asks for `POSTGRES_HOST`, `POSTGRES_USER` and `POSTGRES_PASSWORD` (from Neon) and
     `GEMINI_API_KEY`. `JWT_SECRET` is generated; the port, database name and `sslmode=require`
     are already set.
3. Wait for the deploy, then open `https://<service>.onrender.com/actuator/health`. It should
   say `UP`. Note the address: Render adds a suffix when the name is already taken.

The free web instance sleeps after 15 minutes without requests; the next request wakes it in
about a minute. To keep it awake, have a free monitor such as [cron-job.org](https://cron-job.org)
call `/actuator/health/liveness` every 10 minutes. Use the liveness URL: it does not query the
database, so Neon can still scale to zero and stay within its free compute hours.

## 4. Android APK

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

# Testing the app on your phone (Expo Go)

## One-time setup

| What | Why |
|------|-----|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) running | PostgreSQL database |
| JDK 21 (e.g. [Temurin 21](https://adoptium.net/temurin/releases/?version=21)) | Backend |
| Node.js 20 or 22 | Mobile dev server |
| **Expo Go** app on the phone (Play Store / App Store), updated | Runs the app |
| `.env` in the repository root with `JWT_SECRET` (copy `.env.example`) | Login tokens |

Allow the backend through the Windows firewall once, in an **administrator** terminal:

```powershell
netsh advfirewall firewall add rule name="Supermarket API 8080" dir=in action=allow protocol=TCP localport=8080
```

(Or click **Allow** on the Windows prompt the first time Java starts, ticking *Private networks*.)

## Every time

Phone and computer on the **same Wi-Fi**. Three terminals from the repository root:

```bash
# 1. Database
docker compose up -d

# 2. Backend (http://localhost:8080/actuator/health should say UP)
cd backend && ./mvnw spring-boot:run

# 3. App
cd mobile && npm ci && npm start
```

Scan the QR code shown in terminal 3:

- **Android**: open Expo Go → *Scan QR code*.
- **iPhone**: open the Camera app and tap the banner.

The app finds the backend automatically: it uses the computer's address that Expo Go
already knows, on port 8080.

## Try it

1. Create an account.
2. Tap **Escanear nota** and point the camera at the QR code at the bottom of a São Paulo
   supermarket receipt (NFC-e).
3. The receipt opens with store, items and totals, and appears in **Minhas notas**.

No receipt at hand? Paste the link of a receipt QR code in **Ou cole o link do QR code**.

## Troubleshooting

| Message / symptom | Fix |
|-------------------|-----|
| "Não foi possível conectar ao servidor…" | Backend not running, firewall blocking port 8080, or phone on another network (guest Wi-Fi, mobile data). |
| Expo Go says the project is incompatible | Update Expo Go: it only supports the latest SDK (57). |
| Phone can't reach even the dev server | Some routers isolate devices. Use `npm start -- --tunnel`, and expose the backend too (e.g. `ngrok http 8080`), then start with `EXPO_PUBLIC_API_URL=https://<ngrok-url> npm start -- --tunnel`. |
| "Não foi possível ler essa nota na SEFAZ…" | Only São Paulo receipts are supported for now. |
| "A SEFAZ não está respondendo…" | SEFAZ-SP is down or slow; try again later. |
| Asked to log in again | Sessions last 30 days of inactivity; after "Sair" or a detected stolen session you must log in again. |

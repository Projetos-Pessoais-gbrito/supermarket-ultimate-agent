# Mobile

React Native + Expo (SDK 57) + TypeScript app, using Expo Router (file-based routes in `src/app`).

## Run

```bash
npm ci
npm start        # then press a (Android), i (iOS) or w (web), or scan with Expo Go
```

## Quality checks

```bash
npm run lint
npm run typecheck
```

Always add native/Expo packages with `npx expo install <pkg>` so versions match the SDK.

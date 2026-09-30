# Supermarket Ultimate Agent

Scan the QR code of your supermarket receipt (NFC-e) from Assaí, Atacadão, Carrefour
or any other store in Brazil. The app imports every item from SEFAZ, stores your purchase
history and turns it into insights: the best day of the month to buy, how much you could
have saved, the cheapest store for each product, and more.

> Status: v1.0.0. See the [roadmap](docs/ROADMAP.md) and the [changelog](CHANGELOG.md).
> Try it on your phone with Expo Go: [testing guide](docs/TESTING-ON-PHONE.md).

## Repository layout

| Path       | Content                                  |
|------------|------------------------------------------|
| `backend/` | Java 21 + Spring Boot 3 REST API         |
| `mobile/`  | React Native + Expo + TypeScript app     |
| `docs/`    | Architecture, roadmap and ADRs           |

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture Decision Records](docs/adr/)
- [Contributing (git-flow, commit conventions)](CONTRIBUTING.md)

## Supported states

| State | Status  |
|-------|---------|
| SP    | supported (NFC-e QR codes) |

# ADR 0006 — Login and registration rate limiting

- Status: accepted
- Date: 2026-09-30

## Context
`POST /api/auth/login` accepted unlimited password guesses, and `POST /api/auth/register`
unlimited account creation. BCrypt slows each guess down, but not enough against an automated
attacker.

## Decision
A sliding-window limiter (`LoginAttemptLimiter`) in front of both endpoints:

| Counter | Default | What it stops |
|---------|---------|---------------|
| Failed logins per e-mail | 5 per 15 min | guessing one account's password |
| Failed logins per client IP | 20 per 15 min | trying many accounts from one place |
| Registrations per client IP | 10 per 15 min | account spam |

- Over the limit → **429** with an RFC 9457 problem detail and `Retry-After` (seconds).
- Failures count for **unknown e-mails too**, so the response never reveals whether an
  account exists. A successful login clears that e-mail's counter.
- The client IP is the direct peer (`getRemoteAddr()`); `X-Forwarded-For` is ignored because
  clients can forge it. Behind a reverse proxy, configure Tomcat's `RemoteIpValve` for the proxy.
- Limits are configurable under `app.security.login-attempts.*`; time comes from the shared
  `Clock`, so tests control it.

## Consequences
- State is **in memory, per instance**. That is right for today's single backend instance.
  With several instances each would count separately; then move the counters to Redis or the
  database.
- Counters are lost on restart (acceptable: an attacker gains at most one window).
- The map of counters is bounded (expired entries are evicted past 100 000 keys), so cycling
  random e-mails cannot exhaust memory.
- A locked e-mail blocks even the right password until the window passes; the app explains
  this with a friendly message.

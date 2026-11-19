# Pingly server

Node.js 24 + TypeScript (run natively via type stripping — no build step), Fastify 5,
WebSockets and the built-in `node:sqlite`. One small container, one volume.

```bash
npm install
npm run dev        # http://localhost:3000, OTP codes are printed to the log
npm test           # integration tests (real HTTP + WebSocket, in-memory DB)
npm run typecheck

# A virtual second phone (same API and end-to-end crypto as the app) for manual testing:
PARTNER_STATE=/tmp/partner.json node scripts/virtual-partner.ts help
```

## What it does

| Area | Endpoints |
|------|-----------|
| Phone OTP login (SMS.ir) | `POST /v1/auth/otp/request`, `POST /v1/auth/otp/verify`, `POST /v1/auth/logout` |
| Profile, consent, Pause | `GET /v1/me`, `PATCH /v1/me`, `POST /v1/me/pause`, `DELETE /v1/me` (delete account) |
| Mutual pairing | `GET/POST /v1/pair/invites`, `POST /v1/pair/invites/:id/accept\|decline`, `DELETE /v1/pair/invites/:id`, `DELETE /v1/pair` |
| Interactions | `POST /v1/events`, `GET /v1/events`, `POST /v1/events/:id/ack\|opened` |
| Encrypted media | `POST /v1/media?kind=voice\|image` (octet-stream), `GET /v1/media`, `GET/DELETE /v1/media/:id` |
| Realtime | `GET /v1/ws` (WebSocket, `Authorization: Bearer …`) |
| Health | `GET /health` |

### Security model

- **OTP**: 6 digits, 2-minute lifetime, 60 s resend cooldown, 5 attempts, stored only as
  `HMAC(secret, phone:code)`; per-IP and per-phone rate limits.
- **Sessions**: opaque `pgl_…` bearer tokens, stored as SHA-256. A new login on another phone
  revokes older sessions (the end-to-end keys live on the device).
- **Consent**: pairing needs the invitee's explicit accept; invites never reveal whether a
  number is registered. The *receiver* decides which actions are allowed and can Pause; both
  are enforced here, not just in the app.
- **End-to-end encryption**: message, question and answer text, captions, voice notes and
  photos are encrypted on the phone with a key derived from both partners' ECDH P-256 keys.
  The server stores and relays ciphertext only.
- **Privacy**: unpairing deletes the couple's events and media; deleting the account erases
  everything tied to it.

### Realtime protocol

Server → app: `hello`, `event`, `status` (delivered/opened/answered/expired), `presence`,
`sync` (re-fetch `/v1/me`), `session_revoked`, `pong`.
App → server: `ack`, `opened`, `ping`. Protocol pings every 25 s drop dead sockets.

Instant actions (`ping`, `vibrate`, `sound`, `voice`) expire if the partner's phone stays
unreachable for 10 minutes; messages, photos and questions wait until it reconnects.

## Configuration

See `.env.example`. In production set `NODE_ENV=production`, mount a volume at `/data`, and
provide `SMSIR_API_KEY` + `SMSIR_TEMPLATE_ID` (+ `SMSIR_PARAMETER_NAME`, default `code`).

# Signal-style E2EE (Phase 2) — Implementation Plan

Status: **IN PROGRESS**
Branch: `feature/signal-e2ee-phase2`

## Goal
Implement end-to-end encryption for app↔bridge communications inspired by Signal architecture (X3DH + Double Ratchet semantics), while keeping compatibility with media attachments and current chat UX.

## Security target
- Bridge can route ciphertext but cannot read plaintext message content after full migration mode.
- Session keys rotate forward-securely.
- Replay protection with message counters and associated data (AD).

## Protocol shape (target)
1. **Identity + prekeys**
   - Device identity key pair (long-term)
   - Signed prekey (medium-term)
   - One-time prekeys (batch)
2. **Session bootstrap**
   - Client fetches bridge prekey bundle
   - Client performs X3DH-style agreement
   - First encrypted envelope includes initial ratchet header
3. **Transport envelope**
   - `v`: protocol version
   - `sessionId`: logical app session
   - `counter`: monotonic per ratchet chain
   - `nonce` / `header`
   - `ciphertext`
   - `ad`: optional associated data (timestamp, message id)
4. **Attachments**
   - Encrypt attachment payload before base64 transport
   - Include MIME/name only as needed metadata (minimal leakage)
5. **Recovery**
   - Session reset endpoint
   - Safe fallback (explicitly disabled when strict-E2EE enabled)

## Migration stages
### Stage A (started in this branch)
- Add E2EE capability flags and strict enforcement mode in bridge.
- Add E2EE status endpoint for client capability negotiation.

### Stage B
- Add cryptographic primitives and key storage on Android client.
- Add bridge prekey bundle generation + rotation.

### Stage C
- Add encrypted `/chat` payload path and decryption pipeline.
- Add encrypted response payload path.

### Stage D
- Add attachment encryption and transcript/TTS path compatibility.

### Stage E
- Remove plaintext path in strict deployments.

## Crypto stack (proposed)
- Curves/key agreement: X25519
- KDF: HKDF-SHA256
- AEAD: AES-256-GCM (or ChaCha20-Poly1305 if consistently available)
- Signature for prekeys: Ed25519

## Operational notes
- Keep TLS enabled even with E2EE (defense in depth)
- Key rotation and backup policy must be documented before production rollout
- Multi-device support requires per-device key identity mapping

## Immediate next coding tasks
1. Add bridge prekey-bundle endpoint with signed metadata.
2. Implement Android key manager (identity + signed prekey cache).
3. Add encrypted envelope parser/serializer in app.
4. Introduce feature flag in app settings: `Require E2EE`.
5. Add integration test vectors for envelope encryption/decryption.

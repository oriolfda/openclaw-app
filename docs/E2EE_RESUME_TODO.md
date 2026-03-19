# E2EE Resume TODO (safe stop point)

Last updated: 2026-03-19 21:12 UTC
Branch: `feature/signal-e2ee-phase2`

## Safe-stop status
- Working tree clean at stop point.
- Latest pushed commits:
  - openclaw-app: `bd908ef`
  - aigor-app equivalent: `ad08a40`
- Current code remains functional with existing E2EE path.

## Real progress snapshot (recalculated)
1. Persistent DR session state model — DONE (100%)
2. Inbound decrypt using persistent `recvChainSeed` — IN PROGRESS (~45%)
3. Reply encrypt using persistent `sendChainSeed` — IN PROGRESS (~40%)
4. Per-message chain advancement — IN PROGRESS (~35%)
5. DH ratchet step over `rootKeySeed` — IN PROGRESS (~35%)
6. Header-based skipped cache usable (`headerId+counter`) — IN PROGRESS (~60%)
7. Persistence/recovery after restart — IN PROGRESS (~55%)
8. Strict checks/fallback policy — PENDING
9. Final tests/validation — PENDING

## Pending tasks (next exact order)
1) Finish inbound decrypt path to prioritize and advance `recvChainSeed` state per message.
2) Finish outbound reply encryption to prioritize and advance `sendChainSeed` state per message.
3) Complete DH ratchet root-key update lifecycle and controlled re-seeding.
4) Finish headerId+counter skipped-key retrieval/consumption path (not only storage).
5) Add strict checks (`E2EE_REQUIRED` hard fail on any plaintext path).
6) Run final test matrix:
   - replay reject
   - out-of-order within window
   - restart persistence continuity
   - attachment encrypted path

## Resume checklist
- Confirm branch: `feature/signal-e2ee-phase2`
- Start with task (1)
- Commit in small slices (1 task = 1 commit)
- Build after each commit:
  - openclaw-app: `./gradlew :app:assembleDebug`
  - aigor-app: `./gradlew :app:assembleRelease`

## ETA (realistic)
- Remaining for block completion: ~2.5h to 4h
- Production hardening after that: +1 day

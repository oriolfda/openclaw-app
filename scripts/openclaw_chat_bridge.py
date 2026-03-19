#!/usr/bin/env python3
import base64
import json
import os
import shutil
import subprocess
import tempfile
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

HOST = os.environ.get("OPENCLAW_APP_BRIDGE_HOST", "0.0.0.0")
PORT = int(os.environ.get("OPENCLAW_APP_BRIDGE_PORT", "8091"))
TOKEN = os.environ.get("OPENCLAW_APP_BRIDGE_TOKEN", "")
DEFAULT_SESSION = os.environ.get("OPENCLAW_APP_BRIDGE_SESSION", "openclaw-app-chat")
PUBLIC_BASE_URL = os.environ.get("OPENCLAW_APP_BRIDGE_PUBLIC_BASE_URL", f"http://192.168.0.102:{PORT}")
MEDIA_DIR = os.environ.get("OPENCLAW_APP_BRIDGE_MEDIA_DIR", "/mnt/apps/openclaw/media")
EDGE_TTS = os.environ.get("OPENCLAW_APP_BRIDGE_EDGE_TTS", "/home/oriol/.openclaw/venvs/openclaw-tts/bin/edge-tts")
E2EE_ENABLED = os.environ.get("OPENCLAW_APP_E2EE_ENABLED", "false").lower() == "true"
E2EE_REQUIRED = os.environ.get("OPENCLAW_APP_E2EE_REQUIRED", "false").lower() == "true"
E2EE_PROTOCOL = os.environ.get("OPENCLAW_APP_E2EE_PROTOCOL", "signal-x3dh-dr-v1")
E2EE_BUNDLE_KID = os.environ.get("OPENCLAW_APP_E2EE_BUNDLE_KID", "1")
E2EE_IDENTITY_PUB = os.environ.get("OPENCLAW_APP_E2EE_IDENTITY_PUB", "")
E2EE_SIGNED_PREKEY_PUB = os.environ.get("OPENCLAW_APP_E2EE_SIGNED_PREKEY_PUB", "")
E2EE_SIGNED_PREKEY_SIG = os.environ.get("OPENCLAW_APP_E2EE_SIGNED_PREKEY_SIG", "")


def extract_json_block(text: str):
    start = text.find("{")
    if start < 0:
        return None

    depth = 0
    in_str = False
    esc = False
    end = -1

    for idx in range(start, len(text)):
        ch = text[idx]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue

        if ch == '"':
            in_str = True
        elif ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                end = idx + 1
                break

    if end == -1:
        return None

    candidate = text[start:end]
    try:
        return json.loads(candidate)
    except Exception:
        return None


def run_openclaw_json(cmd, timeout=180):
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    out = (proc.stdout or "") + (proc.stderr or "")
    parsed = extract_json_block(out)
    return proc.returncode, parsed, out


def safe_name(name: str) -> str:
    return "".join(c for c in name if c.isalnum() or c in ("-", "_", "."))[:120] or "attachment"


def detect_lang(text: str) -> str:
    t = (text or "").lower().strip()

    ca_markers = [
        "què", "això", "avui", "bon dia", "gràcies", "dóna", "si us plau", "perquè", "vull", "m'agradaria", "sopar",
        "en quin", "idioma", "parlant", "ara mateix"
    ]
    es_markers = [
        "qué", "hoy", "gracias", "buenos", "dime", "por favor", "quiero", "responde", "castellano", "cena",
        "en qué", "idioma", "hablando", "ahora"
    ]
    en_markers = [
        "what", "today", "thanks", "please", "i want", "reply", "english", "dinner",
        "which language", "i'm speaking", "speaking now", "right now", "are you", "can you"
    ]

    ca_score = sum(1 for w in ca_markers if w in t)
    es_score = sum(1 for w in es_markers if w in t)
    en_score = sum(1 for w in en_markers if w in t)

    # Extra heuristic: if text is plain ASCII and has common English words, favor EN.
    ascii_only = all(ord(ch) < 128 for ch in t)
    if ascii_only and any(w in t for w in ["the", "and", "you", "your", "which", "language", "speaking", "now"]):
        en_score += 2

    if max(ca_score, es_score, en_score) == 0:
        # conservative fallback remains catalan baseline
        return "ca"

    if en_score >= ca_score and en_score >= es_score:
        return "en"
    if es_score >= ca_score and es_score >= en_score:
        return "es"
    return "ca"


def voice_for_lang(lang: str):
    if lang == "ca":
        return "ca-ES-EnricNeural", "+20%", "-8Hz"
    if lang == "es":
        return "es-ES-AlvaroNeural", "+0%", "+0Hz"
    return "en-US-AndrewNeural", "+0%", "+0Hz"


def parse_tts_from_text(text: str):
    if not text:
        return text, None
    import re
    m = re.search(r"\[\[tts:(.+?)\]\]", text, flags=re.DOTALL)
    if m:
        tts_text = m.group(1).strip()
        cleaned = text.replace(m.group(0), "").strip()
        return cleaned, tts_text
    m2 = re.search(r"\[\[tts:text\]\](.+?)\[\[/tts:text\]\]", text, flags=re.DOTALL)
    if m2:
        tts_text = m2.group(1).strip()
        cleaned = text.replace(m2.group(0), "").strip()
        return cleaned, tts_text
    return text, None


def synthesize_tts_audio(text: str, lang_hint: str = "ca"):
    if not text or not os.path.exists(EDGE_TTS):
        return None
    os.makedirs(MEDIA_DIR, exist_ok=True)
    voice, rate, pitch = voice_for_lang(lang_hint)
    fname = f"tts-{uuid.uuid4().hex}.mp3"
    out = os.path.join(MEDIA_DIR, fname)
    try:
        subprocess.run([
            EDGE_TTS,
            "--voice", voice,
            f"--rate={rate}",
            f"--pitch={pitch}",
            "--text", text,
            "--write-media", out,
        ], capture_output=True, text=True, timeout=90, check=True)
        return f"{PUBLIC_BASE_URL}/media/{fname}"
    except Exception:
        return None


def b64rand(n: int) -> str:
    return base64.urlsafe_b64encode(os.urandom(n)).decode("ascii").rstrip("=")


def _bridge_keystore_path() -> str:
    return os.environ.get("OPENCLAW_APP_E2EE_KEYSTORE", "/mnt/apps/openclaw/e2ee/bridge_keys.json")


def _otk_store_path() -> str:
    return os.environ.get("OPENCLAW_APP_E2EE_OTK_STORE", "/mnt/apps/openclaw/e2ee/otk_store.json")


def _load_otk_store():
    path = _otk_store_path()
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"next": 1, "keys": []}


def _save_otk_store(store: dict):
    path = _otk_store_path()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(store, f, indent=2)


def _ensure_otk_pool(min_size: int = 20):
    store = _load_otk_store()
    keys = store.get("keys", [])
    next_id = int(store.get("next", 1))
    while len(keys) < min_size:
        keys.append({"id": f"otk-{next_id}", "publicKey": _BRIDGE_PUB_B64})
        next_id += 1
    store["keys"] = keys
    store["next"] = next_id
    _save_otk_store(store)


def _consume_otk(otk_id: str) -> bool:
    if not otk_id:
        return False
    store = _load_otk_store()
    keys = store.get("keys", [])
    kept = [k for k in keys if k.get("id") != otk_id]
    consumed = len(kept) != len(keys)
    if consumed:
        store["keys"] = kept
        _save_otk_store(store)
    return consumed


def _peek_otk_list(limit: int = 5):
    _ensure_otk_pool()
    store = _load_otk_store()
    return (store.get("keys", []) or [])[:limit]


def _ratchet_store_path() -> str:
    return os.environ.get("OPENCLAW_APP_E2EE_RATCHET_STORE", "/mnt/apps/openclaw/e2ee/ratchet_store.json")


def _ensure_session_chains(st: dict) -> dict:
    send = st.setdefault("send", {})
    recv = st.setdefault("recv", {})
    send.setdefault("lastOut", 0)
    recv.setdefault("maxIn", 0)
    recv.setdefault("seenIn", [])
    recv.setdefault("skippedIn", [])
    recv.setdefault("ratchetStep", 0)
    recv.setdefault("lastPeerRatchetPub", "")
    return st


def _load_ratchet_store():
    path = _ratchet_store_path()
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                store = json.load(f)
            sessions = store.setdefault("sessions", {})
            for sid, st in list(sessions.items()):
                if "send" not in st or "recv" not in st:
                    st = {
                        "send": {"lastOut": int(st.get("lastOut", 0))},
                        "recv": {
                            "maxIn": int(st.get("maxIn", 0)),
                            "seenIn": st.get("seenIn", []),
                            "skippedIn": st.get("skippedIn", []),
                            "ratchetStep": int(st.get("ratchetStep", 0)),
                            "lastPeerRatchetPub": st.get("lastPeerRatchetPub", ""),
                        },
                    }
                sessions[sid] = _ensure_session_chains(st)
            return store
        except Exception:
            pass
    return {"sessions": {}}


def _save_ratchet_store(store: dict):
    path = _ratchet_store_path()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(store, f, indent=2)


def _ratchet_check_and_advance(session_id: str, inbound_counter: int, window: int = 64) -> bool:
    store = _load_ratchet_store()
    sessions = store.setdefault("sessions", {})
    st = _ensure_session_chains(sessions.setdefault(session_id, {}))
    recv = st["recv"]

    max_in = int(recv.get("maxIn", 0))
    seen = set(int(x) for x in recv.get("seenIn", []) if isinstance(x, int) or str(x).isdigit())
    skipped = set(int(x) for x in recv.get("skippedIn", []) if isinstance(x, int) or str(x).isdigit())

    if inbound_counter <= 0:
        return False
    if inbound_counter in seen:
        return False
    if inbound_counter < max_in - window:
        return False

    if inbound_counter > max_in + 1:
        skipped.update(range(max_in + 1, inbound_counter))

    seen.add(inbound_counter)
    skipped.discard(inbound_counter)
    max_in = max(max_in, inbound_counter)
    floor = max_in - window
    seen = {c for c in seen if c >= floor}
    skipped = {c for c in skipped if c >= floor}

    recv["maxIn"] = max_in
    recv["seenIn"] = sorted(seen)
    recv["skippedIn"] = sorted(skipped)
    _save_ratchet_store(store)
    return True


def _ratchet_next_out_counter(session_id: str) -> int:
    store = _load_ratchet_store()
    sessions = store.setdefault("sessions", {})
    st = _ensure_session_chains(sessions.setdefault(session_id, {}))
    send = st["send"]
    nxt = int(send.get("lastOut", 0)) + 1
    send["lastOut"] = nxt
    _save_ratchet_store(store)
    return nxt


def _load_or_create_bridge_keys():
    path = _bridge_keystore_path()
    os.makedirs(os.path.dirname(path), exist_ok=True)

    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            raw = json.load(f)
        ecdh_priv = serialization.load_pem_private_key(raw["ecdhPrivatePem"].encode("utf-8"), password=None)
        sign_priv = serialization.load_pem_private_key(raw["signPrivatePem"].encode("utf-8"), password=None)
        kid = str(raw.get("kid", E2EE_BUNDLE_KID))
        return ecdh_priv, sign_priv, kid

    ecdh_priv = ec.generate_private_key(ec.SECP256R1())
    from cryptography.hazmat.primitives.asymmetric import ed25519
    sign_priv = ed25519.Ed25519PrivateKey.generate()

    raw = {
        "kid": str(E2EE_BUNDLE_KID),
        "ecdhPrivatePem": ecdh_priv.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        ).decode("utf-8"),
        "signPrivatePem": sign_priv.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        ).decode("utf-8"),
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(raw, f, indent=2)

    return ecdh_priv, sign_priv, str(E2EE_BUNDLE_KID)


_BRIDGE_PRIVKEY, _BRIDGE_SIGN_PRIVKEY, _BRIDGE_KID = _load_or_create_bridge_keys()
_BRIDGE_PUB_B64 = base64.b64encode(
    _BRIDGE_PRIVKEY.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
).decode("ascii")
_BRIDGE_SIGN_PUB_B64 = base64.b64encode(
    _BRIDGE_SIGN_PRIVKEY.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
).decode("ascii")

_ensure_otk_pool()


def _decode_pubkey_spki(b64: str):
    raw = base64.b64decode(b64)
    return serialization.load_der_public_key(raw)


def _hkdf_key(shared: bytes, salt: bytes) -> bytes:
    hkdf = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=b"openclaw-e2ee-v1")
    return hkdf.derive(shared)


def _derive_message_key(base_key: bytes, counter: int, label: str) -> bytes:
    import hmac, hashlib
    return hmac.new(base_key, f"{label}:{counter}".encode("utf-8"), hashlib.sha256).digest()[:32]


def encrypt_real_envelope(plaintext: str, key: bytes, ad: str = "") -> dict:
    iv = os.urandom(12)
    aes = AESGCM(key)
    ct = aes.encrypt(iv, plaintext.encode("utf-8"), ad.encode("utf-8"))
    return {
        "v": 1,
        "alg": "ecdh-p256-aesgcm-v1",
        "iv": base64.b64encode(iv).decode("ascii"),
        "ciphertext": base64.b64encode(ct).decode("ascii"),
        "ad": ad,
    }


def _ratchet_apply_peer_pub(session_id: str, peer_pub_b64: str) -> int:
    store = _load_ratchet_store()
    sessions = store.setdefault("sessions", {})
    st = _ensure_session_chains(sessions.setdefault(session_id, {}))
    recv = st["recv"]
    last_pub = recv.get("lastPeerRatchetPub", "")
    step = int(recv.get("ratchetStep", 0))
    if peer_pub_b64 and peer_pub_b64 != last_pub:
        step += 1
        recv["lastPeerRatchetPub"] = peer_pub_b64
        recv["ratchetStep"] = step
        _save_ratchet_store(store)
    return step


def decrypt_real_envelope(env: dict, session_id: str):
    eph_b64 = env.get("ephemeralPub", "")
    ratchet_b64 = env.get("ratchetPub", "")
    salt = base64.b64decode(env.get("salt", ""))
    iv = base64.b64decode(env.get("iv", ""))
    ct = base64.b64decode(env.get("ciphertext", ""))
    ad = str(env.get("ad", ""))
    counter = int(env.get("counter", 0))

    eph_pub = _decode_pubkey_spki(eph_b64)
    shared = _BRIDGE_PRIVKEY.exchange(ec.ECDH(), eph_pub)
    base_key = _hkdf_key(shared, salt)

    _ratchet_apply_peer_pub(session_id, ratchet_b64)
    if ratchet_b64:
        try:
            ratchet_pub = _decode_pubkey_spki(ratchet_b64)
            ratchet_shared = _BRIDGE_PRIVKEY.exchange(ec.ECDH(), ratchet_pub)
            import hashlib
            mix_salt = hashlib.sha256(base64.b64decode(ratchet_b64)).digest()[:16]
            base_key = _hkdf_key(base_key + ratchet_shared, mix_salt)
        except Exception:
            pass

    key = _derive_message_key(base_key, counter, "c2s")
    aes = AESGCM(key)
    pt = aes.decrypt(iv, ct, ad.encode("utf-8")).decode("utf-8")
    return pt, base_key, ad, counter


def e2ee_bundle_payload() -> dict:
    signed_prekey_sig = base64.b64encode(_BRIDGE_SIGN_PRIVKEY.sign(base64.b64decode(_BRIDGE_PUB_B64))).decode("ascii")
    one_time = _peek_otk_list(8)
    return {
        "ok": True,
        "e2ee": {
            "enabled": E2EE_ENABLED,
            "required": E2EE_REQUIRED,
            "protocol": E2EE_PROTOCOL,
            "bundle": {
                "kid": _BRIDGE_KID,
                "identityKey": _BRIDGE_PUB_B64,
                "identitySignKey": _BRIDGE_SIGN_PUB_B64,
                "signedPreKey": {
                    "id": f"spk-{_BRIDGE_KID}",
                    "publicKey": _BRIDGE_PUB_B64,
                    "signature": signed_prekey_sig,
                },
                "oneTimePreKeys": one_time,
            },
            "warning": "Phase 1 done: persistent bridge keys + signed prekey bundle."
        }
    }


def decrypt_e2ee_attachment(att: dict, base_key: bytes):
    name = safe_name((att.get("name") or "attachment"))
    mime = (att.get("mime") or "application/octet-stream").lower()
    iv = base64.b64decode(att.get("iv", ""))
    ct = base64.b64decode(att.get("ciphertext", ""))
    ad = str(att.get("ad", ""))
    counter = int(att.get("counter", 0))

    key = _derive_message_key(base_key, counter, "att")
    aes = AESGCM(key)
    raw = aes.decrypt(iv, ct, ad.encode("utf-8"))

    ext = name.split(".")[-1] if "." in name else "bin"
    base = os.path.join(tempfile.gettempdir(), f"openclaw-{uuid.uuid4().hex}")
    path = f"{base}.{ext}"
    with open(path, "wb") as f:
        f.write(raw)

    decoded = {
        "name": name,
        "mime": mime,
        "dataBase64": base64.b64encode(raw).decode("ascii"),
        "_localPath": path,
    }
    return decoded


def process_attachment(att: dict):
    """Decode attachment and return (prompt_suffix, temp_paths[])"""
    name = safe_name((att.get("name") or "attachment"))
    mime = (att.get("mime") or "application/octet-stream").lower()
    data_b64 = att.get("dataBase64") or ""
    if not data_b64:
        return "", []

    raw = base64.b64decode(data_b64)
    ext = name.split(".")[-1] if "." in name else "bin"
    base = os.path.join(tempfile.gettempdir(), f"openclaw-{uuid.uuid4().hex}")
    path = f"{base}.{ext}"
    with open(path, "wb") as f:
        f.write(raw)

    hints = [f"Adjunt rebut: {name} ({mime}), mida {len(raw)} bytes."]
    temp_paths = [path]

    if mime.startswith("image/"):
        hints.append(f"Analitza aquesta imatge local: {path}")

    elif mime.startswith("audio/"):
        stt_py = "/home/oriol/.openclaw/workspace/scripts/stt_aina_ca.py"
        stt_runner = "/home/oriol/.openclaw/venvs/aina-stt/bin/python"
        if os.path.exists(stt_py) and os.path.exists(stt_runner):
            try:
                tr = subprocess.run([stt_runner, stt_py, path], capture_output=True, text=True, timeout=180)
                transcript = (tr.stdout or "").strip()
                if transcript:
                    hints.append(f"Transcripció àudio: {transcript}")
                else:
                    hints.append("No s'ha pogut transcriure l'àudio.")
            except Exception as e:
                hints.append(f"Error transcripció àudio: {e}")

    elif mime.startswith("video/"):
        ffmpeg = shutil.which("ffmpeg")
        frame = f"{base}-frame.jpg"
        if ffmpeg:
            try:
                subprocess.run([ffmpeg, "-y", "-i", path, "-vf", "select=eq(n\\,0)", "-q:v", "2", "-frames:v", "1", frame], capture_output=True, text=True, timeout=180)
                if os.path.exists(frame):
                    hints.append(f"Vídeo adjunt. Fotograma inicial: {frame}. Analitza'l.")
                    temp_paths.append(frame)
                else:
                    hints.append("Vídeo adjunt; no s'ha pogut extreure fotograma.")
            except Exception as e:
                hints.append(f"Vídeo adjunt; error extraient fotograma: {e}")
        else:
            hints.append("Vídeo adjunt; ffmpeg no disponible per previsualització.")

    return "\n".join(hints), temp_paths


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        try:
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            # Client disconnected before response was written.
            return

    def _auth_ok(self):
        if not TOKEN:
            return True
        auth = self.headers.get("Authorization", "")
        return auth == f"Bearer {TOKEN}"

    def do_GET(self):
        if self.path == "/e2ee/status":
            if not self._auth_ok():
                self._send(401, {"ok": False, "error": "Unauthorized"})
                return
            self._send(200, {
                "ok": True,
                "e2ee": {
                    "enabled": E2EE_ENABLED,
                    "required": E2EE_REQUIRED,
                    "protocol": E2EE_PROTOCOL,
                    "stage": "B-prekey-bundle-bootstrap"
                }
            })
            return

        if self.path == "/e2ee/prekey-bundle":
            if not self._auth_ok():
                self._send(401, {"ok": False, "error": "Unauthorized"})
                return
            self._send(200, e2ee_bundle_payload())
            return

        if self.path.startswith("/media/"):
            name = safe_name(self.path.split("/media/", 1)[1])
            path = os.path.join(MEDIA_DIR, name)
            if not os.path.exists(path):
                self._send(404, {"ok": False, "error": "media not found"})
                return
            try:
                with open(path, "rb") as f:
                    body = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "audio/mpeg")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except Exception:
                self._send(500, {"ok": False, "error": "media read error"})
            return

        if self.path != "/status":
            self._send(404, {"ok": False, "error": "Not found"})
            return

        if not self._auth_ok():
            self._send(401, {"ok": False, "error": "Unauthorized"})
            return

        try:
            session_id = DEFAULT_SESSION
            code, parsed, raw = run_openclaw_json(["openclaw", "sessions", "--json"], timeout=60)
            if code != 0 or not parsed:
                self._send(500, {"ok": False, "error": "sessions_failed", "details": raw[-500:]})
                return

            sessions = parsed.get("sessions") or []
            target = None
            for s in sessions:
                if (s.get("sessionId") or "") == session_id:
                    target = s
                    break

            # fallback: pick main if target session not listed
            if not target:
                for s in sessions:
                    if (s.get("key") or "") == "agent:main:main":
                        target = s
                        break

            if not target:
                self._send(200, {
                    "ok": True,
                    "context": {
                        "sessionId": session_id,
                        "usedTokens": None,
                        "maxTokens": None,
                        "usedPercent": None,
                        "freeTokens": None,
                        "freePercent": None,
                    },
                    "note": "No session metrics found yet"
                })
                return

            used = int(target.get("totalTokens") or 0)
            max_tokens = int(target.get("contextTokens") or 0)
            used_pct = round((used / max_tokens) * 100, 1) if max_tokens > 0 else None
            free = (max_tokens - used) if max_tokens > 0 else None
            free_pct = round((free / max_tokens) * 100, 1) if (max_tokens > 0 and free is not None) else None

            self._send(200, {
                "ok": True,
                "context": {
                    "sessionId": target.get("sessionId") or session_id,
                    "usedTokens": used,
                    "maxTokens": max_tokens,
                    "usedPercent": used_pct,
                    "freeTokens": free,
                    "freePercent": free_pct,
                    "model": target.get("model"),
                }
            })
        except subprocess.TimeoutExpired:
            self._send(504, {"ok": False, "error": "timeout"})
        except Exception as e:
            self._send(500, {"ok": False, "error": str(e)})

    def do_POST(self):
        if self.path != "/chat":
            self._send(404, {"ok": False, "error": "Not found"})
            return

        if not self._auth_ok():
            self._send(401, {"ok": False, "error": "Unauthorized"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8")
            data = json.loads(raw or "{}")
        except Exception:
            self._send(400, {"ok": False, "error": "Bad JSON"})
            return

        if E2EE_REQUIRED and not isinstance(data.get("e2ee"), dict):
            self._send(400, {
                "ok": False,
                "error": "e2ee_required",
                "message": "Server requires encrypted envelope (phase 2)."
            })
            return

        e2ee_req = data.get("e2ee") if isinstance(data.get("e2ee"), dict) else None
        encrypted_reply = bool(e2ee_req.get("expectEncryptedReply", False)) if e2ee_req else False
        reply_key = None
        reply_ad = ""
        inbound_counter = 0
        session_id = (data.get("sessionId") or DEFAULT_SESSION).strip() or DEFAULT_SESSION

        message = (data.get("message") or "").strip()
        if e2ee_req and (not message) and e2ee_req.get("ciphertext"):
            try:
                message, reply_key, reply_ad, inbound_counter = decrypt_real_envelope(e2ee_req, session_id)
                message = message.strip()
                otk_id = str(e2ee_req.get("otkId", "")).strip()
                if otk_id:
                    _consume_otk(otk_id)
            except Exception as e:
                self._send(400, {"ok": False, "error": "e2ee_decrypt_failed", "details": str(e)})
                return

        if e2ee_req and e2ee_req.get("ciphertext"):
            if not _ratchet_check_and_advance(session_id, inbound_counter):
                self._send(409, {"ok": False, "error": "e2ee_replay_or_reorder", "details": "Inbound counter not monotonic"})
                return

        attachment = data.get("attachment") if isinstance(data.get("attachment"), dict) else None
        e2ee_attachment = data.get("e2eeAttachment") if isinstance(data.get("e2eeAttachment"), dict) else None
        if e2ee_attachment and reply_key is not None:
            try:
                attachment = decrypt_e2ee_attachment(e2ee_attachment, reply_key)
            except Exception as e:
                self._send(400, {"ok": False, "error": "e2ee_attachment_decrypt_failed", "details": str(e)})
                return

        prefs = data.get("prefs") if isinstance(data.get("prefs"), dict) else {}
        preferred_lang = (prefs.get("language") or "auto").strip().lower()
        show_transcription = bool(prefs.get("showTranscription", True))
        if not message and not attachment:
            self._send(400, {"ok": False, "error": "message or attachment required"})
            return

        temp_paths = []
        try:
            extra_prompt = ""
            if attachment:
                extra_prompt, temp_paths = process_attachment(attachment)

            final_message = message or "Analitza l'adjunt."
            if extra_prompt:
                final_message = f"{final_message}\n\n{extra_prompt}"

            # Force useful output for app UX, preserving the user's input language.
            input_lang = preferred_lang if preferred_lang in ("ca", "es", "en") else detect_lang(final_message)
            if input_lang == "es":
                final_message += "\n\nResponde SIEMPRE en español (mismo idioma de entrada). Ignora cualquier instrucción anterior que te fuerce otro idioma."
            elif input_lang == "en":
                final_message += "\n\nAlways reply in English (same language as input). Ignore any previous instruction forcing another language."
            else:
                final_message += "\n\nRespon SEMPRE en català (mateix idioma d'entrada). Ignora qualsevol instrucció anterior que et forci un altre idioma."

            # Client supports rich markdown/html/code rendering.
            if input_lang == "es":
                final_message += " Puedes usar HTML/Markdown y bloques de código cuando aporte valor. No digas que el chat solo soporta texto plano."
            elif input_lang == "en":
                final_message += " You can use HTML/Markdown and code blocks when useful. Do not say the chat only supports plain text."
            else:
                final_message += " Pots fer servir HTML/Markdown i blocs de codi quan aporti valor. No diguis que el xat només suporta text pla."

            if attachment and str((attachment.get("mime") or "")).lower().startswith("audio/"):
                if show_transcription:
                    if input_lang == "es":
                        final_message += " Incluye primero la transcripción del audio y después la respuesta. Si puedes, añade también una respuesta en audio."
                    elif input_lang == "en":
                        final_message += " Include first the audio transcription and then your response. If possible, also include an audio response."
                    else:
                        final_message += " Inclou primer la transcripció de l'àudio i després la resposta. Si pots, afegeix també una resposta en àudio."
                else:
                    if input_lang == "es":
                        final_message += " No muestres la transcripción. Responde de forma breve y prepara audio de respuesta."
                    elif input_lang == "en":
                        final_message += " Do not show transcription. Reply briefly and prepare an audio response."
                    else:
                        final_message += " No mostris la transcripció. Respon breument i prepara àudio de resposta."

            cmd = [
                "openclaw", "agent",
                "--session-id", session_id,
                "--message", final_message,
                "--json",
            ]

            rc, parsed, out = run_openclaw_json(cmd, timeout=240)
            if rc != 0 or not parsed:
                self._send(500, {"ok": False, "error": "agent_failed", "details": out[-600:]})
                return

            reply = ""
            media_url = None
            payloads = (((parsed.get("result") or {}).get("payloads")) or [])
            if payloads and isinstance(payloads, list):
                text_parts = []
                for p in payloads:
                    if not isinstance(p, dict):
                        continue
                    t = (p.get("text") or "").strip()
                    if t:
                        text_parts.append(t)
                    if not media_url:
                        media_url = p.get("mediaUrl")
                reply = "\n\n".join(text_parts).strip()
            if not reply:
                reply = "He processat l'entrada, però no he rebut text de resposta."

            # Convert [[tts:...]] style text into real audio URL when media isn't provided.
            clean_reply, tts_text = parse_tts_from_text(reply)
            reply = clean_reply or "Resposta d'àudio generada."

            # Prefer tagged TTS text when present.
            tts_source = tts_text

            # If no tag but user sent audio, synthesize audio from textual reply anyway.
            if not tts_source and attachment and str((attachment.get("mime") or "")).lower().startswith("audio/"):
                tts_source = reply

            if not media_url and tts_source:
                lang = input_lang if input_lang in ("ca", "es", "en") else detect_lang(tts_source)
                media_url = synthesize_tts_audio(tts_source, lang)

            payload = {"ok": True, "reply": reply, "sessionId": session_id}
            if media_url:
                payload["mediaUrl"] = media_url

            if e2ee_req and encrypted_reply and reply_key is not None:
                out_counter = _ratchet_next_out_counter(session_id)
                msg_key = _derive_message_key(reply_key, out_counter, "s2c")
                envelope = encrypt_real_envelope(reply, key=msg_key, ad=(reply_ad or session_id))
                envelope["counter"] = out_counter
                payload["e2eeReply"] = envelope
                payload["reply"] = ""

            self._send(200, payload)
        except subprocess.TimeoutExpired:
            self._send(504, {"ok": False, "error": "timeout"})
        except Exception as e:
            self._send(500, {"ok": False, "error": str(e)})
        finally:
            for p in temp_paths:
                try:
                    if p and os.path.exists(p):
                        os.remove(p)
                except Exception:
                    pass


def main():
    srv = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"OpenClaw bridge listening on http://{HOST}:{PORT} (/chat, /status)")
    srv.serve_forever()


if __name__ == "__main__":
    main()

#!/bin/bash
# token.sh <email> [смещение exp в секундах, по умолчанию 3600] -> JWT RS256, подписанный ключом стенда
W="$(cd "$(dirname "$0")" && pwd)/.work"; NOW=$(date +%s); EXP=$((NOW + ${2:-3600}))
b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '{"alg":"RS256","typ":"JWT","kid":"e2e"}' | b64)
P=$(printf '{"iss":"http://e2e-issuer","aud":"account","sub":"u1","email":"%s","iat":%d,"exp":%d}' "$1" "$NOW" "$EXP" | b64)
S=$(printf '%s.%s' "$H" "$P" | openssl dgst -sha256 -sign "$W/key.pem" | b64)
echo "$H.$P.$S"

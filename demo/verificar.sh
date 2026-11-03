#!/usr/bin/env bash
# Confere o modo demonstração: as três contas entram e o dashboard vem preenchido.
# Sobe o ambiente antes: docker compose -f docker-compose.demo.yml up --build -d
set -u

URL=http://127.0.0.1:8090
SENHA=demo-solveit-2026

# A API leva alguns segundos para aplicar as migrations depois que o container sobe.
for _ in $(seq 1 60); do
  codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$URL/api/auth/me" || true)
  [ "$codigo" = 401 ] && break
  sleep 3
done
if [ "$codigo" != 401 ]; then
  echo "A API não respondeu (último código: $codigo)."
  exit 1
fi

token_de() {
  curl -s -X POST "$URL/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"senha\":\"$SENHA\"}" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

for conta in admin atendente solicitante; do
  if [ -z "$(token_de "$conta@demo.solveit.test")" ]; then
    echo "A conta $conta não conseguiu entrar."
    exit 1
  fi
  echo "Conta $conta: entrou."
done

resumo=$(curl -s "$URL/api/dashboard/resumo" -H "Authorization: Bearer $(token_de admin@demo.solveit.test)")
if ! echo "$resumo" | grep -q '"novos":[1-9]'; then
  echo "O dashboard não trouxe chamados nas últimas semanas."
  exit 1
fi
if ! echo "$resumo" | grep -q '"criticaAberta":[1-9]'; then
  echo "O dashboard não trouxe críticos abertos."
  exit 1
fi
echo "Dashboard: preenchido."

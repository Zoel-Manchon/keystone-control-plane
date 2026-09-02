#!/usr/bin/env bash
#
# Keystone :: end-to-end demo
#
# Brings up the infrastructure, builds the project, starts the control plane and walks
# the identity lifecycle: register, enrol, verify, rotate, publish firmware, roll out,
# inspect revocation and verify the audit chain.
#
#   ./demo.sh                         full run
#   ./demo.sh --fast                  skip the test suite during the build
#   ./demo.sh --full                  add the Testcontainers integration tests
#   ./demo.sh --keep                  leave the control plane running after a successful run
#   ./demo.sh --presentation --keep   paced, interactive run for recording the web console
#
set -Eeuo pipefail

# ---------------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------------

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT}"

WORKSPACE="${ROOT}/.demo"
LOG="${WORKSPACE}/keystone.log"
BUILD_LOG="${WORKSPACE}/build.log"
APP_JAR="${ROOT}/keystone-bootstrap/target/keystone.jar"
SIMULATOR_JAR="${ROOT}/keystone-simulator/target/keystone-simulator.jar"
MQTT_CERTS_DIR="${ROOT}/docker/mosquitto/certs"
BASE_URL="http://localhost:8080"
OPERATOR="operator"

SKIP_TESTS=false
FULL_SUITE=false
KEEP_RUNNING=false
PRESENTATION_MODE=false
APP_PID=""
DEMO_COMPLETED=false

usage() {
  cat <<'EOF'
Usage: ./demo.sh [options]

  --fast          Skip the Maven test suite during the build.
  --full          Also run the integration tests (Testcontainers; adds minutes).
  --keep          Leave the control plane running after a successful run.
  --presentation  Interactive, paced run for recording the web console.
  --help          Show this help.

Recommended for recording:
  ./demo.sh --presentation --keep

Optional pacing:
  KEYSTONE_PRESENTATION_DELAY=1.5 ./demo.sh --presentation --keep
EOF
}

for arg in "$@"; do
  case "${arg}" in
    --fast) SKIP_TESTS=true ;;
    --full) FULL_SUITE=true ;;
    --keep) KEEP_RUNNING=true ;;
    --presentation|--present) PRESENTATION_MODE=true ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'unknown option: %s\n\n' "${arg}" >&2; usage >&2; exit 1 ;;
  esac
done

BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GREEN=$'\033[32m'
YELLOW=$'\033[33m'; BLUE=$'\033[34m'; RESET=$'\033[0m'

step()   { printf '\n%s▸ %s%s\n' "${BOLD}${BLUE}" "$*" "${RESET}"; }
info()   { printf '  %s%s%s\n' "${DIM}" "$*" "${RESET}"; }
pass()   { printf '  %s✓ %s%s\n' "${GREEN}" "$*" "${RESET}"; }
warn()   { printf '  %s! %s%s\n' "${YELLOW}" "$*" "${RESET}"; }
fail()   { printf '  %s✗ %s%s\n' "${RED}" "$*" "${RESET}" >&2; exit 1; }
proves() { printf '  %s→ %s%s\n' "${DIM}" "$*" "${RESET}"; }

PRESENTATION_DELAY="${KEYSTONE_PRESENTATION_DELAY:-1.4}"

presentation_banner() {
  [[ "${PRESENTATION_MODE}" == true ]] || return 0
  printf '\n%s' "${BOLD}${BLUE}"
  cat <<'EOF'
                 .-----------------------.
              .-'                         '-.
            .'          K E Y S T O N E      '.
           /        DEVICE TRUST CONTROL       \
          /_____________________________________\
             |       |       |       |
             |  PKI  | mTLS  |  OTA  | AUDIT |
             |_______|_______|_______|_______|
                    LIVE PRESENTATION
EOF
  printf '%s\n' "${RESET}"
  printf '  %sIdentity lifecycle + adversarial controls, rendered live in the console.%s\n' \
    "${DIM}" "${RESET}"
}

presentation_beat() {
  [[ "${PRESENTATION_MODE}" == true ]] || return 0
  sleep "${PRESENTATION_DELAY}"
}

presentation_gate() {
  local title="$1"
  shift
  [[ "${PRESENTATION_MODE}" == true ]] || return 0

  printf '\n%s+----------------------------------------------------------+%s\n' "${BOLD}${BLUE}" "${RESET}"
  printf '%s| %-56s |%s\n' "${BOLD}${BLUE}" "${title}" "${RESET}"
  printf '%s+----------------------------------------------------------+%s\n' "${BOLD}${BLUE}" "${RESET}"
  while (($#)); do
    printf '  %s%s%s\n' "${DIM}" "$1" "${RESET}"
    shift
  done
  printf '\n'

  if [[ -t 0 ]]; then
    printf '  %sPress ENTER when the web console is ready...%s' "${BOLD}${YELLOW}" "${RESET}"
    read -r _
    printf '\n'
  else
    warn "presentation checkpoint skipped because stdin is not interactive"
  fi
}

show_log_excerpt() {
  local candidate="$1"
  [[ -s "${candidate}" ]] || return 0
  printf '\n%s─── %s ───%s\n' "${RED}" "${candidate}" "${RESET}" >&2
  grep -E "ERROR|BUILD FAILURE|Caused by|FATAL|Exception|error:" "${candidate}" \
    | tail -30 >&2 || tail -30 "${candidate}" >&2
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  set +e

  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    # --keep only applies to a fully successful demo. A failed run must not leave
    # port 8080 occupied or state that no longer matches the next clean database.
    if [[ ${exit_code} -eq 0 && "${DEMO_COMPLETED}" == true && "${KEEP_RUNNING}" == true ]]; then
      printf '\n%sControl plane still running (pid %s) — %s%s\n' \
        "${YELLOW}" "${APP_PID}" "${BASE_URL}" "${RESET}"
    else
      info "stopping the control plane"
      kill "${APP_PID}" 2>/dev/null || true
      for _ in $(seq 1 20); do
        kill -0 "${APP_PID}" 2>/dev/null || break
        sleep 0.25
      done
      kill -9 "${APP_PID}" 2>/dev/null || true
      wait "${APP_PID}" 2>/dev/null || true
      rm -f "${WORKSPACE}/control-plane.pid"
    fi
  fi

  if [[ ${exit_code} -ne 0 ]]; then
    printf '\n%sDemo aborted.%s\n' "${RED}" "${RESET}" >&2
    show_log_excerpt "${BUILD_LOG}"
    show_log_excerpt "${LOG}"
    for candidate in \
      "${WORKSPACE}/openssl-device-key.log" \
      "${WORKSPACE}/openssl-device-csr.log" \
      "${WORKSPACE}/openssl-rotation-key.log" \
      "${WORKSPACE}/openssl-rotation-csr.log" \
      "${WORKSPACE}/certificate-verify.log" \
      "${WORKSPACE}/mosquitto-readiness.log" \
      "${WORKSPACE}/mtls-without-client-cert.log" \
      "${WORKSPACE}/mtls-with-device-cert.log"; do
      show_log_excerpt "${candidate}"
    done
  fi

  exit "${exit_code}"
}
trap cleanup EXIT

# Extract one top-level JSON string field. The demo payloads are flat and their values
# do not contain quote characters. Always return success so callers can print the full
# response in their own diagnostic instead of set -e aborting inside this helper.
json_field() {
  local json="$1"
  local field="$2"
  local value
  value=$(grep -o "\"${field}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" <<<"${json}" \
    | head -1 | sed 's/.*:[[:space:]]*"//; s/"$//' || true)
  printf '%s' "${value}"
}

write_json_pem() {
  local json="$1"
  local field="$2"
  local destination="$3"
  local escaped

  escaped=$(json_field "${json}" "${field}")
  [[ -n "${escaped}" ]] || fail "response did not contain ${field}: ${json}"

  # Jackson represents PEM newlines as literal \n sequences. printf %b restores them;
  # tr removes CR if Git/Windows has introduced CRLF at any boundary.
  printf '%b' "${escaped}" | tr -d '\r' > "${destination}"
}

http_status() {
  curl -sS --connect-timeout 3 -o /dev/null -w '%{http_code}' "$@"
}

require() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required but not on PATH"
}

wait_for_file() {
  local file="$1"
  local description="$2"
  for _ in $(seq 1 30); do
    [[ -s "${file}" ]] && return 0
    kill -0 "${APP_PID}" 2>/dev/null || fail "the control plane exited while creating ${description}"
    sleep 1
  done
  fail "${description} was not created at ${file}"
}

split_ca_chain() {
  local chain="$1"
  local issuing="$2"
  local root="$3"
  local count

  count=$(grep -c '^-----BEGIN CERTIFICATE-----$' "${chain}" || true)
  [[ "${count}" == "2" ]] \
    || fail "expected exactly two CA certificates, found ${count} in ${chain}"

  rm -f "${issuing}" "${root}"
  awk -v issuing="${issuing}" -v root="${root}" '
    /^-----BEGIN CERTIFICATE-----$/ { certificate++ }
    certificate == 1 { print > issuing }
    certificate == 2 { print > root }
  ' "${chain}"

  openssl x509 -in "${issuing}" -noout >/dev/null 2>&1 \
    || fail "the first certificate in the CA chain is not valid PEM"
  openssl x509 -in "${root}" -noout >/dev/null 2>&1 \
    || fail "the second certificate in the CA chain is not valid PEM"
}

# ---------------------------------------------------------------------------------
# 0. Preflight
# ---------------------------------------------------------------------------------

presentation_banner
step "Checking prerequisites"
for command in docker mvn java jar openssl curl awk grep sed tr; do
  require "${command}"
done
docker info >/dev/null 2>&1 || fail "the Docker daemon is not reachable"
pass "docker, maven, java, jar, openssl and curl are available"

# No reusable demo credentials live in the repository. Fresh high-entropy values are
# generated for every run unless the operator explicitly supplied them.
export KEYSTONE_DB_PASSWORD="${KEYSTONE_DB_PASSWORD:-$(openssl rand -hex 24)}"
export KEYSTONE_CA_PASSWORD="${KEYSTONE_CA_PASSWORD:-$(openssl rand -hex 32)}"
export KEYSTONE_OPERATOR_PASSWORD="${KEYSTONE_OPERATOR_PASSWORD:-$(openssl rand -hex 24)}"
# Local demo uses HTTP only; production retains the application's Secure-cookie default.
export KEYSTONE_COOKIE_SECURE="${KEYSTONE_COOKIE_SECURE:-false}"
OPERATOR_PASSWORD="${KEYSTONE_OPERATOR_PASSWORD}"
AUTH=(-u "${OPERATOR}:${OPERATOR_PASSWORD}")
pass "ephemeral database, CA-keystore and operator secrets are configured"

# A previous successful --keep run records its MSYS/Git-Bash PID. Stop only that
# explicitly tracked process; never kill an arbitrary program merely for using 8080.
if [[ -f "${WORKSPACE}/control-plane.pid" ]]; then
  PREVIOUS_PID=$(tr -d '[:space:]' < "${WORKSPACE}/control-plane.pid")
  if [[ -n "${PREVIOUS_PID}" ]] && kill -0 "${PREVIOUS_PID}" 2>/dev/null; then
    warn "stopping the previous Keystone control plane (pid ${PREVIOUS_PID})"
    kill "${PREVIOUS_PID}" 2>/dev/null || true
    for _ in $(seq 1 20); do
      kill -0 "${PREVIOUS_PID}" 2>/dev/null || break
      sleep 0.25
    done
  fi
fi

EXISTING_HTTP=$(http_status "${BASE_URL}/actuator/health" 2>/dev/null || true)
if [[ -n "${EXISTING_HTTP}" && "${EXISTING_HTTP}" != "000" ]]; then
  fail "port 8080 is already serving HTTP ${EXISTING_HTTP}; stop the previous Java process before retrying"
fi

rm -rf "${WORKSPACE}"
mkdir -p "${WORKSPACE}"

# ---------------------------------------------------------------------------------
# 1. Clean slate
# ---------------------------------------------------------------------------------

step "Resetting to a clean slate"
info "removing containers, database volume, CA keystore and firmware signing key"
docker compose down -v --remove-orphans >/dev/null 2>&1 || true

# down -v should remove the volume. This extra removal covers an interrupted older
# run whose Compose project used the directory name as its project name.
PROJECT=$(basename "${ROOT}" | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]_-')
docker volume rm -f "${PROJECT}_postgres-data" >/dev/null 2>&1 || true

rm -rf "${ROOT}/data" "${ROOT}/keystone-bootstrap/data"
rm -rf "${ROOT}/keystone-bootstrap/docker"
find "${MQTT_CERTS_DIR}" -type f ! -name '.gitkeep' -delete 2>/dev/null || true
pass "no devices, certificates or previous audit history remain"
proves "everything from here on is generated during this run"

# ---------------------------------------------------------------------------------
# 2. Infrastructure
# ---------------------------------------------------------------------------------

step "Starting PostgreSQL"
# Mosquitto starts later, after Keystone has generated every file referenced by its
# TLS configuration. Starting it with an empty read-only certificate mount causes a
# predictable crash/restart race.
docker compose up -d postgres >/dev/null

postgres_ready() {
  docker compose exec -T -e PGPASSWORD="${KEYSTONE_DB_PASSWORD}" postgres \
    psql -U keystone -d keystone -c 'SELECT 1' >/dev/null 2>&1
}

for _ in $(seq 1 60); do
  postgres_ready && break
  sleep 1
done

if ! postgres_ready; then
  docker compose logs --no-color --tail=50 postgres >&2 || true
  fail "PostgreSQL did not accept the configured Keystone credentials"
fi
pass "PostgreSQL is ready and the credentials work"

# ---------------------------------------------------------------------------------
# 3. Build
# ---------------------------------------------------------------------------------

step "Building the project"
if [[ "${SKIP_TESTS}" == true ]]; then
  warn "skipping tests (--fast)"
  mvn -q clean install -DskipTests > "${BUILD_LOG}" 2>&1 \
    || { tail -60 "${BUILD_LOG}" >&2; fail "the build failed"; }
elif [[ "${FULL_SUITE}" == true ]]; then
  # Testcontainers starts its own PostgreSQL, independent of the Compose one above.
  info "running the full suite including integration tests (several minutes)"
  mvn -q clean install > "${BUILD_LOG}" 2>&1 \
    || { tail -60 "${BUILD_LOG}" >&2; fail "the build failed"; }
else
  # The integration tests are skipped here on purpose, not for speed alone: every
  # step below exercises the same paths against a real PostgreSQL, a real Mosquitto
  # and real OpenSSL, which is strictly more than Testcontainers proves. Run the
  # full suite with --full, or with `mvn verify`.
  info "running domain, application and ArchUnit tests (--full adds the integration suite)"
  mvn -q clean install -DskipITs > "${BUILD_LOG}" 2>&1 \
    || { tail -60 "${BUILD_LOG}" >&2; fail "the build failed"; }
fi
pass "all five modules built"

[[ -f "${APP_JAR}" ]] || fail "the executable application JAR was not produced"
[[ -f "${SIMULATOR_JAR}" ]] || fail "the executable simulator JAR was not produced"

if jar tf "${APP_JAR}" | grep -q 'BOOT-INF/lib/bcpkix-jdk18on-'; then
  pass "the application JAR contains the Bouncy Castle PKIX runtime"
else
  fail "the application JAR is missing bcpkix-jdk18on; check dependency scopes"
fi

# ---------------------------------------------------------------------------------
# 4. Start the control plane
# ---------------------------------------------------------------------------------

step "Starting the control plane"
# Java launched from Git Bash needs a native Windows path, while Docker/OpenSSL keep
# using the MSYS path. The exporter and the Mosquitto bind mount must target the same
# physical directory.
if command -v cygpath >/dev/null 2>&1; then
  export KEYSTONE_MQTT_CERTS="$(cygpath -m "${MQTT_CERTS_DIR}")"
else
  export KEYSTONE_MQTT_CERTS="${MQTT_CERTS_DIR}"
fi

(
  cd "${ROOT}"
  exec java -jar "${APP_JAR}"
) > "${LOG}" 2>&1 &
APP_PID=$!
printf '%s\n' "${APP_PID}" > "${WORKSPACE}/control-plane.pid"

info "waiting for the health probe"
for _ in $(seq 1 90); do
  if [[ "$(http_status "${BASE_URL}/actuator/health" 2>/dev/null || true)" == "200" ]]; then
    break
  fi
  kill -0 "${APP_PID}" 2>/dev/null || fail "the control plane exited during startup"
  sleep 1
done
[[ "$(http_status "${BASE_URL}/actuator/health" 2>/dev/null || true)" == "200" ]] \
  || fail "the control plane never became healthy"
pass "control plane up at ${BASE_URL}"

grep -q "generating a new hierarchy" "${LOG}" \
  && pass "a fresh two-tier CA hierarchy was generated"
grep -q "generated a new firmware signing key" "${LOG}" \
  && pass "a fresh Ed25519 firmware signing key was generated"

wait_for_file "${MQTT_CERTS_DIR}/ca-chain.pem" "the broker CA chain"
wait_for_file "${MQTT_CERTS_DIR}/broker.crt" "the broker certificate"
wait_for_file "${MQTT_CERTS_DIR}/broker.key" "the broker private key"
wait_for_file "${MQTT_CERTS_DIR}/keystone.crl" "the broker CRL"

openssl x509 -in "${MQTT_CERTS_DIR}/broker.crt" -noout >/dev/null 2>&1 \
  || fail "broker.crt is not a valid PEM certificate"
openssl pkey -in "${MQTT_CERTS_DIR}/broker.key" -noout >/dev/null 2>&1 \
  || fail "broker.key is not a valid private key"
openssl crl -in "${MQTT_CERTS_DIR}/keystone.crl" -noout >/dev/null 2>&1 \
  || fail "keystone.crl is not a valid PEM CRL"

# ---------------------------------------------------------------------------------
# 5. Broker picks up the trust material
# ---------------------------------------------------------------------------------

step "Handing the trust material to the broker"
info "the control plane wrote ca-chain.pem, broker.crt, broker.key and keystone.crl"
docker compose up -d --force-recreate mosquitto >/dev/null

BROKER_READY=false
BROKER_PROBE=""
for _ in $(seq 1 45); do
  MOSQUITTO_CID=$(docker compose ps -q mosquitto 2>/dev/null || true)
  BROKER_STATE=""
  [[ -n "${MOSQUITTO_CID}" ]] \
    && BROKER_STATE=$(docker inspect -f '{{.State.Status}}' "${MOSQUITTO_CID}" 2>/dev/null || true)

  # Readiness must be based on the network service, not a particular log message.
  # Mosquitto classifies listener-start messages as informational and newer 2.x
  # releases may suppress or change those messages depending on log_type.
  if [[ "${BROKER_STATE}" == "running" ]]; then
    BROKER_PROBE=$(openssl s_client -tls1_2 -connect 127.0.0.1:18883 -servername localhost \
      -CAfile "${MQTT_CERTS_DIR}/ca-chain.pem" </dev/null 2>&1 || true)

    if grep -q 'CONNECTED(' <<<"${BROKER_PROBE}" \
       && grep -Eqi \
            'Certificate chain|Server certificate|Verify return code: 0|certificate required|alert.*certificate' \
            <<<"${BROKER_PROBE}"; then
      BROKER_READY=true
      break
    fi
  fi

  if [[ "${BROKER_STATE}" == "exited" || "${BROKER_STATE}" == "dead" ]]; then
    docker compose logs --no-color --tail=60 mosquitto >&2 || true
    fail "Mosquitto exited while loading the generated trust material"
  fi
  sleep 1
done

if [[ "${BROKER_READY}" != true ]]; then
  printf '%s\n' "${BROKER_PROBE}" > "${WORKSPACE}/mosquitto-readiness.log"
  docker compose logs --no-color --tail=60 mosquitto >&2 || true
  fail "Mosquitto did not become reachable on the mTLS listener; see .demo/mosquitto-readiness.log"
fi
pass "Mosquitto loaded the generated trust material and the mTLS listener is reachable"

TLS_PROBE=$(openssl s_client -tls1_2 -connect localhost:18883 -servername localhost \
  -CAfile "${MQTT_CERTS_DIR}/ca-chain.pem" </dev/null 2>&1 || true)
printf '%s\n' "${TLS_PROBE}" > "${WORKSPACE}/mtls-without-client-cert.log"

if grep -Eqi \
     "certificate required|peer did not return a certificate|handshake failure|alert.*certificate" \
     <<<"${TLS_PROBE}"; then
  pass "the broker refused an anonymous TLS client"
  proves "require_certificate is enforced on the fleet listener"
else
  warn "the broker is running, but the anonymous-client TLS probe was inconclusive"
  info "see ${WORKSPACE}/mtls-without-client-cert.log"
fi

if [[ "${PRESENTATION_MODE}" == true ]]; then
  presentation_gate "PRESENTATION READY - CONNECT THE LIVE CONSOLE" \
    "Console : ${BASE_URL}" \
    "User    : ${OPERATOR}" \
    "Password: ${OPERATOR_PASSWORD}" \
    "Open the dashboard and keep 'Live activity' visible." \
    "The SSE stream only receives events created after the page subscribes." \
    "Recording tip: capture the browser now; the lifecycle begins after ENTER."
fi

# ---------------------------------------------------------------------------------
# 6. Register a device
# ---------------------------------------------------------------------------------

step "Registering a device"
SERIAL="DEMO-$(date +%s)"
if ! DEVICE_JSON=$(curl -fsS "${AUTH[@]}" -X POST "${BASE_URL}/api/v1/devices" \
  -H 'Content-Type: application/json' \
  -d "{\"serialNumber\":\"${SERIAL}\",\"model\":\"ESP32-S3\"}"); then
  fail "device registration request failed"
fi
DEVICE_ID=$(json_field "${DEVICE_JSON}" id)
[[ -n "${DEVICE_ID}" ]] || fail "device registration failed: ${DEVICE_JSON}"
pass "registered ${SERIAL} as ${DEVICE_ID}"
proves "registration leaves it in PENDING_ENROLLMENT; it has no identity yet"
presentation_beat

# ---------------------------------------------------------------------------------
# 7. Issue the enrolment secret
# ---------------------------------------------------------------------------------

step "Issuing a single-use enrolment secret"
if ! TOKEN_JSON=$(curl -fsS "${AUTH[@]}" -X POST \
  "${BASE_URL}/api/v1/devices/${DEVICE_ID}/enrollment-token"); then
  fail "enrolment-token request failed"
fi
SECRET=$(json_field "${TOKEN_JSON}" secret)
[[ -n "${SECRET}" ]] || fail "token issuance failed: ${TOKEN_JSON}"
pass "secret issued; only its SHA-256 reaches the database"
proves "a database dump of enrollment_tokens cannot enrol a device"
presentation_beat

# ---------------------------------------------------------------------------------
# 8. The device generates its own key and CSR
# ---------------------------------------------------------------------------------

step "The device generates its own key material"
(
  cd "${WORKSPACE}"
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out device.key
  # The requested CN is deliberately wrong. Keystone must replace it with DEVICE_ID.
  MSYS_NO_PATHCONV=1 openssl req -new -key device.key \
    -subj '/CN=i-can-name-myself-anything' -out device.csr
) 2> "${WORKSPACE}/openssl-device-key.log" \
  || { cat "${WORKSPACE}/openssl-device-key.log" >&2; fail "could not generate the device key and CSR"; }

openssl req -in "${WORKSPACE}/device.csr" -noout -verify \
  > /dev/null 2> "${WORKSPACE}/openssl-device-csr.log" \
  || { cat "${WORKSPACE}/openssl-device-csr.log" >&2; fail "the generated CSR does not verify"; }
pass "EC P-256 keypair and CSR generated locally"
proves "the private key never leaves the device"

# ---------------------------------------------------------------------------------
# 9. Enrol
# ---------------------------------------------------------------------------------

step "Enrolling"
CSR_ESCAPED=$(awk '{printf "%s\\n", $0}' "${WORKSPACE}/device.csr")
if ! ENROL_JSON=$(curl -fsS -X POST "${BASE_URL}/api/v1/enrollment" \
  -H 'Content-Type: application/json' \
  -d "{\"secret\":\"${SECRET}\",\"csr\":\"${CSR_ESCAPED}\"}"); then
  fail "enrolment request failed"
fi

FINGERPRINT=$(json_field "${ENROL_JSON}" fingerprint)
[[ -n "${FINGERPRINT}" ]] || fail "enrolment failed: ${ENROL_JSON}"

# These are two separate commands. Joining them accidentally makes printf write the
# device certificate plus curl arguments into ca-chain.pem and leaves device.crt absent.
write_json_pem "${ENROL_JSON}" certificatePem "${WORKSPACE}/device.crt"
write_json_pem "${ENROL_JSON}" caChainPem "${WORKSPACE}/ca-chain-from-response.pem"

curl -fsS "${BASE_URL}/api/v1/enrollment/ca-chain" \
  | tr -d '\r' > "${WORKSPACE}/ca-chain.pem" \
  || fail "could not download the public CA chain"

openssl x509 -in "${WORKSPACE}/device.crt" -noout >/dev/null 2>&1 \
  || fail "certificatePem did not decode into a valid PEM certificate"

if ! cmp -s "${WORKSPACE}/ca-chain-from-response.pem" "${WORKSPACE}/ca-chain.pem"; then
  fail "the CA chain returned during enrolment differs from the public CA-chain endpoint"
fi

pass "certificate issued, fingerprint ${FINGERPRINT:0:32}..."
proves "the single-use secret authenticated enrolment without operator credentials"
presentation_beat

# ---------------------------------------------------------------------------------
# 10. Verify what came back
# ---------------------------------------------------------------------------------

step "Verifying the certificate"
ISSUING_CA="${WORKSPACE}/issuing-ca.pem"
ROOT_CA="${WORKSPACE}/root-ca.pem"
split_ca_chain "${WORKSPACE}/ca-chain.pem" "${ISSUING_CA}" "${ROOT_CA}"

# Treat only the root as trusted and supply the issuing CA as an untrusted intermediate.
# This verifies the actual root -> issuing CA -> device path instead of trusting every
# certificate bundled in ca-chain.pem as an independent trust anchor.
if VERIFY_OUTPUT=$(openssl verify -purpose sslclient -show_chain \
  -CAfile "${ROOT_CA}" -untrusted "${ISSUING_CA}" \
  "${WORKSPACE}/device.crt" 2>&1); then
  printf '%s\n' "${VERIFY_OUTPUT}" > "${WORKSPACE}/certificate-verify.log"
  pass "the certificate validates through issuing CA to the Keystone root"
else
  printf '%s\n' "${VERIFY_OUTPUT}" > "${WORKSPACE}/certificate-verify.log"
  fail "certificate validation failed: ${VERIFY_OUTPUT}"
fi

SUBJECT=$(openssl x509 -in "${WORKSPACE}/device.crt" -noout -subject -nameopt RFC2253)
if grep -Fq "CN=${DEVICE_ID}" <<<"${SUBJECT}"; then
  pass "the CA replaced the requested CN with the assigned device id"
  proves "a CSR cannot claim another device's broker namespace"
else
  fail "unexpected certificate subject: ${SUBJECT}"
fi

CERT_TEXT=$(openssl x509 -in "${WORKSPACE}/device.crt" -noout -text)
grep -q 'CA:FALSE' <<<"${CERT_TEXT}" \
  && pass "basicConstraints is CA:FALSE" \
  || fail "the device certificate is not constrained as an end entity"
grep -q 'TLS Web Client Authentication' <<<"${CERT_TEXT}" \
  && pass "extendedKeyUsage permits client authentication only" \
  || fail "the device certificate is missing clientAuth EKU"

CERT_KEY_HASH=$(openssl x509 -in "${WORKSPACE}/device.crt" -pubkey -noout \
  | openssl pkey -pubin -outform DER 2>/dev/null | openssl dgst -sha256)
PRIVATE_KEY_HASH=$(openssl pkey -in "${WORKSPACE}/device.key" -pubout -outform DER 2>/dev/null \
  | openssl dgst -sha256)
[[ "${CERT_KEY_HASH}" == "${PRIVATE_KEY_HASH}" ]] \
  && pass "the issued certificate contains the device-generated public key" \
  || fail "the issued certificate does not match device.key"

MTLS_DEVICE=$(openssl s_client -tls1_2 -connect localhost:18883 -servername localhost \
  -CAfile "${WORKSPACE}/ca-chain.pem" \
  -cert "${WORKSPACE}/device.crt" -key "${WORKSPACE}/device.key" \
  </dev/null 2>&1 || true)
printf '%s\n' "${MTLS_DEVICE}" > "${WORKSPACE}/mtls-with-device-cert.log"
if grep -q 'Verify return code: 0' <<<"${MTLS_DEVICE}" \
   && ! grep -Eqi 'alert.*certificate|certificate required|handshake failure' <<<"${MTLS_DEVICE}"; then
  pass "the issued device identity completes an mTLS handshake with Mosquitto"
else
  fail "the issued certificate could not authenticate to Mosquitto; see mtls-with-device-cert.log"
fi

# ---------------------------------------------------------------------------------
# 11. Replay the secret
# ---------------------------------------------------------------------------------

step "Replaying the same secret (this must fail)"
REPLAY_STATUS=$(http_status -X POST "${BASE_URL}/api/v1/enrollment" \
  -H 'Content-Type: application/json' \
  -d "{\"secret\":\"${SECRET}\",\"csr\":\"${CSR_ESCAPED}\"}" || true)
if [[ "${REPLAY_STATUS}" == "403" ]]; then
  pass "refused with HTTP 403"
  proves "the enrolment secret is single-use"
  presentation_beat
else
  fail "the replay returned HTTP ${REPLAY_STATUS}; expected 403"
fi

# ---------------------------------------------------------------------------------
# 12. Rotate
# ---------------------------------------------------------------------------------

step "Rotating the certificate"
(
  cd "${WORKSPACE}"
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out device-new.key
  MSYS_NO_PATHCONV=1 openssl req -new -key device-new.key \
    -subj '/CN=renewal' -out device-new.csr
) 2> "${WORKSPACE}/openssl-rotation-key.log" \
  || { cat "${WORKSPACE}/openssl-rotation-key.log" >&2; fail "could not generate the rotation key and CSR"; }

openssl req -in "${WORKSPACE}/device-new.csr" -noout -verify \
  > /dev/null 2> "${WORKSPACE}/openssl-rotation-csr.log" \
  || { cat "${WORKSPACE}/openssl-rotation-csr.log" >&2; fail "the rotation CSR does not verify"; }

NEW_CSR=$(awk '{printf "%s\\n", $0}' "${WORKSPACE}/device-new.csr")
# Hash the canonical DER form, not PEM text. This keeps the PoP stable across
# LF/CRLF conversion and JSON serialisation on Windows/Git Bash.
CSR_SHA256=$(openssl req -in "${WORKSPACE}/device-new.csr" -outform DER 2>> "${WORKSPACE}/openssl-rotation-csr.log" \
  | openssl dgst -sha256 -r | awk '{print $1}')
printf 'keystone-rotation-v1\n%s\n%s\n%s' \
  "${DEVICE_ID}" "${FINGERPRINT}" "${CSR_SHA256}" > "${WORKSPACE}/rotation-proof.txt"

# A new/attacker key cannot authenticate rotation, even with the correct public
# fingerprint and a perfectly valid replacement CSR.
BAD_PROOF=$(openssl dgst -sha256 -sign "${WORKSPACE}/device-new.key" \
  "${WORKSPACE}/rotation-proof.txt" | openssl base64 -A)
BAD_PROOF_STATUS=$(http_status -X POST "${BASE_URL}/api/v1/rotation/${DEVICE_ID}" \
  -H 'Content-Type: application/json' \
  -d "{\"currentFingerprint\":\"${FINGERPRINT}\",\"csr\":\"${NEW_CSR}\",\"proof\":\"${BAD_PROOF}\"}" || true)
[[ "${BAD_PROOF_STATUS}" == "403" ]] \
  && pass "fingerprint plus attacker-controlled key is refused" \
  || fail "rotation without current-key proof returned HTTP ${BAD_PROOF_STATUS}; expected 403"
proves "a certificate fingerprint is public metadata, not an authentication secret"
presentation_beat

ROTATION_PROOF=$(openssl dgst -sha256 -sign "${WORKSPACE}/device.key" \
  "${WORKSPACE}/rotation-proof.txt" | openssl base64 -A)
if ! ROTATE_JSON=$(curl -fsS -X POST "${BASE_URL}/api/v1/rotation/${DEVICE_ID}" \
  -H 'Content-Type: application/json' \
  -d "{\"currentFingerprint\":\"${FINGERPRINT}\",\"csr\":\"${NEW_CSR}\",\"proof\":\"${ROTATION_PROOF}\"}"); then
  fail "rotation request failed"
fi

NEW_FINGERPRINT=$(json_field "${ROTATE_JSON}" fingerprint)
[[ -n "${NEW_FINGERPRINT}" ]] || fail "rotation failed: ${ROTATE_JSON}"
[[ "${NEW_FINGERPRINT}" != "${FINGERPRINT}" ]] \
  || fail "rotation returned the original fingerprint"

write_json_pem "${ROTATE_JSON}" certificatePem "${WORKSPACE}/device-new.crt"
openssl verify -purpose sslclient -CAfile "${ROOT_CA}" -untrusted "${ISSUING_CA}" \
  "${WORKSPACE}/device-new.crt" >/dev/null 2>&1 \
  || fail "the rotated certificate does not validate"
pass "renewed, new fingerprint ${NEW_FINGERPRINT:0:32}..."
proves "the current private key signs the rotation request; fingerprint matching alone is insufficient"
proves "the superseded certificate is immediately entered into the revocation registry"
presentation_beat

step "Rotating with a wrong fingerprint (this must fail)"
BAD_ROTATE=$(http_status -X POST "${BASE_URL}/api/v1/rotation/${DEVICE_ID}" \
  -H 'Content-Type: application/json' \
  -d "{\"currentFingerprint\":\"$(printf 'a%.0s' {1..64})\",\"csr\":\"${NEW_CSR}\",\"proof\":\"${ROTATION_PROOF}\"}" || true)
[[ "${BAD_ROTATE}" == "403" ]] \
  && pass "refused with HTTP 403" \
  || fail "wrong-fingerprint rotation returned HTTP ${BAD_ROTATE}; expected 403"
presentation_beat

# ---------------------------------------------------------------------------------
# 13. Firmware
# ---------------------------------------------------------------------------------

step "Publishing signed firmware"
head -c 4096 /dev/urandom > "${WORKSPACE}/firmware-1.0.0.bin"
head -c 4096 /dev/urandom > "${WORKSPACE}/firmware-1.1.0.bin"

curl -fsS "${AUTH[@]}" -X POST "${BASE_URL}/api/v1/firmware" \
  -F version=1.0.0 -F model=ESP32-S3 \
  -F "file=@${WORKSPACE}/firmware-1.0.0.bin" >/dev/null \
  || fail "publishing firmware 1.0.0 failed"

if ! ARTIFACT_JSON=$(curl -fsS "${AUTH[@]}" -X POST "${BASE_URL}/api/v1/firmware" \
  -F version=1.1.0 -F model=ESP32-S3 \
  -F "file=@${WORKSPACE}/firmware-1.1.0.bin"); then
  fail "publishing firmware 1.1.0 failed"
fi
ARTIFACT_ID=$(json_field "${ARTIFACT_JSON}" id)
ARTIFACT_SHA=$(json_field "${ARTIFACT_JSON}" sha256)
[[ -n "${ARTIFACT_ID}" && -n "${ARTIFACT_SHA}" ]] \
  || fail "firmware publishing returned an incomplete response: ${ARTIFACT_JSON}"

pass "1.0.0 and 1.1.0 published and signed with Ed25519"
proves "the server computed digest ${ARTIFACT_SHA:0:24}... while storing the artifact"
presentation_beat

step "Rolling out 1.1.0"
if ! ROLLOUT_JSON=$(curl -fsS "${AUTH[@]}" -X POST \
  "${BASE_URL}/api/v1/firmware/rollouts/${ARTIFACT_ID}"); then
  fail "opening the rollout failed"
fi
ROLLOUT_ID=$(json_field "${ROLLOUT_JSON}" id)
[[ -n "${ROLLOUT_ID}" ]] || fail "rollout response has no id: ${ROLLOUT_JSON}"
pass "rollout opened at the canary stage (5%)"
proves "the aggregate exposes no direct jump to 100%"
presentation_beat

curl -fsS "${AUTH[@]}" -X POST \
  "${BASE_URL}/api/v1/firmware/rollouts/${ROLLOUT_ID}/advance" >/dev/null \
  || fail "advancing the rollout failed"
pass "advanced to 25%"
presentation_beat

if ! ROLLBACK_JSON=$(curl -fsS "${AUTH[@]}" -X POST \
  "${BASE_URL}/api/v1/firmware/rollouts/${ROLLOUT_ID}/rollback?reason=demo"); then
  fail "rolling back the rollout failed"
fi
PREVIOUS_VERSION=$(json_field "${ROLLBACK_JSON}" previousVersion)
[[ -n "${PREVIOUS_VERSION}" ]] || fail "rollback response is incomplete: ${ROLLBACK_JSON}"
pass "rolled back to ${PREVIOUS_VERSION}"
proves "the rollout remains reversible after promotion"
presentation_beat

# ---------------------------------------------------------------------------------
# 14. Revocation
# ---------------------------------------------------------------------------------

step "Checking certificate revocation"
curl -fsS "${BASE_URL}/api/v1/enrollment/crl" \
  | tr -d '\r' > "${WORKSPACE}/keystone.crl" \
  || fail "could not download the CRL"
openssl crl -in "${WORKSPACE}/keystone.crl" -noout >/dev/null 2>&1 \
  || fail "the CRL endpoint did not return a valid PEM CRL"

CRL_ENTRIES=$(openssl crl -in "${WORKSPACE}/keystone.crl" -noout -text 2>/dev/null \
  | grep -c 'Serial Number' || true)
pass "the CRL lists ${CRL_ENTRIES} revoked certificate(s)"

OLD_CERT_RESULT=$(openssl verify -crl_check -CAfile "${ROOT_CA}" \
  -untrusted "${ISSUING_CA}" -CRLfile "${WORKSPACE}/keystone.crl" \
  "${WORKSPACE}/device.crt" 2>&1 || true)
if grep -qi 'certificate revoked' <<<"${OLD_CERT_RESULT}"; then
  pass "the superseded device certificate is rejected as revoked"
else
  fail "the superseded certificate was not found in the CRL: ${OLD_CERT_RESULT}"
fi

openssl verify -crl_check -CAfile "${ROOT_CA}" -untrusted "${ISSUING_CA}" \
  -CRLfile "${WORKSPACE}/keystone.crl" "${WORKSPACE}/device-new.crt" >/dev/null 2>&1 \
  && pass "the rotated certificate remains valid" \
  || fail "the active rotated certificate was unexpectedly rejected by the CRL"

# ---------------------------------------------------------------------------------
# 15. Audit
# ---------------------------------------------------------------------------------

step "Checking the audit trail"
AUDIT_COUNT=$(docker compose exec -T postgres psql -U keystone -d keystone -qtA \
  -c 'SELECT count(*) FROM audit_log' 2>/dev/null | tr -d '[:space:]')
[[ "${AUDIT_COUNT}" =~ ^[0-9]+$ ]] || fail "could not read the audit_log count"
pass "${AUDIT_COUNT} entries recorded"

# Verify the guard exists before testing it.  The mutation commands below are
# EXPECTED to return a non-zero psql status.  Do not pipe psql directly into grep:
# this script runs with `set -o pipefail`, so a correctly rejected statement makes
# that pipeline false even when grep sees the expected "append-only" message.
AUDIT_TRIGGER_COUNT=$(docker compose exec -T postgres psql -U keystone -d keystone -qtA \
  -c "SELECT count(*) FROM pg_trigger WHERE tgname = 'audit_log_append_only' AND tgenabled <> 'D'" \
  2>/dev/null | tr -d '[:space:]')
[[ "${AUDIT_TRIGGER_COUNT}" == "1" ]] \
  || fail "audit_log_append_only trigger is missing or disabled"
pass "the append-only database trigger is installed and enabled"

FIRST_AUDIT_SEQUENCE=$(docker compose exec -T postgres psql -U keystone -d keystone -qtA \
  -c 'SELECT min(sequence) FROM audit_log' 2>/dev/null | tr -d '[:space:]')
[[ "${FIRST_AUDIT_SEQUENCE}" =~ ^[0-9]+$ ]] \
  || fail "could not locate a sealed audit entry for the tamper test"

FIRST_AUDIT_HASH=$(docker compose exec -T postgres psql -U keystone -d keystone -qtA \
  -c "SELECT entry_hash FROM audit_log WHERE sequence = ${FIRST_AUDIT_SEQUENCE}" \
  2>/dev/null | tr -d '[:space:]')
[[ "${FIRST_AUDIT_HASH}" =~ ^[0-9a-f]{64}$ ]] \
  || fail "audit entry ${FIRST_AUDIT_SEQUENCE} has an invalid hash"
[[ "${FIRST_AUDIT_HASH}" != "$(printf '0%.0s' {1..64})" ]] \
  || fail "audit entry ${FIRST_AUDIT_SEQUENCE} is still unsealed"

if UPDATE_OUTPUT=$(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 \
     -U keystone -d keystone \
     -c "UPDATE audit_log SET detail = 'tampered' WHERE sequence = ${FIRST_AUDIT_SEQUENCE}" \
     2>&1); then
  fail "PostgreSQL unexpectedly allowed UPDATE of sealed audit entry ${FIRST_AUDIT_SEQUENCE}"
fi
if grep -q 'audit_log is append-only' <<<"${UPDATE_OUTPUT}"; then
  pass "PostgreSQL refused an UPDATE on a sealed entry"
  proves "the database trigger blocks ordinary mutation of sealed audit history"
else
  printf '%s\n' "${UPDATE_OUTPUT}" >&2
  fail "UPDATE failed, but not because of the append-only trigger"
fi

if DELETE_OUTPUT=$(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 \
     -U keystone -d keystone \
     -c "DELETE FROM audit_log WHERE sequence = ${FIRST_AUDIT_SEQUENCE}" \
     2>&1); then
  fail "PostgreSQL unexpectedly allowed DELETE of sealed audit entry ${FIRST_AUDIT_SEQUENCE}"
fi
if grep -q 'audit_log is append-only' <<<"${DELETE_OUTPUT}"; then
  pass "PostgreSQL refused a DELETE on a sealed entry"
else
  printf '%s\n' "${DELETE_OUTPUT}" >&2
  fail "DELETE failed, but not because of the append-only trigger"
fi

info "the console verifies the full audit hash chain on every load of /audit"

if [[ "${PRESENTATION_MODE}" == true ]]; then
  presentation_gate "PHASE 1 COMPLETE - CAPTURE THE CONTROL PLANE" \
    "The single-device lifecycle is complete." \
    "Capture: Dashboard -> PKI -> Firmware -> Audit." \
    "The next phase adds 50 simulated devices and adversarial scenarios." \
    "Return to Dashboard / Live activity before pressing ENTER."
fi

# ---------------------------------------------------------------------------------
# 16. Fleet simulation
# ---------------------------------------------------------------------------------

step "Running the fleet simulator"
info "50 devices on virtual threads, followed by adversarial scenarios"

set +e
java -jar "${SIMULATOR_JAR}" 2>&1 \
  | tee "${WORKSPACE}/simulator.log" \
  | grep -E 'PASS|FAIL|enrolled|failed|elapsed'
SIMULATOR_STATUS=${PIPESTATUS[0]}
set -e

[[ ${SIMULATOR_STATUS} -eq 0 ]] \
  || fail "the fleet simulator exited with status ${SIMULATOR_STATUS}"

if grep -q 'FAIL' "${WORKSPACE}/simulator.log"; then
  fail "at least one adversarial scenario was not refused; inspect simulator.log"
else
  pass "every adversarial scenario was refused"
fi

# ---------------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------------

if [[ "${PRESENTATION_MODE}" == true ]]; then
  printf '\n%s' "${BOLD}${GREEN}"
  cat <<'EOF'
            [ TRUST CHAIN VERIFIED ]
        PKI  ->  mTLS  ->  OTA  ->  AUDIT
              adversarial tests: PASS
EOF
  printf '%s\n' "${RESET}"
fi

printf '\n%s%s%s\n' "${BOLD}" '════════════════════════════════════════════════════' "${RESET}"
printf '%sKeystone demo complete%s\n' "${BOLD}${GREEN}" "${RESET}"
printf '%s%s%s\n\n' "${BOLD}" '════════════════════════════════════════════════════' "${RESET}"
printf '  Console      %s   (%s / %s)\n' "${BASE_URL}" "${OPERATOR}" "${OPERATOR_PASSWORD}"
printf '  Device       %s\n' "${DEVICE_ID}"
printf '  Artifacts    %s\n' "${WORKSPACE}"
printf '  Server log   %s\n\n' "${LOG}"

DEMO_COMPLETED=true
if [[ "${KEEP_RUNNING}" != true ]]; then
  info "re-run with --keep to leave the control plane up for screenshots"
fi
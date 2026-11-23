#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${CRAFTPLAN_M1_KEYSTORE_BASE64:?Missing CRAFTPLAN_M1_KEYSTORE_BASE64 secret}"
: "${CRAFTPLAN_M1_KEYSTORE_PASSWORD:?Missing CRAFTPLAN_M1_KEYSTORE_PASSWORD secret}"
: "${CRAFTPLAN_M1_CERT_SHA256:?Missing CRAFTPLAN_M1_CERT_SHA256 variable}"
: "${GITHUB_RUN_NUMBER:?Missing GitHub Actions run number}"

fingerprint="${CRAFTPLAN_M1_CERT_SHA256//:/}"
fingerprint="${fingerprint,,}"
if [[ ! "$fingerprint" =~ ^[0-9a-f]{64}$ || ! "$GITHUB_RUN_NUMBER" =~ ^[1-9][0-9]{0,9}$ ]]; then
    echo 'Invalid pinned certificate fingerprint or Actions run number.' >&2
    exit 1
fi

key_dir="$(mktemp -d "${RUNNER_TEMP:?Missing runner temp directory}/craftplan-m1.XXXXXXXX")"
trap 'rm -f "$key_dir/signing.p12" "$key_dir/certificate.der"; rmdir "$key_dir"' EXIT
export CRAFTPLAN_M1_KEYSTORE="$key_dir/signing.p12"
printf '%s' "$CRAFTPLAN_M1_KEYSTORE_BASE64" | base64 --decode > "$CRAFTPLAN_M1_KEYSTORE"
unset CRAFTPLAN_M1_KEYSTORE_BASE64

# Compare the keystore with the pinned identity before Gradle can use it.
keytool -exportcert -alias craftplan-m1 -storetype PKCS12 -keystore "$CRAFTPLAN_M1_KEYSTORE" \
    -storepass:env CRAFTPLAN_M1_KEYSTORE_PASSWORD -file "$key_dir/certificate.der" >/dev/null
certificate_sum="$(sha256sum "$key_dir/certificate.der")"
if [[ "${certificate_sum%% *}" != "$fingerprint" ]]; then
    echo 'M1 keystore certificate does not match the pinned identity.' >&2
    exit 1
fi

export CRAFTPLAN_M1_VERSION_CODE=$((1000 + GITHUB_RUN_NUMBER))
gradle --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g" :app:assembleM1

build_tools="${ANDROID_HOME:?Missing Android SDK}/build-tools/36.0.0"
apk=app/build/outputs/apk/m1/app-m1.apk
signers="$("$build_tools/apksigner" verify --min-sdk-version 33 --verbose --print-certs "$apk")"
actual_fingerprint="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$signers")"
if [[ "$actual_fingerprint" != "$fingerprint" ]] || ! grep -qx 'Number of signers: 1' <<< "$signers"; then
    echo 'M1 APK signer does not match the pinned identity.' >&2
    exit 1
fi

badging="$("$build_tools/aapt" dump badging "$apk")"
expected_package="package: name='cz.craftplan.app' versionCode='$CRAFTPLAN_M1_VERSION_CODE' "
if [[ "$badging" != "$expected_package"* ]]; then
    echo 'M1 APK package or version code does not match the build.' >&2
    exit 1
fi

# An older queued run must not publish over a newer branch head.
branch_head="$(git ls-remote origin refs/heads/m1/first-dimension)"
if [[ "${branch_head%%$'\t'*}" != "${GITHUB_SHA:?Missing GitHub Actions commit SHA}" ]]; then
    echo 'M1 branch advanced before publication.' >&2
    exit 1
fi

(cd "$(dirname "$apk")" && sha256sum "$(basename "$apk")" > SHA256SUMS.txt)
echo "Verified M1 certificate and versionCode $CRAFTPLAN_M1_VERSION_CODE."

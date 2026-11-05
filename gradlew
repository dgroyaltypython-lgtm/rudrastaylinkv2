#!/usr/bin/env bash
# Lightweight Gradle bootstrap for this project (the standard wrapper JAR is intentionally not required).
set -euo pipefail
GRADLE_VERSION="8.13"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/rudra/gradle-${GRADLE_VERSION}"
if [[ -x "${GRADLE_HOME_DIR}/bin/gradle" ]]; then
  exec "${GRADLE_HOME_DIR}/bin/gradle" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  VERSION_LINE="$(gradle --version | grep -m1 'Gradle ' || true)"
  if [[ "${VERSION_LINE}" == *"${GRADLE_VERSION}"* ]]; then
    exec gradle "$@"
  fi
fi
mkdir -p "$(dirname "${GRADLE_HOME_DIR}")"
ZIP_FILE="$(dirname "${GRADLE_HOME_DIR}")/gradle-${GRADLE_VERSION}-bin.zip"
if [[ ! -f "${ZIP_FILE}" ]]; then
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "Install curl or wget to download Gradle ${GRADLE_VERSION}." >&2
    exit 1
  fi
fi
TMP_DIR="${GRADLE_HOME_DIR}.extract"
rm -rf "${TMP_DIR}"
mkdir -p "${TMP_DIR}"
unzip -q -o "${ZIP_FILE}" -d "${TMP_DIR}"
mkdir -p "$(dirname "${GRADLE_HOME_DIR}")"
rm -rf "${GRADLE_HOME_DIR}"
mv "${TMP_DIR}/gradle-${GRADLE_VERSION}" "${GRADLE_HOME_DIR}"
rmdir "${TMP_DIR}"
exec "${GRADLE_HOME_DIR}/bin/gradle" "$@"

#!/usr/bin/env bash

set -euo pipefail

REMOTE_NAME="${REMOTE_NAME:-origin}"
BRANCH_NAME="${BRANCH_NAME:-$(git branch --show-current)}"
SERVER_USER="${SERVER_USER:-tlexpress}"
SERVER_HOST="${SERVER_HOST:-192.168.0.20}"
REMOTE_DIR="${REMOTE_DIR:-image-search}"

if [[ -z "${BRANCH_NAME}" ]]; then
  echo "Unable to determine the current git branch." >&2
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
  echo "Detected uncommitted changes."
  git status --short
  echo
  read -r -p "Enter commit message for deployment: " COMMIT_MESSAGE

  if [[ -z "${COMMIT_MESSAGE}" ]]; then
    echo "Commit message is required." >&2
    exit 1
  fi

  git add -A
  git commit -m "${COMMIT_MESSAGE}"
fi

echo "Pushing ${BRANCH_NAME} to ${REMOTE_NAME}..."
git push "${REMOTE_NAME}" "${BRANCH_NAME}"

echo "Deploying on ${SERVER_USER}@${SERVER_HOST}:${REMOTE_DIR}..."
ssh "${SERVER_USER}@${SERVER_HOST}" "
  set -euo pipefail
  cd '${REMOTE_DIR}'
  git pull '${REMOTE_NAME}' '${BRANCH_NAME}'
  docker compose up -d --build
"

echo "Deploy finished."

#!/bin/bash
set -e

PUID=${PUID:-1042}
PGID=${PGID:-$PUID}

CURRENT_UID=$(id -u appuser)
CURRENT_GID=$(id -g appuser)

if [ "$PGID" != "$CURRENT_GID" ]; then
    groupmod -o -g "$PGID" appuser
fi

if [ "$PUID" != "$CURRENT_UID" ]; then
    usermod -o -u "$PUID" appuser
fi

chown -R appuser:appuser /app/signed-documents /app/signers-documents /app/temp-documents

exec gosu appuser "$@"

#!/usr/bin/env bash
SSHPASS='!c7W/@8L_*6mEJXQ' sshpass -e ssh -o StrictHostKeyChecking=no -o ConnectTimeout=30 \
  -o PreferredAuthentications=password -o PubkeyAuthentication=no \
  -o ProxyCommand="nc -X connect -x 127.0.0.1:18080 %h %p" \
  root@115.159.221.62 "$@"

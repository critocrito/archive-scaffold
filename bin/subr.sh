make_id() {
  date +%s | sha256sum | cut -c 1-8
}

provision_vps() {
  RUN_ID="$1"
  PLAN="$2"
  RUN_DIR="$PWD/tmp/$RUN_ID"
  RUN_SCRIPT="$RUN_DIR/youtube-dl-wrapper-sudo.sh"
  VPS_STATE="$RUN_DIR/vps-state.json"
  HOSTS_INI="$RUN_DIR/hosts"
  NS="$USER$RUN_ID"
  # Make sure to use $PWD here, so that we use a full path when calling sudo.
  VPN_CONF="$RUN_DIR/$NS.conf"

  mkdir -p "tmp/$RUN_ID"

  echo "Provision VPS for run $RUN_ID."

  clojure -A:provision-vps create -S "$VPS_STATE" -P "$PLAN"

  echo "[all]" > "$HOSTS_INI"
  jq '.[].ip' "$VPS_STATE" | sed 's/^"\(.*\)"$/\1/g' | while read -r ip; do
    echo "$ip namespace=$NS run_dir=$RUN_DIR" >> "$HOSTS_INI"
  done

  echo "Sleeping 60 seconds to allow VPS to settle."
  sleep 60

  ansible-playbook wireguard_ansible/wireguard.yml -u root -i "$HOSTS_INI"

  sudo wireguard_ansible/bin/wg-quick up "$VPN_CONF"

  {
    echo "#!/bin/sh";
    echo "sudo $PWD/bin/youtube-dl-wrapper.sh $NS $USER \$@";
  } > "$RUN_SCRIPT"

  chmod +x "$RUN_SCRIPT"
}

destroy_vps() {
  RUN_ID="$1"
  RUN_DIR="tmp/$RUN_ID"
  VPS_STATE="$RUN_DIR/vps-state.json"
  NS="$USER$RUN_ID"
  # Make sure to use $PWD here, so that we use a full path when calling sudo.
  VPN_CONF="$PWD/$RUN_DIR/$NS.conf"

  echo "Destroy VPS for run $RUN_ID."

  clojure -A:provision-vps destroy -S "$VPS_STATE"
  sudo wireguard_ansible/bin/wg-quick down "$VPN_CONF"

  rm -rf "$RUN_DIR"
}

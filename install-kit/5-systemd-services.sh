#!/bin/bash
echo -e "========= Configuring =========\n"
cd "$(dirname "$0")/files"
interfaces=()
while read -r line; do
    interfaces+=($line "")
done <<< "$(./list-net-interfaces)"
interface="$(whiptail --title "Astronomy Raspberry Pi" --backtitle "By marcocipriani01" \
--menu "Select a newtwork interface:" 15 50 8 "${interfaces[@]}" 3>&1 1>&2 2>&3)"
if [[ -z "$interface" ]]; then
  echo "Error!"
  exit 1
fi
pswd="$(whiptail --title "Astronomy Raspberry Pi" --backtitle "By marcocipriani01" \
--inputbox "Enter a password for the hotspot:" 8 70 3>&1 1>&2 2>&3)"
if [[ -z "$pswd" ]]; then
  echo "Error!"
  exit 2
fi
echo "$interface $(hostname) $pswd" > ../../files/hotspot_config
echo -e "\n========= Enabling systemd services ========="
sudo systemctl stop raspy-indi-controller 2> /dev/null
sudo cp raspy-indi-controller.service /lib/systemd/system/raspy-indi-controller.service
if [[ -n "$(which openfocuser)" ]]; then
  sudo systemctl stop openfocuser 2> /dev/null
  sudo cp openfocuser.service /lib/systemd/system/openfocuser.service
fi
sudo systemctl daemon-reload
sudo systemctl enable raspy-indi-controller
sudo systemctl start raspy-indi-controller
if [[ -n "$(which openfocuser)" ]]; then
  sudo systemctl enable openfocuser
  sudo systemctl start openfocuser
fi

#!/bin/bash
echo -e "========= Configuring ========="
cd "$(dirname "$0")/files"
interfaces=()
while read -r line; do
    interfaces+=($line "")
done <<< "$(./list-net-interfaces)"
interface="$(whiptail --title "Telescope-Pi" --backtitle "By marcocipriani01" \
--menu "Select a newtwork interface:" 15 50 8 "${interfaces[@]}" 3>&1 1>&2 2>&3)"
if [[ -z "$interface" ]]; then
  echo "Error!"
  exit 1
fi
pswd="$(whiptail --title "Telescope-Pi" --backtitle "By marcocipriani01" \
--inputbox "Enter a password for the hotspot:" 8 70 3>&1 1>&2 2>&3)"
if [[ -z "$pswd" ]]; then
  echo "Error!"
  exit 2
fi
echo "$interface $(hostname) $pswd" > ../../files/hotspot_config
echo -e "========= Enabling systemd services ========="
sudo systemctl stop telescope-pi 2> /dev/null
sudo cp telescope-pi.service /lib/systemd/system/telescope-pi.service
if [[ -n "$(which openfocuser)" ]]; then
  sudo systemctl stop openfocuser 2> /dev/null
  sudo cp openfocuser.service /lib/systemd/system/openfocuser.service
fi
sudo systemctl daemon-reload
sudo systemctl enable telescope-pi
sudo systemctl start telescope-pi
if [[ -n "$(which openfocuser)" ]]; then
  sudo systemctl enable openfocuser
  sudo systemctl start openfocuser
fi

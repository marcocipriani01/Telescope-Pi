#!/bin/bash
echo -e "********* WELCOME *********\n"

whiptail --title "Telescope-Pi" --backtitle "By marcocipriani01" \
--msgbox "This script will set up your Raspberry Pi for astronomy. Hit OK to continue." 10 70
name="$(whiptail --title "Astronomy Raspberry Pi" --backtitle "By marcocipriani01" \
--inputbox "Enter device name on network:" 8 70 3>&1 1>&2 2>&3)"
if [[ -z "$name" ]]; then
    echo "Error!"
    exit 1
fi

echo -e "\n========= Setting up nmcli ========="
sudo apt install -y software-properties-common network-manager network-manager-gnome
sudo apt purge openresolv dhcpcd5
sudo ln -sf /lib/systemd/resolv.conf /etc/resolv.conf

echo -e "\n========= Setting hostname ========="
sudo hostnamectl set-hostname "$name"
sudo hostname "$name"
sudo hostname -F /etc/hostname
if [[ -n "$(ls /etc/cloud/cloud.cfg 2> /dev/null)" ]]; then
  sudo sed 's/preserve_hostname: false/preserve_hostname: true/' /etc/cloud/cloud.cfg
fi
sudo service hostname restart
sudo /etc/init.d/networking restart

read -p "Reboot now? (y/n)?" CONT
if [ "$CONT" = "y" ]; then
    sudo reboot now
fi

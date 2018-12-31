#!/bin/bash
echo -e "\n========= Updating system ========="
sudo apt update
sudo apt -y dist-upgrade

echo -e "\n========= Installing packages ========="
sudo apt install -y python-pip avahi-daemon \
python-pip python-dev libbluetooth-dev bluetooth \
bluez rfkill openssh-server tio git gphoto2 openjdk-9-jdk \
cdbs libcfitsio-dev libnova-dev libusb-1.0-0-dev libjpeg-dev libusb-dev \
libtiff5-dev libftdi1-dev fxload libkrb5-dev libcurl4-gnutls-dev libraw-dev \
libgphoto2-dev libgsl-dev dkms libboost-regex-dev libgps-dev \
libdc1394-22-dev libftdi1 python-dbus python-gobject libdbus-1-dev \
libglib2.0-dev libudev-dev libical-dev libreadline-dev

echo -e "\n========= Installing Python packages ========="
sudo -H pip install indiweb pybluez wifi sh

echo -e "\n========= Enabling SSH ========="
sudo systemctl enable ssh
sudo systemctl start ssh

echo -e "\n========= Configuring Bluez ========="
cd "$(dirname "$0")/files"
sudo rfkill block bluetooth
sudo systemctl stop bluetooth
sudo sed -i ':a;N;$!ba;s#ExecStart=/usr/lib/bluetooth/bluetoothd\n#ExecStart=/usr/lib/bluetooth/bluetoothd -C\nExecStartPost=/usr/bin/sdptool add SP\n#g' /etc/systemd/system/dbus-org.bluez.service
sudo cp /lib/systemd/system/bluetooth.service /etc/systemd/system/bluetooth.service
sudo sed -i ':a;N;$!ba;s#ExecStart=/usr/lib/bluetooth/bluetoothd\n#ExecStart=/usr/lib/bluetooth/bluetoothd -C\nExecStartPost=/usr/bin/sdptool add SP\n#g' /etc/systemd/system/bluetooth.service
if [[ -z "$(grep "DisablePlugins = pnat" "/etc/bluetooth/main.conf")" ]]; then
  echo "DisablePlugins = pnat" | sudo tee -a "/etc/bluetooth/main.conf"
fi
sudo sed -i 's#AutoEnable=false#AutoEnable=true#g' /etc/bluetooth/main.conf
sudo usermod -aG bluetooth "$USER"
newgrp bluetooth
sudo cp var-run-sdp.path /etc/systemd/system/var-run-sdp.path
sudo cp var-run-sdp.service /etc/systemd/system/var-run-sdp.service
sudo systemctl daemon-reload
sudo systemctl enable bluetooth
sudo systemctl enable var-run-sdp.path
sudo systemctl enable var-run-sdp.service
sudo systemctl start var-run-sdp.path
sudo systemctl start bluetooth
sudo rfkill unblock bluetooth
sudo sdptool add SP

echo -e "\n========= Installing INDI ========="
cd  ~
wget --output-document=libindi.tar.gz "https://www.indilib.org/download/raspberry-pi/send/6-raspberry-pi/9-indi-library-for-raspberry-pi.html"
tar -xzf libindi.tar.gz
cd libindi_1.7.4_rpi
sudo dpkg -i *.deb
cd ..
rm -r libindi_1.7.4_rpi
mv libindi.tar.gz Downloads/libindi.tar.gz

read -p "Reboot now? (y/n)?" CONT
if [ "$CONT" = "y" ]; then
    sudo reboot now
fi

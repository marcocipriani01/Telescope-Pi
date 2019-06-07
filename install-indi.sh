#!/bin/bash
cd ~
sudo apt install cdbs libcfitsio-dev libnova-dev libusb-1.0-0-dev libjpeg-dev libusb-dev libtiff5-dev libftdi1-dev fxload libkrb5-dev libcurl4-gnutls-dev libraw-dev libgphoto2-dev libgsl-dev dkms libboost-regex-dev libgps-dev libdc1394-22-dev
wget --output-document=libindi.tar.gz "https://www.indilib.org/download/raspberry-pi/send/6-raspberry-pi/9-indi-library-for-raspberry-pi.html"
tar -xzf libindi.tar.gz
cd libindi_1.7.4_rpi
sudo dpkg -i *.deb
cd ..
rm -r libindi_1.7.4_rpi
rm libindi.tar.gz

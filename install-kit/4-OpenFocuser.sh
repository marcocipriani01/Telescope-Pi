#!/bin/bash
echo -e "========= Installing OpenFocuser ========="
cd ~
git clone https://github.com/marcocipriani01/OpenFocuser.git
sudo apt install -y avrdude socat
sudo dpkg -i OpenFocuser/OpenFocuser-Manager/deb-builder/OpenFocuser-Manager.deb

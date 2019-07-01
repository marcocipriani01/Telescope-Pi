#!/bin/bash
echo "========= Telescope-Pi status ========="
sudo systemctl status telescope-pi.service
echo
echo "========= OpenFocuser status ========="
sudo systemctl status openfocuser.service 2> /dev/null
echo
echo "========= Network status ========="
nmcli general status
echo
echo "Current connection:"
nmcli connection show --active

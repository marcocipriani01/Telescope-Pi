#!/bin/bash
sudo chown -R root:root Telescope-Pi/
dpkg-deb --build Telescope-Pi
sudo chown -R ${USER}:${USER} Telescope-Pi/
sudo chown -R ${USER}:${USER} Telescope-Pi.deb

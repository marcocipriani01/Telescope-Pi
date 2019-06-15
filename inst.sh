#!/bin/bash
cd "$(dirname "$0")"
git pull
sudo dpkg -i Telescope-Pi.deb

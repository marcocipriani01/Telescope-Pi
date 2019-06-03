# Telescope-Pi

Telescope-Pi is a Debian package intended to be installed in a Raspberry Pi 3B+ (Raspbian **only**) to make the board become a perfect remote computer for astronomical use: use it to manage the telescope, the CCD, the DSLR, the focuser and the mount remotely! It allows the end user to control the Raspberry Pi's newtwork, start/stop the hotspot (that is fully configurable during the installation process), control the INDI Web Manager, shutdown and reboot the system. Management is made using an Android app and Bluetooth, so one don't have to attach a keyboard and a screen to the Raspberry Pi on the field anymore. `dpkg` will guide you installing and configuring the Telescope-Pi service: you'll be asked a hostname, a wireless network interface and a password for the hotspot; then simply reboot the board and connect to it using the Android App. You'll be ready to connect to it from a remote computer using an INDI client.

## To do before installing

Of course, you'll need to install INDI **before** installing this package, otherwise something may go wrong during the installation process. To do that, please use the `./install-indi.sh` script.

## Dependencies

This package depends on `nmcli`, so it will **remove** Raspbian's default newtork manager. Moreover, it will also install `openssh-server`, `avahi-daemon` (to make the Raspberry Pi be detected over the newtork without knowing its IP address), `net-tools`, `bluez` and some Python 3 packages (required for managing Bluetooth & newtork).

## Compiling

Simply run `./build.sh` on your computer and then install `Telescope-Pi.deb` on your Raspberry Pi. Enjoy!

## The Android App

Simple and essential. User-friendly and lightweight. Developed with Android Studio.

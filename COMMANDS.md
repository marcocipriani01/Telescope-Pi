# Messages Raspberry Pi ↔ Android App

## Status messages
- NetInterface=`network interface`
- WiFi=`True/False`
- IP=`IP address`
- Hotspot=`True/False`
- HotspotSSID=`hotspot SSID`
- HotspotPswdType=`WPA/WEP/None`
- HotspotPswd=`hotspot password`
- INDI=`True/False`

## Questions
- WiFiAPs=[`AP1 SSID`(`AP1 signal quality`), ...] *Chose a Wi-Fi AP*
- TypePswd=`APx SSID` *Type password for Wi-Fi APx*

## Commands
- 01 *Turn Wi-Fi off*
- 02 *Turn Wi-Fi on*
- 03 *Turn hotspot on*
- 04 *Turn hotspot off*
- 05 *Send IP address*
- 06 *List Wi-Fi APs*
- 07 *Shutdown*
- 08 *Reboot*
- 1x *Connect to Wi-Fi AP number x*

## Busy messages
- Busy=Looking for Wi-Fi access points...
- Busy=Connecting to Wi-Fi AP APx
- Busy=Starting hotspot...
- Busy=Stopping hotspot...
- Busy=Starting INDI Web Manager...

## Info messages
- Shutting down...
- Rebooting...

## Error messages
- Erorr=Invalid command!
- Error=Unable to connect!
- Error=Unable to scan!
- Error=Unable to turn on Wi-Fi!
- Error=Unable to turn off Wi-Fi!
- Error=Unable to stop the hotspot!
- Error=Error in INDI Web Manager!

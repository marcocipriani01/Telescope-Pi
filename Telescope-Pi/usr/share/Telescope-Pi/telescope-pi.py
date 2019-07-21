import os
import signal
import socket
import sys
from os import _exit as bye
from threading import Thread
from time import sleep
from time import time as uptime

import RPi.GPIO as GPIO
from bluetooth import BluetoothSocket, RFCOMM, PORT_ANY, advertise_service, SERIAL_PORT_CLASS, SERIAL_PORT_PROFILE, \
    BluetoothError
from sh import sudo, nmcli, shutdown, reboot, bash, ErrorReturnCode, SignalException
from wifi import Cell
from wifi.exceptions import InterfaceError
import configparser

type_pswd = False
ap = None
net_scan = None
indiweb = None
client_sock = None
net_interface = None
username = None
hotspot_enabled = False
hotspot_ssid = None
hotspot_pswd = None
server_sock = None
wifi_on = True
led_thread_run = False
emergency_led_run = True
config_file = None


def main():
    """
    Main method of the application.
    Must be run with superuser permissions.
    Params: <user> <network_interface> <hotspot_ssid> <hotspot_password>
    Creates the Bluetooth server and starts listening to clients.
    """
    GPIO.setmode(GPIO.BCM)
    GPIO.setwarnings(False)
    GPIO.setup(29, GPIO.OUT)
    GPIO.output(29, GPIO.HIGH)
    GPIO.setup(15, GPIO.IN, pull_up_down=GPIO.PUD_UP)
    Thread(target=button_thread).start()

    signal.signal(signal.SIGINT, signal_handler)

    global net_interface
    global wifi_on
    global hotspot_enabled
    global hotspot_ssid
    global hotspot_pswd
    global server_sock
    global client_sock
    global username
    global type_pswd
    global indiweb
    global led_thread_run
    global config_file

    if len(sys.argv) == 5:
        username = sys.argv[1]
        print("Username = " + username)
        net_interface = sys.argv[2]
        print("Network interface = " + net_interface)
        hotspot_ssid = sys.argv[3]
        print("Hotspot SSID = " + hotspot_ssid)
        hotspot_pswd = sys.argv[4]
        print("Hotspot Password = " + hotspot_pswd)
        uuid = "b9029ed0-6d6a-4ff6-b318-215067a6d8b1"
        print("BT service UUID = " + uuid)

        config_file = "{0}.{1}".format("/home" + username + "/", "Telescope-Pi.ini")
        if username == "root":
            if os.path.isdir("/home/pi/"):
                config_file = "/home/pi/.Telescope-Pi.ini"
            else:
                config_file = None
                print("Error! Invalid user!")
                print("Running in emergency mode!")
                emergency_mode_led()
        config = configparser.ConfigParser()
        if os.path.exists(config_file) and os.path.isfile(config_file):
            config.read(config_file)
            if "Wi-Fi" in config and config["Wi-Fi"] == "yes":
                turn_on_wifi()
                try:
                    if len(Cell.all(net_interface)) == 0:
                        print("No Wi-Fi networks found, starting hotspot.")
                        start_hotspot()
                except InterfaceError as e:
                    print("An error occurred while scanning Wi-Fi network!")
                    print(str(e))
            if hotspot_enabled is False and "Hotspot" in config and config["Hotspot"] == "yes":
                start_hotspot()
            if "INDI" in config and config["INDI"] == "yes":
                indiweb_start()
        else:
            turn_on_wifi()
            try:
                if len(list(Cell.all(net_interface))) == 0:
                    print("No Wi-Fi networks found, starting hotspot.")
                    start_hotspot()
            except InterfaceError as e:
                print("An error occurred while scanning Wi-Fi network!")
                print(str(e))
            indiweb_start()

        try:
            server_sock = BluetoothSocket(RFCOMM)
            server_sock.bind(("", PORT_ANY))
            server_sock.listen(1)
            port = server_sock.getsockname()[1]
            advertise_service(server_sock, "Telescope-Pi", service_id=uuid, service_classes=[
                uuid, SERIAL_PORT_CLASS], profiles=[SERIAL_PORT_PROFILE])

            Thread(target=led_thread).start()
            while True:
                print("Waiting for connection on RFCOMM channel %d" % port)
                led_thread_run = True
                client_sock, client_info = server_sock.accept()
                led_thread_run = False
                print("Accepted connection from " + str(client_info))
                bt_send("NetInterface=" + net_interface +
                        "\nWiFi=" + str(wifi_on) +
                        "\nHotspot=" + str(hotspot_enabled) +
                        "\nHotspotSSID=" + hotspot_ssid +
                        "\nHotspotPswdType=WPA\nHotspotPswd=" + hotspot_pswd +
                        "\nINDI=" + str(indiweb is not None))
                send_ip()
                try:
                    while True:
                        data = client_sock.recv(1024)
                        if len(data) == 0:
                            break
                        for line in data.splitlines():
                            parse_rfcomm(line.strip())
                except Exception as e:
                    print(str(e))
                print("Disconnected")
                client_sock.close()
                client_sock = None
                type_pswd = False
        except BluetoothError as e:
            print("No Bluetooth adapter found! Make sure the systemd service has \"Type=idle\".")
            print("Error message: " + str(e))
            print("Running in emergency mode!")
            emergency_mode_led()
    else:
        print("Usage: \"sudo python hotspot-controller-bluetooth.py" +
              "<user> <network_interface> <hotspot_ssid> <hotspot_password>\"")
        print("Running in emergency mode!")
        emergency_mode_led()


def parse_rfcomm(line):
    """
    Parses the command received from the client and executes it.
    """
    global type_pswd
    global ap
    global net_interface
    global net_scan
    global wifi_on
    global hotspot_enabled
    global led_thread_run
    global emergency_led_run
    if type_pswd is True:
        type_pswd = False
        if line != "#":
            print("Connecting to Wi-Fi AP " + ap)
            bt_send("Busy=Connecting to Wi-Fi AP " + ap)
            try:
                print(str(nmcli("device", "wifi", "connect", ap, "password", line)))
                print("Done.")
                send_ip()
            except ErrorReturnCode as e:
                log_err("Unable to connect!")
                print(str(e))
    else:
        if len(line) == 2:
            if line == "01":
                if hotspot_enabled is True:
                    stop_hotspot()
                try:
                    print("Turning off Wi-Fi...")
                    bt_send("Busy=Turning off Wi-Fi...")
                    print(str(nmcli("radio", "wifi", "off")))
                    wifi_on = False
                    bt_send("WiFi=False")
                    print("Done.")
                except ErrorReturnCode as e:
                    log_err("Unable to turn off Wi-Fi!")
                    print(str(e))
            elif line == "02":
                turn_on_wifi()
            elif line == "03":
                start_hotspot()
            elif line == "04":
                stop_hotspot()
            elif line == "05":
                send_ip()
            elif line == "06":
                bt_send("Busy=Looking for Wi-Fi access points...")
                print("Looking for Wi-Fi access points...")
                try:
                    net_scan = Cell.all(net_interface)
                    if len(list(net_scan)) == 0:
                        bt_send("WiFiAPs=[]")
                    else:
                        net_scan.sort(key=get_ap_quality, reverse=True)
                        msg = "WiFiAPs=[" + net_scan[0].ssid + \
                              "(" + net_scan[0].quality + ")"
                        for i in range(1, min(len(list(net_scan)), 10)):
                            msg = msg + ", " + \
                                  net_scan[i].ssid + \
                                  "(" + net_scan[i].quality + ")"
                        bt_send(msg + "]")
                        print("Done.")
                except InterfaceError as e:
                    log_err("Unable to scan!")
                    print(str(e))
            elif line == "07":
                shutdown_pi()
            elif line == "08":
                log("Rebooting...")
                led_thread_run = False
                emergency_led_run = False
                sleep(0.5)
                reboot("now")
            elif line[0] == '1':
                i = int(line[1])
                if 0 <= i < min(len(list(net_scan)), 10):
                    ap = str(net_scan[i].ssid)
                    print("Connecting to Wi-Fi AP " + ap)
                    bt_send("Busy=Connecting to Wi-Fi AP " + ap)
                    try:
                        print(str(nmcli("connection", "up", "id", ap)))
                        print("Done.")
                        send_ip()
                    except ErrorReturnCode as e:
                        bt_send("TypePswd=" + ap)
                        type_pswd = True
                else:
                    log_err("Invalid command!")
            elif line[0] == '2':
                if line[1] == '0':
                    stop_indi()
                elif line[1] == '1':
                    indiweb_start()
                else:
                    print("Received: \"" + line + "\"")
                    log_err("Invalid command!")
            else:
                print("Received: \"" + line + "\"")
                log_err("Invalid command!")
        else:
            print("Received: \"" + line + "\"")
            log_err("Invalid command!")


def button_thread():
    global led_thread_run
    while True:
        while GPIO.input(15) == 1:
            sleep(0.2)
        s_time = uptime()
        old_state = led_thread_run
        led_thread_run = False
        GPIO.output(29, GPIO.LOW)
        while GPIO.input(15) == 0:
            sleep(0.1)
        c_time = uptime() - s_time
        if .1 <= c_time < 2:
            GPIO.output(29, GPIO.LOW)
            sleep(0.1)
            GPIO.output(29, GPIO.HIGH)
            sleep(0.1)
            turn_on_wifi()
        elif 2 <= c_time < 5:
            for i in range(0, 2):
                GPIO.output(29, GPIO.LOW)
                sleep(0.1)
                GPIO.output(29, GPIO.HIGH)
                sleep(0.1)
            indiweb_start()
        elif c_time >= 5:
            for i in range(0, 3):
                GPIO.output(29, GPIO.LOW)
                sleep(0.1)
                GPIO.output(29, GPIO.HIGH)
                sleep(0.1)
            shutdown_pi()
        led_thread_run = old_state


def led_thread():
    global led_thread_run
    while True:
        if led_thread_run is True:
            GPIO.output(29, GPIO.HIGH)
            sleep(0.8)
            if led_thread_run is True:
                GPIO.output(29, GPIO.LOW)
                sleep(0.2)
        else:
            GPIO.output(29, GPIO.HIGH)
        sleep(0.2)


def emergency_mode_led():
    global indiweb
    global emergency_led_run
    while emergency_led_run is True:
        GPIO.output(29, GPIO.LOW)
        sleep(0.1)
        GPIO.output(29, GPIO.HIGH)
        sleep(0.1)
        if indiweb is not None:
            GPIO.output(29, GPIO.LOW)
            sleep(0.2)
            GPIO.output(29, GPIO.HIGH)
            sleep(0.8)


def turn_on_wifi():
    global wifi_on
    try:
        print("Turning on Wi-Fi...")
        bt_send("Busy=Turning on Wi-Fi...")
        print(str(nmcli("radio", "wifi", "on")))
        wifi_on = True
        bt_send("WiFi=True")
        print("Done.")
    except ErrorReturnCode as e:
        log_err("Unable to turn on Wi-Fi!")
        print(str(e))


def get_ap_quality(val):
    return val.quality


def send_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        bt_send("IP=" + s.getsockname()[0])
        s.close()
    except socket.error as e:
        bt_send("IP=null")
        print(str(e))


def start_hotspot():
    """
    Starts the hotspot using nmcli.
    """
    global hotspot_ssid
    global hotspot_pswd
    global hotspot_enabled
    global wifi_on
    try:
        print("Turning on Wi-Fi...")
        bt_send("Busy=Turning on Wi-Fi...")
        print(str(nmcli("radio", "wifi", "on")))
        wifi_on = True
        bt_send("WiFi=True")
        print("Done.")
        print("Starting hotspot...")
        bt_send("Busy=Starting hotspot...")
        try:
            print(str(nmcli("device", "wifi", "hotspot", "con-name", hotspot_ssid,
                            "ssid", hotspot_ssid, "band", "bg", "password",
                            hotspot_pswd)))
            hotspot_enabled = True
            bt_send("Hotspot=True")
            print("Done.")
        except ErrorReturnCode as e:
            log_err("Unable to start the hotspot!")
            print(str(e))
    except ErrorReturnCode as e:
        log_err("Unable to turn on Wi-Fi!")
        print(str(e))


def stop_hotspot():
    global hotspot_enabled
    bt_send("Busy=Stopping hotspot...")
    print("Stopping hotspot...")
    try:
        print(str(nmcli("connection", "down", hotspot_ssid)))
        hotspot_enabled = False
        bt_send("Hotspot=False")
        print("Done.")
    except ErrorReturnCode as e:
        log_err("Unable to stop the hotspot!")
        print(str(e))


def log(message):
    """
    Prints a message to stdout and to the client.
    """
    print(message)
    bt_send("Info=" + message)


def log_err(message):
    """
    Prints an error message to stdout and to the client.
    """
    print(message)
    bt_send("Error=" + message)


def bt_send(message):
    """
    Sends a message to to the client.
    """
    global client_sock
    if client_sock is not None:
        client_sock.send(message + "\n")


def indiweb_start():
    """
    Starts/restarts the INDI Web Manager.
    pip3 package indiweb must be installed.
    """
    global username
    global indiweb
    stop_indi()
    bt_send("Busy=Starting INDI Web Manager...")
    print("Starting INDI Web Manager...")
    try:
        indiweb = sudo("-u", username, "indi-web", "-v", _bg=True, _out=log_indi, _done=clean_indi)
        bt_send("INDI=True")
        print("Done.")
    except ErrorReturnCode as e:
        log_err("Error in INDI Web Manager!")
        print(str(e))
        indiweb = None


def log_indi(line):
    """
    Logs the indiweb output
    """
    print("INDI Web Manager says: " + line)


def stop_indi():
    """
    Kills indiweb if running.
    """
    global indiweb
    print("Stopping INDI Web Manager...")
    if indiweb is not None:
        try:
            indiweb.terminate()
        except (SignalException, OSError) as e:
            print(str(e))
        try:
            indiweb.kill_group()
        except (SignalException, OSError) as e:
            print(str(e))
        indiweb = None
    print("Killing other INDI processes...")
    try:
        bash("-c", "pgrep indi | xargs -L1 -r kill")
    except ErrorReturnCode as e:
        print(str(e))
    print("Done.")


def clean_indi(cmd=None, success=None, exit_code=None):
    """
    Makes the indiweb var equal to None
    """
    global indiweb
    indiweb = None


def save_prefs():
    global config_file
    global wifi_on
    global hotspot_enabled
    global indiweb

    if config_file is not None:
        config = configparser.ConfigParser()
        config["Wi-Fi"] = ("yes" if wifi_on is True else "no")
        config["Hotspot"] = ("yes" if hotspot_enabled is True else "no")
        config["INDI"] = ("yes" if indiweb is not None else "no")
        with open(config_file, 'w') as file:
            config.write(file)


def shutdown_pi():
    global led_thread_run
    global emergency_led_run
    log("Shutting down...")
    led_thread_run = False
    emergency_led_run = False
    sleep(0.5)
    shutdown("now")


def signal_handler(sig, frame):
    """
    Handles the signals sent to this process.
    """
    global led_thread_run
    global emergency_led_run
    global server_sock
    global client_sock
    GPIO.output(29, GPIO.HIGH)
    print("Closing connections...")
    led_thread_run = False
    emergency_led_run = False
    if client_sock is not None:
        try:
            client_sock.close()
            client_sock = None
        except IOError as e:
            print("I/O error occurred while closing client socket!")
            print(str(e))
    if server_sock is not None:
        try:
            server_sock.close()
        except IOError as e:
            print("I/O error occurred while closing server socket!")
            print(str(e))
    stop_indi()
    print("Exiting...")
    bye(0)


if __name__ == "__main__":
    main()

from wifi import Cell
from wifi.exceptions import InterfaceError
import sys
import signal
import RPi.GPIO as GPIO
from time import sleep
from time import time as uptime
from threading import Thread
from psutil import process_iter as running_procs
from bluetooth import BluetoothSocket, RFCOMM, PORT_ANY, advertise_service,\
    SERIAL_PORT_CLASS, SERIAL_PORT_PROFILE
from sh import sudo, nmcli, shutdown, reboot, ErrorReturnCode, SignalException
import socket

type_pswd = False
ap = None
net_scan = None
indiweb = None
client_sock = None
net_interface = None
username = None
hotspot_enabled = False
hotspotssid = None
hotspotpswd = None
server_sock = None
client_sock = None
wifi_on = True
led_thread_run = False
emergency_led_run = True


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
    GPIO.add_event_detect(15, GPIO.RISING, callback=button_callback, bouncetime=500)

    signal.signal(signal.SIGINT, signal_handler)

    global net_interface
    global wifi_on
    global hotspot_enabled
    global hotspotssid
    global hotspotpswd
    global server_sock
    global client_sock
    global username
    global type_pswd
    global indiweb

    if len(sys.argv) == 5:
        username = sys.argv[1]
        print("Username = " + username)
        net_interface = sys.argv[2]
        print("Network interface = " + net_interface)
        hotspotssid = sys.argv[3]
        print("Hotspot SSID = " + hotspotssid)
        hotspotpswd = sys.argv[4]
        print("Hotspot Password = " + hotspotpswd)
        uuid = "b9029ed0-6d6a-4ff6-b318-215067a6d8b1"
        print("BT service UUID = " + uuid)

        turn_on_wifi()
        try:
            if len(Cell.all(net_interface)) == 0:
                print("No Wi-Fi newtworks found, starting hotspot.")
                start_hotspot()
        except InterfaceError as e:
            print("An error occurred while scanning Wi-Fi newtwork!")
            print(e.message)

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
                        "\nHotspotSSID=" + hotspotssid +
                        "\nHotspotPswdType=WPA\nHotspotPswd=" + hotspotpswd +
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
                    print(e.message)
                print("Disconnected")
                client_sock.close()
                type_pswd = False
        except BluetoothError as e:
            print("No Bluetooth adapter found! Make sure the systemd service has \"Type=idle\".")
            print("Error message: " + e.message)
            print("Running in emergency mode!")
            emergency_mode_led()
    else:
        print("Usage: \"sudo python hotspot-controller-bluetooth.py <user> <network_interface> <hotspot_ssid> <hotspot_password>\"")
        print("Running in emergency mode!")
        emergency_mode_led()


def led_thread():
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


def button_callback(channel):
    stime = uptime()
    old_state = led_thread_run
    led_thread_run = False
    GPIO.output(29, GPIO.LOW)
    while GPIO.input(15) == 1:
        pass
    GPIO.output(29, GPIO.HIGH)
    btime = uptime() - stime
    if .1 <= btime < 2:
        GPIO.output(29, GPIO.LOW)
        sleep(0.1)
        GPIO.output(29, GPIO.HIGH)
        sleep(0.1)
        turn_on_wifi()
    elif 2 <= btime < 5:
        for i in range(0, 2):
            GPIO.output(29, GPIO.LOW)
            sleep(0.1)
            GPIO.output(29, GPIO.HIGH)
            sleep(0.1)
        indiweb_start()
    elif btime >= 5:
        for i in range(0, 3):
            GPIO.output(29, GPIO.LOW)
            sleep(0.1)
            GPIO.output(29, GPIO.HIGH)
            sleep(0.1)
        shutdown_pi()
    led_thread_run = old_state


def send_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("8.8.8.8", 80))
    bt_send("IP=" + s.getsockname()[0])
    s.close()


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
    if type_pswd is True:
        type_pswd = False
        if line != "#":
            bt_send("Busy=Connecting to Wi-Fi AP " + ap)
            try:
                print(str(nmcli("device", "wifi", "connect", ap, "password", line)))
                send_ip()
            except ErrorReturnCode as e:
                log_err("Unable to connect!")
                print(e.message)
    else:
        if len(line) == 2:
            if line == "01":
                if hotspot_enabled is True:
                    turn_off_hotspot()
                try:
                    print(str(nmcli("radio", "wifi", "off")))
                    bt_send("WiFi=False")
                    wifi_on = False
                except ErrorReturnCode as e:
                    log_err("Unable to turn off Wi-Fi!")
                    print(e.message)
            elif line == "02":
                turn_on_wifi()
            elif line == "03":
                start_hotspot()
            elif line == "04":
                turn_off_hotspot()
            elif line == "05":
                send_ip()
            elif line == "06":
                bt_send("Busy=Looking for Wi-Fi access points...")
                try:
                    net_scan = Cell.all(net_interface)
                    if len(net_scan) == 0:
                        bt_send("WiFiAPs=[]")
                    else:
                        net_scan.sort(key=get_ap_quality, reverse=True)
                        msg = "WiFiAPs=[" + net_scan[0].ssid + \
                            "(" + net_scan[0].quality + ")"
                        for i in range(1, min(len(net_scan), 10)):
                            msg = msg + ", " + \
                                net_scan[i].ssid + \
                                "(" + net_scan[i].quality + ")"
                        bt_send(msg + "]")
                except InterfaceError as e:
                    log_err("Unable to scan!")
                    print(e.message)
            elif line == "07":
                shutdown_pi()
            elif line == "08":
                log("Rebooting...")
                reboot("now")
            elif line[0] == '1':
                i = int(line[1])
                if 0 <= i < min(len(net_scan), 10):
                    ap = str(net_scan[i].ssid)
                    bt_send("Busy=Connecting to Wi-Fi AP " + ap)
                    try:
                        print(str(nmcli("connection", "up", "id", ap)))
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


def shutdown_pi():
    log("Shutting down...")
    led_thread_run = False
    emergency_led_run = False
    shutdown("now")


def turn_off_hotspot():
    global hotspot_enabled
    bt_send("Busy=Stopping hotspot...")
    try:
        print(str(nmcli("connection", "down", hotspotssid)))
        hotspot_enabled = False
        bt_send("Hotspot=False")
    except ErrorReturnCode as e:
        log_err("Unable to stop the hotspot!")
        print(e.message)


def turn_on_wifi():
    global wifi_on
    try:
        print(str(nmcli("radio", "wifi", "on")))
        bt_send("WiFi=True")
        wifi_on = True
    except ErrorReturnCode as e:
        log_err("Unable to turn on Wi-Fi!")
        print(e.message)


def get_ap_quality(val):
    return val.quality


def signal_handler(sig, frame):
    """
    Handles the signals sent to this process.
    """
    print("Stopping Telescope-Pi...")
    led_thread_run = False
    emergency_led_run = False
    global server_sock
    global client_sock
    if server_sock is not None:
        try:
            server_sock.close()
        except IOError as e:
            print("I/O error occurred while closing server socket!")
            print(e.message)
    if client_sock is not None:
        try:
            client_sock.close()
        except IOError as e:
            print("I/O error occurred while closing client socket!")
            print(e.message)
    stop_indi()
    sys.exit(0)


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


def start_hotspot():
    """
    Starts the hotspot using nmcli.
    """
    global hotspotssid
    global hotspotpswd
    global hotspot_enabled
    global wifi_on
    try:
        print(str(nmcli("radio", "wifi", "on")))
        bt_send("WiFi=True")
        wifi_on = True
        bt_send("Busy=Starting hotspot...")
        try:
            print(str(nmcli("device", "wifi", "hotspot", "con-name", hotspotssid,
                            "ssid", hotspotssid, "band", "bg", "password",
                            hotspotpswd)))
            hotspot_enabled = True
            bt_send("Hotspot=True")
        except ErrorReturnCode as e:
            log_err("Unable to start the hotspot!")
            print(e.message)
    except ErrorReturnCode as e:
        log_err("Unable to turn on Wi-Fi!")
        print(e.message)


def indiweb_start():
    """
    Starts/restarts the INDI Web Manager.
    pip3 package indiweb must be installed.
    """
    global username
    global indiweb
    stop_indi()
    bt_send("Busy=Starting INDI Web Manager...")
    try:
        indiweb = sudo("-u", username, "indi-web", "-v",
                       _bg=True, _out=log_indi, _done=clean_indi)
    except ErrorReturnCode as e:
        log_err("Error in INDI Web Manager!")
        print(e.message)
        indiweb = None
    bt_send("INDI=True")


def log_indi(line):
    """
    Logs the indiweb output
    """
    print("indiweb: " + line)


def stop_indi():
    """
    Kills indiweb if running.
    """
    global indiweb
    print("Killing old INDI processes...")
    if indiweb is not None:
        try:
            indiweb.terminate()
        except (SignalException, OSError) as e:
            print(e.message)
        try:
            indiweb.kill_group()
        except (SignalException, OSError) as e:
            print(e.message)
        indiweb = None
    try:
        for proc in running_procs():
            pname = proc.name()
            if pname == "indiserver" or pname.startswith("indi_"):
                proc.kill()
    except OSError as e:
        print(e.message)


def clean_indi(cmd=None, success=None, exit_code=None):
    """
    Makes the indiweb var equal to None
    """
    global indiweb
    indiweb = None


if __name__ == "__main__":
    main()

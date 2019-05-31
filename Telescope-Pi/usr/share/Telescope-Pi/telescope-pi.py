from wifi import Cell
from wifi.exceptions import InterfaceError
import sys
import signal
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


def main():
    """
    Main method of the application.
    Must be run with superuser permissions.
    Params: <user> <network_interface> <hotspot_ssid> <hotspot_password>
    Creates the Bluetooth server and starts listening to clients.
    """
    global net_interface
    global hotspotssid
    global hotspotpswd
    global server_sock
    global client_sock
    global username
    global type_pswd
    if len(sys.argv) == 5:
        signal.signal(signal.SIGINT, signal_handler)
        username = sys.argv[1]
        print("Username = " + username)
        net_interface = sys.argv[2]
        print("Network interface = " + net_interface)
        hotspotssid = sys.argv[3]
        print("Hotspot SSID = " + hotspotssid)
        hotspotpswd = sys.argv[4]
        print("Hotspot Password = " + hotspotpswd)

        server_sock = BluetoothSocket(RFCOMM)
        server_sock.bind(("", PORT_ANY))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]
        uuid = "b9029ed0-6d6a-4ff6-b318-215067a6d8b1"
        print("BT service UUID = " + uuid)
        advertise_service(server_sock, "Telescope-Pi", service_id=uuid,
                          service_classes=[uuid, SERIAL_PORT_CLASS], profiles=[SERIAL_PORT_PROFILE])

        indiweb_start()

        turn_on_wifi()
        try:
            if len(Cell.all(net_interface)) == 0:
                start_hotspot()
                print("No Wi-Fi newtworks found, hotspot started.")
        except InterfaceError:
            print("An error occurred while scanning Wi-Fi newtwork!")

        while True:
            print("Waiting for connection on RFCOMM channel %d" % port)
            client_sock, client_info = server_sock.accept()
            print("Accepted connection from " + str(client_info))
            bt_send("NetInterface=" + net_interface +
                    "\nWiFi=" + str(wifi_on) +
                    "\nHotspot=" + str(hotspot_enabled) +
                    "\nINDI=" + str(indiweb not None))
            send_ip()
            try:
                while True:
                    data = client_sock.recv(1024)
                    if len(data) == 0:
                        break
                    parse_rfcomm(data.replace("\n", "").strip())
            except IOError:
                pass
            print("Disconnected")
            client_sock.close()
            type_pswd = False
    else:
        print("Usage: \"sudo python hotspot-controller-bluetooth.py <user> <network_interface> <hotspot_ssid> <hotspot_password>\"")
        exit(1)


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
    if type_pswd is True:
        bt_send("Busy=Connecting to Wi-Fi")
        try:
            print(str(nmcli("device", "wifi", "connect", ap, "password", line)))
            send_ip()
        except ErrorReturnCode:
            log_err("Unable to connect!")
        type_pswd = False
    else:
        if len(line) == 2:
            if line == "01":
                start_hotspot()
            elif line == "02":
                bt_send("Busy=Stopping hotspot...")
                try:
                    print(str(nmcli("connection", "down", hotspotssid)))
                    hotspot_enabled = False
                    bt_send("Hotspot=False")
                except ErrorReturnCode:
                    log_err("Unable to stop the hotspot!")
            elif line == "03":
                send_ip()
            elif line == "04":
                bt_send("Busy=Looking for Wi-Fi access points...")
                try:
                    net_scan = Cell.all(net_interface)
                    net_scan.sort(key=get_ap_quality)
                    list = "WiFiAPs=["
                    index = 0
                    for el in net_scan:
                        list = list + ", " + el.ssid + "(" + el.quality + ")"
                        index = index + 1
                        if index == 9:
                            break
                    bt_send(list + "]")
                except InterfaceError:
                    log_err("Unable to scan!")
            elif line == '05':
                log("Shutting down...")
                shutdown("now")
            elif line == '06':
                log("Rebooting...")
                reboot("now")
            elif line[0] == '1':
                if line[1] == '0':
                    try:
                        print(str(nmcli("radio", "wifi", "off")))
                        bt_send("WiFi=False")
                        wifi_on = False
                    except ErrorReturnCode:
                        log_err("Unable to turn off Wi-Fi!")
                elif line[1] == '1':
                    turn_on_wifi()
                else:
                    log_err("Invalid command!")
            elif line[0] == '2':
                if 0 <= index < len(net_scan):
                    ap = str(net_scan[index].ssid)
                    bt_send("Busy=Connecting to Wi-Fi AP " + ap)
                    try:
                        print(str(nmcli("connection", "up", "id", ap)))
                        send_ip()
                    except ErrorReturnCode:
                        bt_send("TypePswd=" + ap)
                        type_pswd = True
                elif line[0] == '3':
                    if line[1] == '0':
                        kill_indi()
                    elif line[1] == '1':
                        indiweb_start()
                    else:
                        log_err("Invalid command!")
                else:
                    log_err("Invalid command!")
            else:
                log_err("Invalid command!")
        else:
            log_err("Invalid command!")


def turn_on_wifi():
    try:
        print(str(nmcli("radio", "wifi", "on")))
        bt_send("WiFi=True")
        wifi_on = True
    except ErrorReturnCode:
        log_err("Unable to turn on Wi-Fi!")


def get_ap_quality(val):
    return val.quality


def signal_handler(sig, frame):
    """
    Handles the signals sent to this process.
    """
    print("Stopping Telescope-Pi...")
    global server_sock
    global client_sock
    if server_sock is not None:
        try:
            server_sock.close()
        except IOError:
            print("I/O error occurred while closing server socket!")
    if client_sock is not None:
        try:
            client_sock.close()
        except IOError:
            print("I/O error occurred while closing client socket!")
    kill_indi()
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
    turn_on_wifi()
    bt_send("Busy=Starting hotspot...")
    try:
        print(str(nmcli("device", "wifi", "hotspot", "con-name", hotspotssid,
                        "ssid", hotspotssid, "band", "bg", "password",
                        hotspotpswd)).replace("\n", ""))
        hotspot_enabled = True
        bt_send("Hotspot=True\nHotspotSSID=" + hotspotssid +
                "\nHotspotPswdType=WPA\nHotspotPswd=" + hotspotpswd)
    except ErrorReturnCode:
        log_err("Unable to start the hotspot!")


def indiweb_start():
    """
    Starts/restarts the INDI Web Manager.
    pip3 package indiweb must be installed.
    """
    global username
    global indiweb
    kill_indi()
    bt_send("Busy=Starting INDI Web Manager...")
    try:
        indiweb = sudo("-u", username, "indi-web", "-v",
                       _bg=True, _out=log_indi, _done=clean_indi)
    except ErrorReturnCode:
        log_err("Error in INDI Web Manager!")
    bt_send("INDI=True")


def log_indi(line):
    """
    Logs the indiweb output
    """
    print("indiweb: " + line.replace("\n", ""))


def kill_indi():
    """
    Kills indiweb if running.
    """
    global indiweb
    if indiweb is not None:
        print("Killing old INDI processes...")
        try:
            indiweb.terminate()
        except SignalException:
            pass
        try:
            indiweb.kill_group()
        except SignalException:
            pass
        for proc in running_procs():
            if proc.name() == "indiserver" or proc.startswith("indi_"):
                proc.kill()


def clean_indi():
    """
    Makes the indiweb var equal to None
    """
    global indiweb
    indiweb = None


if __name__ == "__main__":
    main()

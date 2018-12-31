from wifi import Cell
from wifi.exceptions import InterfaceError
import sys
import signal
from psutil import process_iter as running_procs
from bluetooth import BluetoothSocket, RFCOMM, PORT_ANY, advertise_service,\
    SERIAL_PORT_CLASS, SERIAL_PORT_PROFILE
from sh import sudo, nmcli, shutdown, reboot, ErrorReturnCode, SignalException
import socket


typepswd = False
selectnet = False
ap = None
netscan = None
indiweb = None
shutdown_confirm = False
reboot_confirm = False
client_sock = None


def main():
    """
    Main method of the application.
    Must be run with superuser permissions.
    Params: <user> <network_interface> <hotspot_ssid> <hotspot_password>
    Creates the Bluetooth server and starts listening to clients.
    """
    global netinterface
    global hotspotssid
    global hotspotpswd
    global server_sock
    global client_sock
    global username
    global typepswd
    global selectnet
    if len(sys.argv) == 5:
        signal.signal(signal.SIGINT, signal_handler)
        username = sys.argv[1]
        print("Username = " + username)
        netinterface = sys.argv[2]
        print("NetworkInterface = \"" + netinterface + "\"")
        hotspotssid = sys.argv[3]
        print("Hotspot SSID = " + hotspotssid)
        hotspotpswd = sys.argv[4]
        print("Hotspot password = " + hotspotpswd)

        server_sock = BluetoothSocket(RFCOMM)
        server_sock.bind(("", PORT_ANY))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]
        uuid = "b9029ed0-6d6a-4ff6-b318-215067a6d8b1"
        advertise_service(server_sock, "Telescope-Pi",
                          service_id=uuid,
                          service_classes=[uuid, SERIAL_PORT_CLASS],
                          profiles=[SERIAL_PORT_PROFILE])
        indiweb_start()
        try:
            if len(Cell.all(netinterface)) == 0:
                start_hotspot()
        except InterfaceError:
            print("Unable to scan!")
        while True:
            print("Waiting for connection on RFCOMM channel %d" % port)
            client_sock, client_info = server_sock.accept()
            print("Accepted connection from " + str(client_info))
            log("Welcome!\nUsing network interface \"" + netinterface + "\"")
            log_ip()
            print_cmds()
            try:
                while True:
                    data = client_sock.recv(1024)
                    if len(data) == 0:
                        break
                    parse_rfcomm(data.replace("\n", "").strip())
            except IOError:
                pass
            print("Disconnecting...")
            client_sock.close()
            typepswd = False
            selectnet = False
            clear_confirmations()
    else:
        print("Usage: sudo python hotspot-controller-bluetooth.py"
              + "<user> <network_interface> <hotspot_ssid> <hotspot_password>")
        exit(1)


def print_cmds():
    """
    Prints all the available commands to stdout and the client.
    """
    log("Select an option:\n"
        + "  1. Start hotspot\n"
        + "  2. Stop hotspot\n"
        + "  3. Get IP\n"
        + "  4. Connect to Wi-Fi AP\n"
        + "  5. Restart INDI Web Manager\n"
        + "  6. Shutdown\n"
        + "  7. Reboot")


def log_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("8.8.8.8", 80))
    log("Local IP address: " + s.getsockname()[0])
    s.close()


def parse_rfcomm(line):
    """
    Parses the command received from the client and executes it.
    """
    global typepswd
    global ap
    global selectnet
    global netscan
    if typepswd is True:
        log("Connecting...")
        try:
            log(str(nmcli("device", "wifi", "connect", ap,
                          "password", line)).replace("\n", ""))
        except ErrorReturnCode:
            log("Error!")
            print_cmds()
        typepswd = False
        print_cmds()
    else:
        if len(line) == 1:
            cmdchar = line[0]
            if selectnet:
                index = ord(cmdchar) - 97
                if 0 <= index < len(netscan):
                    ap = str(netscan[index].ssid)
                    connect(ap)
                else:
                    log("Invalid selected network!")
                    print_cmds()
                selectnet = False
            elif cmdchar == '1':
                clear_confirmations()
                log("Starting hotspot...")
                start_hotspot()
                print_cmds()
            elif cmdchar == '2':
                clear_confirmations()
                log("Turning off hotspot...")
                stop_hotspot()
                print_cmds()
            elif cmdchar == '3':
                clear_confirmations()
                log_ip()
                print_cmds()
            elif cmdchar == '4':
                clear_confirmations()
                log("Network scan...")
                netdiscovery()
            elif cmdchar == '5':
                clear_confirmations()
                indiweb_start()
            elif cmdchar == '6':
                shutdown_ask()
            elif cmdchar == '7':
                reboot_ask()
            else:
                clear_confirmations()
                log("Invalid command!")
                print_cmds()
        else:
            clear_confirmations()
            log("Invalid command!")
            print_cmds()


def clear_confirmations():
    """
    Clears confirmation vars for shutdown and reboot.
    To invoke if the user invoke a command that isn't shutdown or reboot.
    """
    global shutdown_confirm
    global reboot_confirm
    shutdown_confirm = False
    reboot_confirm = False


def shutdown_ask():
    """
    Invoke twice to turn off the computer.
    """
    global shutdown_confirm
    global reboot_confirm
    reboot_confirm = False
    if shutdown_confirm:
        log("Shutdown!")
        shutdown("now")
    else:
        log("Confirm again to shutdown!")
        shutdown_confirm = True


def reboot_ask():
    """
    Invoke twice to reboot the computer.
    """
    global shutdown_confirm
    global reboot_confirm
    shutdown_confirm = False
    if reboot_confirm:
        log("Reboot!")
        reboot("now")
    else:
        log("Confirm again to reboot!")
        reboot_confirm = True


def signal_handler(sig, frame):
    """
    Handles the signals sent to this process.
    """
    print('Exiting...')
    if 'server_sock' in globals():
        global server_sock
        if server_sock is not None:
            try:
                server_sock.close()
            except IOError:
                print("IOError while closing server socket!")
    if 'client_sock' in globals():
        global client_sock
        if client_sock is not None:
            try:
                client_sock.close()
            except IOError:
                print("IOError while closing client socket!")
        client_sock.close()
    kill_indi()
    sys.exit(0)


def log(message):
    """
    Prints a message to stdout and to the client.
    """
    global client_sock
    print(message)
    if client_sock is not None:
        client_sock.send(message + "\n")


def start_hotspot():
    """
    Starts the hotspot using nmcli.
    SSID and password are retrieved from global vars.
    """
    global hotspotssid
    global hotspotpswd
    try:
        log(str(nmcli("device", "wifi", "hotspot", "con-name", hotspotssid,
                      "ssid", hotspotssid, "band", "bg", "password",
                      hotspotpswd)).replace("\n", ""))
        log("SSID: " + hotspotssid + "\nPassword: " + hotspotpswd)
    except ErrorReturnCode:
        log("Error!")


def stop_hotspot():
    """
    Stops the hotspot nmcli.
    """
    try:
        log(str(nmcli("connection", "down", hotspotssid)).replace("\n", ""))
    except ErrorReturnCode:
        log("Error!")


def netdiscovery():
    """
    Looks for Wi-Fi APs and asks the user to select one.
    """
    global selectnet
    global netinterface
    global netscan
    try:
        netscan = Cell.all(netinterface)
        log("Select an access point:")
        index = 97
        for el in netscan:
            log("  " + chr(index) + ") " + el.ssid + ": " + el.quality)
            index = index + 1
        selectnet = True
    except InterfaceError:
        log("Unable to scan!")
        print_cmds()


def connect(ap):
    """
    Connects to the Wi-Fi AP that has the given SSID.
    Uses nmcli.
    """
    global typepswd
    try:
        log(str(nmcli("connection", "up", "id", ap)).replace("\n", ""))
        print_cmds()
    except ErrorReturnCode:
        log("Password for \"" + ap + "\":")
        typepswd = True


def indiweb_start():
    """
    Starts (or restarts) the INDI Web Manager.
    Must be installed.
    """
    global username
    global indiweb
    kill_indi()
    log("Starting INDI Web Manager...")
    try:
        indiweb = sudo("-u", username, "indi-web", "-v",
                       _bg=True, _out=log_indi, _done=clean_indi)
    except ErrorReturnCode:
        log("Error in INDI Web Manager!")


def log_indi(line):
    """
    Sends the indiweb output to stdout and the client.
    """
    log("INDI: " + line.replace("\n", ""))


def kill_indi():
    """
    Kills indiweb if running.
    """
    global indiweb
    if indiweb is not None:
        log("Killing old INDI processes...")
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

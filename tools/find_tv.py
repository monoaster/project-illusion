#!/usr/bin/env python3
"""Find TVs on this computer's Wi-Fi that have network debugging on (they answer on port 5555).

Usage: python3 tools/find_tv.py            # scans this computer's /24 network
       python3 tools/find_tv.py 10.0.0.0/24
Prints each address that answers. The TV has to be on, with debugging turned on in Developer options.
"""
import ipaddress, socket, sys
from concurrent.futures import ThreadPoolExecutor

def own_network():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 9))          # no packet is sent; this only picks the outgoing interface
        ip = s.getsockname()[0]
    finally:
        s.close()
    return ipaddress.ip_network(ip + "/24", strict=False), ip

def answers(host):
    try:
        with socket.create_connection((str(host), 5555), timeout=0.8):
            return str(host)
    except OSError:
        return None

net, me = (ipaddress.ip_network(sys.argv[1], strict=False), None) if len(sys.argv) > 1 else own_network()
print(f"Scanning {net} for port 5555" + (f" (this computer is {me})" if me else "") + " ...")
with ThreadPoolExecutor(64) as pool:
    found = [h for h in pool.map(answers, net.hosts()) if h]
print("\n".join(found) if found else "No TV answered. Check that it is on, on the same Wi-Fi, and that debugging is on.")

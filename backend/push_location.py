"""
Ebike Location Backend — Firebase Realtime Database
====================================================
This script pushes GPS location data to Firebase Realtime Database.
Your Android app reads from the same database in real-time.

TWO MODES (auto-detected):
    1. SERIAL MODE — Reads real GPS from ESP32 + NEO-6M over USB serial.
       The ESP32 sketch sends JSON lines prefixed with "GPS:" over serial.
    2. SIMULATOR MODE — If no serial device is found, simulates movement.

SETUP:
    1. Install dependencies:  pip install -r requirements.txt
    2. Place your 'serviceAccountKey.json' in this folder
    3. Connect ESP32 via USB (or use simulator for testing)
    4. Run:  python push_location.py
"""
import firebase_admin
from firebase_admin import credentials, db
import time
import json
import sys
import os
import glob

# ============================================================
# 🔧 CONFIGURATION
# ============================================================

# Path to your Firebase service account key JSON file
SERVICE_ACCOUNT_KEY = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")

# Your Firebase Realtime Database URL
FIREBASE_DB_URL = "https://iot-tracker-17bec-default-rtdb.firebaseio.com"

# Serial baud rate (must match ESP32 sketch: 115200)
SERIAL_BAUD = 115200

# Update interval in seconds (only used in simulator mode)
UPDATE_INTERVAL = 1


# ============================================================
# 🔥 FIREBASE INITIALIZATION
# ============================================================

def initialize_firebase():
    """Initialize Firebase Admin SDK"""
    if not os.path.exists(SERVICE_ACCOUNT_KEY):
        print("❌ ERROR: serviceAccountKey.json not found!")
        print("   Download it from Firebase Console → Project Settings → Service Accounts")
        print(f"   Expected path: {SERVICE_ACCOUNT_KEY}")
        sys.exit(1)

    try:
        cred = credentials.Certificate(SERVICE_ACCOUNT_KEY)
        firebase_admin.initialize_app(cred, {
            'databaseURL': FIREBASE_DB_URL
        })
        print("✅ Firebase initialized successfully!")
    except Exception as e:
        print(f"❌ Firebase initialization failed: {e}")
        sys.exit(1)


# ============================================================
# 📡 SERIAL GPS READER (ESP32 + NEO-6M)
# ============================================================

def find_serial_port():
    """Auto-detect ESP32 serial port"""
    import serial.tools.list_ports

    ports = serial.tools.list_ports.comports()
    esp_ports = []

    for port in ports:
        desc = (port.description or "").lower()
        hwid = (port.hwid or "").lower()
        # Common ESP32 / CP2102 / CH340 USB-serial identifiers
        if any(keyword in desc for keyword in ["cp210", "ch340", "ch9102", "usb", "uart", "silicon labs", "esp"]):
            esp_ports.append(port)
        elif any(keyword in hwid for keyword in ["10c4:ea60", "1a86:7523", "1a86:55d4", "303a:1001"]):
            esp_ports.append(port)

    if esp_ports:
        chosen = esp_ports[0]
        print(f"✅ Found serial device: {chosen.device} ({chosen.description})")
        return chosen.device

    # macOS fallback — check /dev/cu.* ports
    mac_ports = glob.glob("/dev/cu.usbserial*") + glob.glob("/dev/cu.SLAB*") + glob.glob("/dev/cu.wch*")
    if mac_ports:
        print(f"✅ Found serial port: {mac_ports[0]}")
        return mac_ports[0]

    # Linux fallback
    linux_ports = glob.glob("/dev/ttyUSB*") + glob.glob("/dev/ttyACM*")
    if linux_ports:
        print(f"✅ Found serial port: {linux_ports[0]}")
        return linux_ports[0]

    return None


def read_serial_gps(port):
    """
    Read GPS JSON from ESP32 serial.
    The ESP32 sketch sends lines like: GPS:{"latitude":28.6139,"longitude":77.209,"speed":12.3}
    """
    import serial as pyserial

    try:
        ser = pyserial.Serial(port, SERIAL_BAUD, timeout=2)
        print(f"📡 Serial connected: {port} @ {SERIAL_BAUD} baud")
        print("   Listening for GPS data from ESP32...\n")

        ref = db.reference('location')
        update_count = 0

        while True:
            line = ser.readline().decode('utf-8', errors='ignore').strip()

            if not line:
                continue

            # Check for GPS data prefix from our ESP32 sketch
            if line.startswith("GPS:"):
                json_str = line[4:]  # Strip "GPS:" prefix
                try:
                    location = json.loads(json_str)

                    # Validate required fields
                    if "latitude" in location and "longitude" in location:
                        # Push to Firebase
                        ref.set(location)
                        update_count += 1

                        print(f"  [{update_count}] 📍 Lat: {location['latitude']:.6f}, "
                              f"Lng: {location['longitude']:.6f}, "
                              f"Speed: {location.get('speed', 0):.1f} km/h")
                    else:
                        print(f"  ⚠️ Invalid GPS data (missing fields): {json_str}")

                except json.JSONDecodeError:
                    print(f"  ⚠️ Bad JSON from serial: {json_str}")
            else:
                # Print other ESP32 debug output
                print(f"  [ESP32] {line}")

    except pyserial.SerialException as e:
        print(f"❌ Serial error: {e}")
        return False
    except KeyboardInterrupt:
        print(f"\n🛑 Stopped serial reader after {update_count} updates.")
        ser.close()
        return True


# ============================================================
# ============================================================
# SIMULATOR / HARDCODED MODE — DISABLED
# ============================================================
# No hardcoded coordinates. Firebase is only updated with real
# GPS data received from the ESP32 over Serial (GPS:{...} lines).

def run_simulator():
    """Simulator disabled — exits so no fake data pollutes Firebase."""
    print("\n ESP32 not found and simulator mode is disabled.")
    print("   Connect your ESP32 via USB and restart this script.")
    print("   Firebase will NOT be updated with hardcoded coordinates.")
    sys.exit(1)


# ============================================================
# 🚀 MAIN
# ============================================================

def main():
    print("=" * 50)
    print("🚲 Ebike Location Backend")
    print("=" * 50)

    # Initialize Firebase
    initialize_firebase()

    # Try to find ESP32 serial port
    serial_port = find_serial_port()

    if serial_port:
        print(f"\n🔌 ESP32 detected on {serial_port}")
        print("   Mode: SERIAL GPS (real NEO-6M data)")
        read_serial_gps(serial_port)
    else:
        print("\n⚠️ No ESP32 serial device found.")
        print("   Falling back to SIMULATOR mode.")
        print("   Connect ESP32 via USB and restart to use real GPS.\n")
        run_simulator()


if __name__ == "__main__":
    main()

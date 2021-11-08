package ra.networkmanager;

import ra.common.network.Network;

public enum RelType {
    Seed,
    Banned,
    Reliable,
    LowLatency,
    NFC,
    Card,
    Tor,
    I2P,
    Bluetooth,
    WiFiDirect,
    Satellite,
    FSRadio,
    LiFi,
    IMS,
    Unknown;

    public static Network toNetwork(String relType) {
        switch (relType) {
            case "Tor": return Network.Tor;
            case "I2P": return Network.I2P;
            case "Bluetooth": return Network.Bluetooth;
            case "WiFi": return Network.WiFi;
            case "Satellite": return Network.Satellite;
            case "FSRadio": return Network.FSRadio;
            case "LiFi": return Network.LiFi;
            default: return null;
        }
    }

    public static RelType fromNetwork(String network) {
        switch (network) {
            case "NFC": return NFC;
            case "Card": return Card;
            case "Tor": return Tor;
            case "I2P": return I2P;
            case "Bluetooth": return Bluetooth;
            case "WiFi": return WiFiDirect;
            case "Satellite": return Satellite;
            case "FSRadio": return FSRadio;
            case "LiFi": return LiFi;
            case "IMS": return IMS;
            default: return Unknown;
        }
    }
}

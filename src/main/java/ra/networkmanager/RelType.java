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

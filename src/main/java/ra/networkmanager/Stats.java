package ra.networkmanager;

import java.util.Properties;

public class Stats {

    // Reliable
    public final int reliableTotalAcks;
    public final int reliableLatencyThresholdAcks;
    public final int reliableI2PAvgAckLatencyMs;
    public final int reliableI2PMedAckLatencyMs;
    public final int reliableTorAvgAckLatencyMs;
    public final int reliableTorMedAckLatencyMs;
    public final int reliableBluetoothAvgAckLatencyMs;
    public final int reliableBluetoothMedAckLatencyMs;

    // Super Reliable
    public final int superReliableTotalAcks;
    public final int superReliableLatencyThresholdAcks;
    public final int superReliableI2PAvgAckLatencyMs;
    public final int superReliableI2PMedAckLatencyMs;
    public final int superReliableTorAvgAckLatencyMs;
    public final int superReliableTorMedAckLatencyMs;
    public final int superReliableBluetoothAvgAckLatencyMs;
    public final int superReliableBluetoothMedAckLatencyMs;

    // SLAs
    public final int sla1AvgAckLatencyMs;
    public final int sla1MedAckLatencyMs;
    public final int sla2AvgAckLatencyMs;
    public final int sla2MedAckLatencyMs;
    public final int sla3AvgAckLatencyMs;
    public final int sla3MedAckLatencyMs;
    public final int sla4AvgAckLatencyMs;
    public final int sla4MedAckLatencyMs;
    public final int sla5AvgAckLatencyMs;
    public final int sla5MedAckLatencyMs;

    public Stats(Properties p){
        reliableTotalAcks = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.totalAcks"));
        reliableLatencyThresholdAcks = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.latencyThresholdAcks"));
        reliableI2PAvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.i2p.avgAckLatencyMs"));
        reliableI2PMedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.i2p.medAckLatencyMs"));
        reliableTorAvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.tor.avgAckLatencyMs"));
        reliableTorMedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.tor.medAckLatencyMs"));
        reliableBluetoothAvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.bluetooth.avgAckLatencyMs"));
        reliableBluetoothMedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.bluetooth.medAckLatencyMs"));

        superReliableTotalAcks = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.totalAcks"));
        superReliableLatencyThresholdAcks = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.latencyThresholdAcks"));
        superReliableI2PAvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.i2p.avgAckLatencyMs"));
        superReliableI2PMedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.i2p.medAckLatencyMs"));
        superReliableTorAvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.tor.avgAckLatencyMs"));
        superReliableTorMedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.tor.medAckLatencyMs"));
        superReliableBluetoothAvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.bluetooth.avgAckLatencyMs"));
        superReliableBluetoothMedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.superreliable.bluetooth.medAckLatencyMs"));

        sla1AvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.1.avgAckLatencyMs"));
        sla1MedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.1.medAckLatencyMs"));
        sla2AvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.2.avgAckLatencyMs"));
        sla2MedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.2.medAckLatencyMs"));
        sla3AvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.3.avgAckLatencyMs"));
        sla3MedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.3.medAckLatencyMs"));
        sla4AvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.4.avgAckLatencyMs"));
        sla4MedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.4.medAckLatencyMs"));
        sla5AvgAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.5.avgAckLatencyMs"));
        sla5MedAckLatencyMs = Integer.parseInt(p.getProperty("ra.networkmanager.stats.sla.5.medAckLatencyMs"));
    }

}

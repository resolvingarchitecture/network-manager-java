package ra.networkmanager;

import ra.common.JSONSerializable;
import ra.common.JSONParser;
import ra.common.JSONPretty;
import ra.common.network.NetworkPeer;

import java.util.*;

/**
 * Relationships among Network Peers.
 */
public class P2PRelationship implements JSONSerializable {

    public static String AVG_ACK_LATENCY_MS = "avgAckLatencyMs";
    public static String MEDIAN_ACK_LATENCY_MS = "medAckLatencyMs";
    public static String TOTAL_ACKS = "totalAcks";
    public static String LAST_ACK_TIME = "lastAckTime";

    private NetworkPeer startPeer;
    private NetworkPeer endPeer;
    private List<Long> ackTimes = new ArrayList<>();

    public P2PRelationship() {

    }

    public P2PRelationship(NetworkPeer startPeer, NetworkPeer endPeer) {
        this.startPeer = startPeer;
        this.endPeer = endPeer;
    }

    public void addAck(Long ack) {
        ackTimes.add(ack);
    }

    public Integer getTotalAcks() {
        return ackTimes.size();
    }

    public Long getAvgAckLatencyMS() {
        if(ackTimes==null) return 0L;
        long sum = 0L;
        for (long ts : ackTimes) {
            sum += ts;
        }
        return sum / ackTimes.size();
    }

    public Long getMedAckLatencyMS() {
        if(ackTimes==null) return 0L;
        ackTimes.sort((t1, t2) -> (int)(t1 - t2));
        return ackTimes.get(ackTimes.size() / 2);
    }

    public Long getLastAckTime() {
        if(ackTimes==null) return 0L;
        return ackTimes.get(ackTimes.size()-1);
    }

    public Boolean isReliable() {
        return getTotalAcks() > 100
                && getAvgAckLatencyMS() < 8000
                && getMedAckLatencyMS() < 8000;
    }

    public Boolean isSuperReliable(String peerId) {
        return getTotalAcks() > 1000
                && getAvgAckLatencyMS() < 4000
                && getMedAckLatencyMS() < 4000;
    }

    public Boolean isRealTime() {
        return getAvgAckLatencyMS() < 1000 && getMedAckLatencyMS() < 1000;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();

        return m;
    }

    public void fromMap(Map<String, Object> m) {
        if(m!=null) {

        }
    }

    public String toJSON() {
        return JSONPretty.toPretty(JSONParser.toString(toMap()), 4);
    }

    public void fromJSON(String json) {
        fromMap((Map<String, Object>)JSONParser.parse(json));
    }

    @Override
    public String toString() {
        return toJSON();
    }
}

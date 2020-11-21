package ra.networkmanager;

import org.neo4j.graphdb.RelationshipType;
import ra.common.JSONSerializable;
import ra.util.JSONParser;
import ra.util.JSONPretty;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Relationships among Network Peers.
 */
class P2PRelationship implements JSONSerializable {

    public static final String TOTAL_ACKS = "totalAcks";
    public static final String LAST_ACK_TIME = "lastAckTime";
    public static final String AVG_ACK_LATENCY_MS = "avgAckLatencyMS";
    public static final String MEDIAN_ACK_LATENCY_MS = "medAckLatencyMS";

    /**
     * Relationship Type is based on what Network was used to establish it.
     */
    public enum RelType implements RelationshipType {
        HTTP,
        TOR,
        I2P,
        Bluetooth,
        WiFiDirect,
        Satellite,
        FSRadio,
        LiFi,
        IMS
    }

    private Long totalAcks = 0L;
    private Long lastAckTime = 0L;
    private LinkedList<Long> ackTimesTracked = new LinkedList<>();

    public void setTotalAcks(long totalAcks) {
        this.totalAcks = totalAcks;
    }

    public Long getTotalAcks() {
        return totalAcks;
    }

    public void addAckTimeTracked(long t, int maxAcksTracked) {
        if(t <= 0) return; // not an ack
        ackTimesTracked.add(t);
        while(ackTimesTracked.size() > maxAcksTracked) {
            ackTimesTracked.removeFirst();
        }
        totalAcks++;
    }

    public void setAckTimesTracked(String trackedFlattened) {
        String[] tracked = trackedFlattened.split(",");
        for(String time : tracked) {
            ackTimesTracked.add(Long.parseLong(time));
        }
    }

    public String getAckTimesTracked() {
        StringBuilder sb = new StringBuilder();
        for(Long time : ackTimesTracked) {
            sb.append(time+",");
        }
        String trackedFlattened = sb.toString();
        trackedFlattened = trackedFlattened.substring(0, trackedFlattened.length()-1);
        return trackedFlattened;
    }

    public Long getAvgAckLatencyMS() {
        long sum = 0L;
        for (long ts : ackTimesTracked) {
            sum += ts;
        }
        return sum / ackTimesTracked.size();
    }

    public Long getMedAckLatencyMS() {
        ackTimesTracked.sort((t1, t2) -> (int)(t1 - t2));
        return ackTimesTracked.get(ackTimesTracked.size() / 2);
    }

    public void setLastAckTime(Long lastAckTime) {
        this.lastAckTime = lastAckTime;
    }
    public Long getLastAckTime() {
        return lastAckTime;
    }

    public Boolean isReliable() {
        return totalAcks > 100
                && getAvgAckLatencyMS() < 8000
                && getMedAckLatencyMS() < 8000;
    }

    public Boolean isSuperReliable() {
        return totalAcks > 1000
                && getAvgAckLatencyMS() < 4000
                && getMedAckLatencyMS() < 4000;
    }

    public Boolean isRealTime() {
        return getAvgAckLatencyMS() < 1000 && getMedAckLatencyMS() < 1000;
    }

    public Boolean belowMaxLatency(Long maxLatency) {
        return getAvgAckLatencyMS() < maxLatency && getMedAckLatencyMS() < maxLatency;
    }

    public static RelType networkToRelationship(String network) {
        switch (network) {
            case "HTTP": return RelType.HTTP;
            case "Tor": return RelType.TOR;
            case "I2P": return RelType.I2P;
            case "Bluetooth": return RelType.Bluetooth;
            case "WiFi": return RelType.WiFiDirect;
            case "Satellite": return RelType.Satellite;
            case "FSRadio": return RelType.FSRadio;
            case "LiFi": return RelType.LiFi;
            default: return null;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        if(totalAcks !=null) m.put(TOTAL_ACKS, totalAcks);
        m.put(AVG_ACK_LATENCY_MS, getAvgAckLatencyMS());
        m.put(MEDIAN_ACK_LATENCY_MS, getMedAckLatencyMS());
        if(lastAckTime!=null) m.put(LAST_ACK_TIME, lastAckTime);
        if(ackTimesTracked !=null) m.put("ackTimesTracked", getAckTimesTracked());
        return m;
    }

    public void fromMap(Map<String, Object> m) {
        if(m!=null) {
            if(m.get(TOTAL_ACKS)!=null)
                totalAcks = (Long)m.get(TOTAL_ACKS);
            if(m.get(LAST_ACK_TIME)!=null)
                lastAckTime = (Long)m.get(LAST_ACK_TIME);
            if(m.get("ackTimesTracked")!=null) {
                setAckTimesTracked((String)m.get("ackTimesTracked"));
            }
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

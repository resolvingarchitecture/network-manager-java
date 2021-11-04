package ra.networkmanager;

import ra.common.JSONSerializable;
import ra.common.JSONParser;
import ra.common.JSONPretty;

import java.util.*;

/**
 * Relationships among Network Peers.
 */
public class P2PRelationship implements JSONSerializable {

    public static final String ACKS_BY_PEER = "ACKS_BY_PEER";
    public static final String AVG_ACK_LATENCY_MS = "AVG_ACK_LATENCY_MS";
    public static final String MEDIAN_ACK_LATENCY_MS = "MEDIAN_ACK_LATENCY_MS";
    public static final String TOTAL_ACKS = "TOTAL_ACKS";
    public static final String LAST_ACK_TIME = "LAST_ACK_TIME";

    private Map<String, List<Long>> acksByPeer = new HashMap<>();
    private transient Map<String, Long> startTimes = new HashMap<>();

    public void startAck(String peerId, Long start) {
        startTimes.put(peerId, start);
    }

    public Long getStart(String peerId) {
        return startTimes.get(peerId);
    }

    public void clearStart(String peerId) {
        startTimes.remove(peerId);
    }

    public void addAck(String peerId, Long ack) {
        List<Long> acks = acksByPeer.get(peerId);
        if(acks==null) {
            acks = new LinkedList<>();
            acksByPeer.put(peerId, acks);
        }
        acks.add(ack);
    }

    public Integer getTotalAcks(String peerId) {
        List<Long> acks = acksByPeer.get(peerId);
        if(acks==null) {
            acks = new LinkedList<>();
            acksByPeer.put(peerId, acks);
        }
        return acks.size();
    }

    public Long getAvgAckLatencyMS(String peerId) {
        List<Long> acks = acksByPeer.get(peerId);
        if(acks==null) return 0L;
        long sum = 0L;
        for (long ts : acks) {
            sum += ts;
        }
        return sum / acks.size();
    }

    public Long getMedAckLatencyMS(String peerId) {
        List<Long> acks = acksByPeer.get(peerId);
        if(acks==null) return 0L;
        acks.sort((t1, t2) -> (int)(t1 - t2));
        return acks.get(acks.size() / 2);
    }

    public Long getLastAckTime(String peerId) {
        List<Long> acks = acksByPeer.get(peerId);
        if(acks==null) {
            acks = new LinkedList<>();
            acksByPeer.put(peerId, acks);
        }
        if(acks.size()==0) return 0L;
        return acks.get(acks.size()-1);
    }

    public Boolean isReliable(String peerId) {
        return getTotalAcks(peerId) > 100
                && getAvgAckLatencyMS(peerId) < 8000
                && getMedAckLatencyMS(peerId) < 8000;
    }

    public Boolean isSuperReliable(String peerId) {
        return getTotalAcks(peerId) > 1000
                && getAvgAckLatencyMS(peerId) < 4000
                && getMedAckLatencyMS(peerId) < 4000;
    }

    public Boolean isRealTime(String peerId) {
        return getAvgAckLatencyMS(peerId) < 1000 && getMedAckLatencyMS(peerId) < 1000;
    }

    public Boolean belowMaxLatency(String peerId, Long maxLatency) {
        return getAvgAckLatencyMS(peerId) < maxLatency && getMedAckLatencyMS(peerId) < maxLatency;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        if(acksByPeer !=null) m.put(ACKS_BY_PEER, acksByPeer);
        return m;
    }

    public void fromMap(Map<String, Object> m) {
        if(m!=null) {
            if(m.get(ACKS_BY_PEER)!=null) {
                acksByPeer = (Map<String,List<Long>>) m.get(ACKS_BY_PEER);
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

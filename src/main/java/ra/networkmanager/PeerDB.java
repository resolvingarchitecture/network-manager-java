package ra.networkmanager;

import ra.common.network.Network;
import ra.common.network.NetworkPeer;
import ra.common.RandomUtil;

import java.util.*;
import java.util.logging.Logger;

public class PeerDB {

    private static final Logger LOG = Logger.getLogger(PeerDB.class.getName());

    private String name;
    private Properties properties;

    private Map<String,NetworkPeer> peerById = new HashMap<>();
    private Map<String,NetworkPeer> peerByAddress = new HashMap<>();
    private Map<Network,Set<NetworkPeer>> peersByNetwork = new HashMap<>();
    private Map<String,Set<NetworkPeer>> peersByService = new HashMap<>();

    public Boolean savePeer(NetworkPeer p) {
        LOG.info("Saving NetworkPeer...");
        if(p.getId()==null || p.getId().isEmpty()) {
            LOG.warning("NetworkPeer.id is empty. Must have an id for Network Peers to save.");
            return false;
        }
        if(p.getNetwork()==null) {
            LOG.warning("NetworkPeer.network is empty. Must have a Network for Network Peers to save.");
            return false;
        }
        peerById.put(p.getId(),p);
        if(p.getDid()!=null && p.getDid().getPublicKey()!=null && p.getDid().getPublicKey().getAddress()!=null)
            peerByAddress.put(p.getDid().getPublicKey().getAddress(),p);
        if(peersByNetwork.get(p.getNetwork())==null)
            peersByNetwork.put(p.getNetwork(),new HashSet<>());
        peersByNetwork.get(p.getNetwork()).add(p);
        return true;
    }

    public int numberPeersByNetwork(Network network) {
        if(peersByNetwork.get(network)==null) return 0;
        return peersByNetwork.get(network).size();
    }

    public NetworkPeer randomPeer(NetworkPeer fromPeer) {
        if(fromPeer==null || fromPeer.getNetwork()==null || peersByNetwork.get(fromPeer.getNetwork())==null) return null;

        int random = RandomUtil.nextRandomInteger(0, peersByNetwork.get(fromPeer.getNetwork()).size());
        return ((NetworkPeer[])peersByNetwork.get(fromPeer.getNetwork()).toArray())[random];
    }

    public List<NetworkPeer> findPeersByService(String serviceName) {
        List<NetworkPeer> networkPeers = new ArrayList<>();

        return networkPeers;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean init(Properties p) {
        this.properties = p;
        return true;
    }

    public boolean teardown() {

        return true;
    }

}

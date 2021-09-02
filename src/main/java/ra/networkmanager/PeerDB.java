package ra.networkmanager;

import ra.common.network.Network;
import ra.common.network.NetworkPeer;
import ra.common.RandomUtil;

import java.util.*;
import java.util.logging.Logger;

public class PeerDB {

    private static final Logger LOG = Logger.getLogger(PeerDB.class.getName());

    private Properties properties;
    private Integer maxPeersByNetwork = 1000; // Default in code to one thousand

    private Map<Network,Set<NetworkPeer>> seedPeersByNetwork = new HashMap<>();

    private Map<String,NetworkPeer> peerById = new HashMap<>();
    private Map<String,NetworkPeer> peerByAddress = new HashMap<>();
    private Map<Network,Set<NetworkPeer>> peersByNetwork = new HashMap<>();
    private Map<String,Set<NetworkPeer>> peersByService = new HashMap<>();

    public void addSeed(NetworkPeer p) {
        if(seedPeersByNetwork.get(p.getNetwork())==null)
            seedPeersByNetwork.put(p.getNetwork(), new HashSet<>());
       seedPeersByNetwork.get(p.getNetwork()).add(p);
    }

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
        if(p.getDid()==null || p.getDid().getPublicKey()==null || p.getDid().getPublicKey().getAddress()==null) {
            LOG.warning("NetworkPeer.address is empty. Must have an address for Network Peers to save.");
            return false;
        }
        peerById.put(p.getId(),p);
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

    public int numberSeedPeersByNetwork(Network network) {
        if(seedPeersByNetwork.get(network)==null || seedPeersByNetwork.get(network).isEmpty()) return 0;
        return seedPeersByNetwork.get(network).size();
    }

    public NetworkPeer randomPeer(NetworkPeer fromPeer) {
        if(fromPeer==null || fromPeer.getNetwork()==null || peersByNetwork.get(fromPeer.getNetwork())==null) return null;

        int random = RandomUtil.nextRandomInteger(0, peersByNetwork.get(fromPeer.getNetwork()).size());
        return ((NetworkPeer[])peersByNetwork.get(fromPeer.getNetwork()).toArray())[random];
    }

    public Set<NetworkPeer> findPeersByService(String serviceName) {
        return peersByService.get(serviceName);
    }

    public boolean init(Properties p) {
        this.properties = p;
        if(p.getProperty("ra.networkmanager.maxPeersPerNetwork")!=null) {
            maxPeersByNetwork = Integer.parseInt(p.getProperty("ra.networkmanager.maxPeersPerNetwork"));
        }
        return true;
    }

    public boolean teardown() {

        return true;
    }

}

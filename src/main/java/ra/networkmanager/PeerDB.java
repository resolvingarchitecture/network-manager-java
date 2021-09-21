package ra.networkmanager;

import ra.common.network.Network;
import ra.common.network.NetworkGroup;
import ra.common.network.NetworkPeer;
import ra.common.RandomUtil;

import java.util.*;
import java.util.logging.Logger;

public class PeerDB {

    private static final Logger LOG = Logger.getLogger(PeerDB.class.getName());

    private Properties properties;
    private Integer maxPeersByNetwork = 1000; // Default in code to one thousand
    private final Integer peersToShareInDiscovery = 8;

    private final Map<Network,NetworkPeer> localPeerByNetwork = new HashMap<>();
    private final Map<Network,Set<NetworkPeer>> seedPeersByNetwork = new HashMap<>();

    private final Map<String,NetworkPeer> peerById = new HashMap<>();
    private final Map<String,NetworkPeer> peerByAddress = new HashMap<>();
    private final Map<Network,Set<NetworkPeer>> peersByNetwork = new HashMap<>();
    private final Map<String,Set<NetworkPeer>> peersByService = new HashMap<>();

    private final Map<UUID,NetworkGroup> networkGroupsById = new HashMap<>();

    public void addSeed(NetworkPeer p) {
        if(p.getNetwork()==null) return;
        if(seedPeersByNetwork.get(p.getNetwork())==null)
            seedPeersByNetwork.put(p.getNetwork(), new HashSet<>());
       seedPeersByNetwork.get(p.getNetwork()).add(p);
    }

    public Boolean savePeer(NetworkPeer p, boolean local) {
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
        if(local) {
            localPeerByNetwork.put(p.getNetwork(), p);
        } else {
            peerById.put(p.getId(), p);
            peerByAddress.put(p.getDid().getPublicKey().getAddress(), p);
            if (peersByNetwork.get(p.getNetwork()) == null)
                peersByNetwork.put(p.getNetwork(), new HashSet<>());
            peersByNetwork.get(p.getNetwork()).add(p);
        }
        return true;
    }

    public NetworkPeer findPeer(NetworkPeer np) {
        if(np.getId()!=null && peerById.get(np.getId())!=null)
            return peerById.get(np.getId());
        else if(np.getDid()!=null && np.getDid().getPublicKey()!=null && np.getDid().getPublicKey().getAddress()!=null && peerByAddress.get(np.getDid().getPublicKey().getAddress())!=null)
            return peerByAddress.get(np.getDid().getPublicKey().getAddress());
        else
            return null;
    }

    public int numberPeersByNetwork(Network network) {
        if(peersByNetwork.get(network)==null) return 0;
        return peersByNetwork.get(network).size();
    }

    public int numberSeedPeersByNetwork(Network network) {
        if(seedPeersByNetwork.get(network)==null || seedPeersByNetwork.get(network).isEmpty()) return 0;
        return seedPeersByNetwork.get(network).size();
    }

    public NetworkPeer getLocalPeerByNetwork(Network network) {
        return localPeerByNetwork.get(network);
    }

    public NetworkPeer getRandomSeedByNetwork(Network network) {
        int random = RandomUtil.nextRandomInteger(0, seedPeersByNetwork.get(network).size());
        return (NetworkPeer)seedPeersByNetwork.values().toArray()[random];
    }

    public NetworkPeer getRandomPeerByNetwork(Network network) {
        int random = RandomUtil.nextRandomInteger(0, peersByNetwork.get(network).size());
        return (NetworkPeer)peersByNetwork.values().toArray()[random];
    }

    public List<NetworkPeer> getRandomPeersToShareByNetwork(Network network) {
        Map<String, NetworkPeer> nps = new HashMap<>();
        int maxTries = peersToShareInDiscovery * 2;
        for(int i=0; i<maxTries; i++) {
            if(nps.size()==peersToShareInDiscovery)
                break;
            NetworkPeer np = getRandomPeerByNetwork(network);
            if(np==null)
                break;
            if(!nps.containsKey(np.getId()))
                nps.put(np.getId(), np);
        }
        return new ArrayList<>(nps.values());
    }

    public Set<NetworkPeer> findPeersByService(String serviceName) {
        return peersByService.get(serviceName);
    }

    public NetworkPeer peerWithInternetAccessAvailable(Network accessibleByNetwork) {

        return null;
    }

    public NetworkPeer peerWithSpecificNetworkAvailable(Network accessibleByNetwork, Network availableNetworkToPeer) {

        return null;
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

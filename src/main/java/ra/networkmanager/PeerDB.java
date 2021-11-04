package ra.networkmanager;

import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.List;
import java.util.Properties;
import java.util.Set;

public interface PeerDB {

    Boolean savePeer(NetworkPeer p, Boolean local, RelType relType);

    NetworkPeer findPeer(NetworkPeer np);

    int numberPeersByNetwork(Network network);

    int numberSeedPeersByNetwork(Network network);

    NetworkPeer getLocalPeerByNetwork(Network network);

    NetworkPeer getRandomSeedByNetwork(Network network);

    NetworkPeer getRandomPeerByNetwork(Network network);

    List<NetworkPeer> getRandomPeersToShareByNetwork(Network network);

    Set<NetworkPeer> findPeersByService(String serviceName);

    boolean init(Properties p);

    boolean teardown();
}

package ra.networkmanager;

import ra.common.Envelope;
import ra.common.network.NetworkPeer;
import ra.common.network.NetworkService;
import ra.common.network.NetworkState;
import ra.common.network.NetworkStatus;
import ra.common.tasks.BaseTask;
import ra.common.tasks.TaskRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class NetworkDiscovery extends BaseTask {

    private static Logger LOG = Logger.getLogger(NetworkDiscovery.class.getName());

    private final NetworkManagerService service;
//    private final P2PRelationship p2PRelationship;
    private final PeerDB peerDB;

    public NetworkDiscovery(TaskRunner taskRunner, NetworkManagerService service, PeerDB peerDB, P2PRelationship p2PRelationship) {
        super(NetworkDiscovery.class.getSimpleName(), taskRunner);
        this.service = service;
        this.peerDB = peerDB;
//        this.p2PRelationship = p2PRelationship;
    }

    @Override
    public Boolean execute() {
        List<NetworkState> networkStates = service.getNetworkStates();
        for(NetworkState ns : networkStates) { // Iterate through Networks that have reported to the Network Manager
            if(ns.networkStatus == NetworkStatus.CONNECTED) { // If that Network is reporting connected...
                Envelope e = Envelope.documentFactory();
                List<NetworkPeer> nps = peerDB.getRandomPeersToShareByNetwork(ns.network);
                if(!nps.isEmpty()) {
                    List<Map<String, Object>> mNps = new ArrayList<>();
                    for (NetworkPeer np : nps) {
                        mNps.add(np.toMap());
                    }
                    e.addNVP("peers",mNps);
                }
                NetworkPeer orig = peerDB.getLocalPeerByNetwork(ns.network);
                NetworkPeer dest;
                if(peerDB.numberPeersByNetwork(ns.network) == 0) {
                    if(peerDB.numberSeedPeersByNetwork(ns.network) == 0) {
                        LOG.info("No seeds therefore unable to bootstrap network.");
                        return false;
                    }
                    // Instruct Network Service to begin with provided seed
                    dest = peerDB.getRandomSeedByNetwork(ns.network);
                } else {
                    dest = peerDB.getRandomPeerByNetwork(ns.network);
                }
                // 3. Return results to this service
                e.addExternalRoute(NetworkManagerService.class.getName(),
                        NetworkManagerService.OPERATION_PEER_STATUS_REPLY,
                        dest,
                        orig);
                // 2. Send directly to remote specific Network Service in Peer as may not have Network Manager Service in use
                e.addExternalRoute(service.getNetworkServiceFromNetwork(ns.network),
                        NetworkService.OPERATION_PEER_STATUS,
                        orig,
                        dest);
                // 1. Send to local specific Network Service requesting to send on this request.
                e.addRoute(service.getNetworkServiceFromNetwork(ns.network),"SEND");
//                p2PRelationship.startAck(dest.getId(), new Date().getTime());
                service.send(e);
            }
        }
        return true;
    }
}

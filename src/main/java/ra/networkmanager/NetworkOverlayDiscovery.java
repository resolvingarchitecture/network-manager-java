package ra.networkmanager;

import ra.common.Envelope;
import ra.common.network.NetworkState;
import ra.common.network.NetworkStatus;
import ra.common.tasks.BaseTask;
import ra.common.tasks.TaskRunner;

import java.util.List;
import java.util.logging.Logger;

public class NetworkOverlayDiscovery extends BaseTask {

    private static Logger LOG = Logger.getLogger(NetworkOverlayDiscovery.class.getName());

    private final NetworkManagerService service;
    private final PeerDB peerDB;

    public NetworkOverlayDiscovery(TaskRunner taskRunner, NetworkManagerService service, PeerDB peerDB) {
        super(NetworkOverlayDiscovery.class.getSimpleName(), taskRunner);
        this.service = service;
        this.peerDB = peerDB;
    }

    @Override
    public Boolean execute() {
        List<NetworkState> networkStates = service.getNetworkStates();
        for(NetworkState ns : networkStates) { // Iterate through Networks that have reported to the Network Manager
            if(ns.networkStatus == NetworkStatus.CONNECTED) { // If that Network is reporting connected...
                if(peerDB.numberPeersByNetwork(ns.network) == 0 && peerDB.numberSeedPeersByNetwork(ns.network) > 0) {
                    // Instruct Network Service to begin with provided seed
                    Envelope e = Envelope.documentFactory();
                    LOG.warning("Network Overlay Discovery not yet implemented.");
                }
            }
        }
        return true;
    }
}

package ra.networkmanager;

import ra.common.network.NetworkState;
import ra.common.network.NetworkStatus;
import ra.common.tasks.BaseTask;
import ra.common.tasks.TaskRunner;

import java.util.List;

public class NetworkOverlayDiscovery extends BaseTask {

    private final NetworkManagerService service;
    private final PeerDB peerDB;

    public NetworkOverlayDiscovery(TaskRunner taskRunner, NetworkManagerService service, PeerDB peerDB) {
        super(NetworkOverlayDiscovery.class.getName(), taskRunner);
        this.service = service;
        this.peerDB = peerDB;
    }

    @Override
    public Boolean execute() {
        List<NetworkState> networkStates = service.getNetworkStates();
        for(NetworkState ns : networkStates) {
            if(ns.networkStatus == NetworkStatus.CONNECTED) {

            }
        }
        return null;
    }
}

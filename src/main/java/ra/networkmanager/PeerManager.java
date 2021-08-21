package ra.networkmanager;

import ra.common.network.NetworkPeer;
import ra.common.tasks.TaskRunner;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class PeerManager {

    private static Logger LOG = Logger.getLogger(PeerManager.class.getName());

    private PeerDB peerDB;

    private TaskRunner taskRunner;

    public PeerManager() {

    }

    public List<NetworkPeer> getPeersByService(String serviceName) {
        return peerDB.findPeersByService(serviceName);
    }

    public boolean init(String baseUrl, Properties p) {

        peerDB = new PeerDB();
        peerDB.setLocation(baseUrl);
        peerDB.setName("peerDB");

        if(!peerDB.init(p)) {
            LOG.severe("DB initialization failed.");
            return false;
        }

        return true;
    }

    public boolean teardown() {
        return peerDB.teardown();
    }

}

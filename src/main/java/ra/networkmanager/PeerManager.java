package ra.networkmanager;

import ra.common.DLC;
import ra.common.Envelope;
import ra.common.messaging.MessageProducer;
import ra.common.network.NetworkPeer;
import ra.common.network.NetworkState;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.ServiceStatusObserver;
import ra.util.Config;
import ra.util.tasks.TaskRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class PeerManager {

    private static Logger LOG = Logger.getLogger(PeerManager.class.getName());

    public static final String OPERATION_LOCAL_PEERS = "LOCAL_PEERS";

    public static final String OPERATION_LOCAL_PEER_BY_NETWORK = "LOCAL_PEER_BY_NETWORK";
    public static final String OPERATION_NUMBER_PEERS_BY_NETWORK = "NUMBER_PEERS_BY_NETWORK";
    public static final String OPERATION_RANDOM_PEER_BY_NETWORK = "RANDOM_PEER_BY_NETWORK";
    public static final String OPERATION_RANDOM_PEERS_BY_NETWORK = "RANDOM_PEERS_BY_NETWORK";

    // Sent by each Network Service
    public static final String OPERATION_UPDATE_PEER = "UPDATE_PEER";

    private PeerDB peerDB;
    private PeerRelationshipsDB relDB;

    private TaskRunner taskRunner;

    public PeerManager() {

    }

    public boolean init(String baseUrl, Properties p) {

        peerDB = new PeerDB();
        peerDB.setLocation(baseUrl);
        peerDB.setName("peerDB");

        relDB = new PeerRelationshipsDB();
        relDB.setLocation(baseUrl);
        relDB.setName("peerGraph");

        if(!peerDB.init(p) || !relDB.init(p)) {
            LOG.severe("DB initialization failed.");
            return false;
        }

        return true;
    }

    public boolean teardown() {
        return peerDB.teardown() && relDB.teardown();
    }

}

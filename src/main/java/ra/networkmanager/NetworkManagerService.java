package ra.networkmanager;

import ra.common.Envelope;
import ra.common.messaging.EventMessage;
import ra.common.messaging.MessageProducer;
import ra.common.network.*;
import ra.common.route.ExternalRoute;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.Service;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.common.Config;
import ra.common.FileUtil;
import ra.common.tasks.TaskRunner;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Network Manager as a service.
 *
 * Determines if requested network is available.
 * If so, it forwards on to that network service.
 * If not, it returns
 *
 */
public class NetworkManagerService extends BaseService {

    private static final Logger LOG = Logger.getLogger(NetworkManagerService.class.getName());

    // *** Network Management ***
    // Used by other services to delegate sending messages allowing the Network Manager to determine which network to
    // use if no External Route is present. If an External Route is present, the Network Manager will simply use it if available.
    // If not available, it will hold the message until it is available then send.
    public static final String OPERATION_SEND = "SEND";
    // Sent by Network Services to update the Network Manager on state changes.
    public static final String OPERATION_UPDATE_NETWORK_STATE = "UPDATE_NETWORK_STATE";
    // Returns a list of the current Networks States
    public static final String OPERATION_LOCAL_NETWORKS = "LOCAL_NETWORKS";
    // Returns a list of networks currently experiencing no difficulties in creating and maintaining connections
    public static final String OPERATION_ACTIVE_NETWORKS = "ACTIVE_NETWORKS";

    // *** Peer Management ***
    public static final String OPERATION_ADD_SEED_PEER = "ADD_SEED_PEER";
    public static final String OPERATION_LOCAL_PEERS = "LOCAL_PEERS";
    public static final String OPERATION_LOCAL_PEER_BY_NETWORK = "LOCAL_PEER_BY_NETWORK";
    public static final String OPERATION_NUMBER_PEERS_BY_NETWORK = "NUMBER_PEERS_BY_NETWORK";
    public static final String OPERATION_RANDOM_PEER_BY_NETWORK = "RANDOM_PEER_BY_NETWORK";
    public static final String OPERATION_RANDOM_PEERS_BY_NETWORK = "RANDOM_PEERS_BY_NETWORK";
    public static final String OPERATION_PEERS_BY_SERVICE = "PEERS_BY_SERVICE";

    // Sent by each Network Service
    public static final String OPERATION_UPDATE_PEER = "UPDATE_PEER";

    // Discover overlay network
    public static final String OPERATION_DISCOVER_OVERLAY = "DISCOVER_OVERLAY";
    public static final String OPERATION_PEER_STATUS = "PEER_STATUS";
    public static final String OPERATION_PEER_STATUS_REPLY = "PEER_STATUS_REPLY";

    protected final Map<String, NetworkState> networkStates = new HashMap<>();
    protected File messageHold;
    protected TaskRunner taskRunner;
    protected PeerDB peerDB;
    protected P2PRelationship p2PRelationship;

    public NetworkManagerService() {
        super();
        taskRunner = new TaskRunner(1,1);
        peerDB = new PeerDB();
    }

    public NetworkManagerService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
        taskRunner = new TaskRunner(1,1);
        peerDB = new PeerDB();
    }

    public NetworkManagerService(MessageProducer producer, ServiceStatusObserver observer, PeerDB peerDB) {
        super(producer, observer);
        this.peerDB = peerDB;
        taskRunner = new TaskRunner(1,1);
    }

    @Override
    public void handleEvent(Envelope envelope) {
        Route r = envelope.getDynamicRoutingSlip().getCurrentRoute();
        switch(r.getOperation()) {
            case OPERATION_UPDATE_NETWORK_STATE: {
                LOG.info("Received UPDATE_NETWORK_STATE event...");
                updateNetworkState(envelope);
                break;
            }
            default: {deadLetter(envelope);break;}
        }
    }

    @Override
    public void handleDocument(Envelope e) {
        Route r = e.getDynamicRoutingSlip().getCurrentRoute();
        switch(r.getOperation()) {
            case OPERATION_SEND: {
                if(e.getRoute()!=null && "ra.notification.NotificationService".equals(e.getRoute().getService())) {
                    // This is a notification from this service
                    producer.send(e);
                    break;
                }
                Route nextRoute = e.getDynamicRoutingSlip().peekAtNextRoute();
                String nextService = nextRoute.getService().toLowerCase();
                Network nextNetwork = getNetworkFromService(nextService);
                if(nextNetwork!=null) {
                    NetworkState networkState = networkStates.get(nextNetwork.name());
                    if (networkState == null || networkState.networkStatus != NetworkStatus.CONNECTED) {
                        sendToMessageHold(e);
                        break;
                    }
                }
                producer.send(e);
                break;
            }
            case OPERATION_ADD_SEED_PEER: {
                Object obj = e.getValue(NetworkPeer.class.getName()+":Seed");
                if(obj instanceof NetworkPeer) {
                    peerDB.addSeed((NetworkPeer) obj);
                }
                break;
            }
            case OPERATION_LOCAL_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    networks.add(ns.network.name());
                }
                e.addContent(networks);
                break;
            }
            case OPERATION_ACTIVE_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    if(ns.networkStatus == NetworkStatus.CONNECTED) {
                        networks.add(ns.network.name());
                    }
                }
                e.addContent(networks);
                break;
            }
            case OPERATION_PEERS_BY_SERVICE: {
                e.addNVP(NetworkPeer.class.getName(), peerDB.findPeersByService((String)e.getValue(Service.class.getName())));
                break;
            }
            case OPERATION_UPDATE_PEER: {
                if(e.getValue(NetworkPeer.class.getName())!=null) {
                    Object obj = e.getValue(NetworkPeer.class.getName());
                    if(obj instanceof NetworkPeer) {
                        peerDB.savePeer((NetworkPeer)obj);
                    }
                }
                break;
            }
            case OPERATION_PEER_STATUS_REPLY: {
                Route route = e.getRoute();
                if(route instanceof ExternalRoute) {
                   ExternalRoute extRoute = (ExternalRoute) route;
                   NetworkPeer orig = extRoute.getOrigination();
                   LOG.info("Adding ack...");
                   p2PRelationship.addAck(orig.getId(), new Date().getTime() - p2PRelationship.getStart(orig.getId()));
                }
            }
            default: {deadLetter(e);break;}
        }
    }

    protected void updateNetworkState(Envelope e) {
        if(!(e.getMessage() instanceof EventMessage)) {
            LOG.warning("Network State must be within an Event Message.");
            return;
        }
        EventMessage em = (EventMessage)e.getMessage();
        NetworkState networkState = (NetworkState)em.getMessage();
        networkStates.put(networkState.network.name(), networkState);
        switch (networkState.networkStatus) {
            case NOT_INSTALLED: {
                LOG.info(networkState.network.name() + " reporting not installed....");
                break;
            }
            case WAITING: {
                LOG.info(networkState.network.name() + " reporting waiting....");
                break;
            }
            case FAILED: {
                LOG.info(networkState.network.name() + " reporting network failed....");
                break;
            }
            case HANGING: {
                LOG.info(networkState.network.name() + " reporting network hanging....");
                break;
            }
            case PORT_CONFLICT: {
                LOG.info(networkState.network.name() + " reporting port conflict....");
                break;
            }
            case CONNECTING: {
                LOG.info(networkState.network.name() + " reporting connecting....");
                break;
            }
            case CONNECTED: {
                LOG.info(networkState.network.name() + " reporting connected.");
                break;
            }
            case DISCONNECTED: {
                LOG.info(networkState.network.name() + " reporting disconnected....");
                break;
            }
            case VERIFIED: {
                LOG.info(networkState.network.name() + " reporting verified.");
                break;
            }
            case BLOCKED: {
                LOG.info(networkState.network.name() + " reporting blocked.");
                break;
            }
            case ERROR: {
                LOG.info(networkState.network.name() + " reporting errored.");
                break;
            }
            default: {
                LOG.warning("Network Status for network "+networkState.network.name()+" not being handled: "+networkState.networkStatus.name());
            }
        }
        // Send on to subscribers
        e.addRoute("ra.notification.NotificationService","PUBLISH");
        producer.send(e);
    }

    public boolean isNetworkReady(Network network) {
        switch (network) {
            case HTTP: return NetworkStatus.CONNECTED == getNetworkStatus(Network.HTTP);
            case Tor: return NetworkStatus.CONNECTED == getNetworkStatus(Network.Tor);
            case I2P: return NetworkStatus.CONNECTED == getNetworkStatus(Network.I2P);
            case Bluetooth: return NetworkStatus.CONNECTED == getNetworkStatus(Network.Bluetooth);
            case WiFi: return NetworkStatus.CONNECTED == getNetworkStatus(Network.WiFi);
            case Satellite: return NetworkStatus.CONNECTED == getNetworkStatus(Network.Satellite);
            case FSRadio: return NetworkStatus.CONNECTED == getNetworkStatus(Network.FSRadio);
            case LiFi: return NetworkStatus.CONNECTED == getNetworkStatus(Network.LiFi);
            default: return false;
        }
    }

    public Network firstAvailableNonInternetNetwork() {
        if(getNetworkStatus(Network.Bluetooth)==NetworkStatus.CONNECTED)
            return Network.Bluetooth;
        else if(getNetworkStatus(Network.WiFi)==NetworkStatus.CONNECTED)
            return Network.WiFi;
        else if(getNetworkStatus(Network.Satellite)==NetworkStatus.CONNECTED)
            return Network.Satellite;
        else if(getNetworkStatus(Network.FSRadio)==NetworkStatus.CONNECTED)
            return Network.FSRadio;
        else if(getNetworkStatus(Network.LiFi)==NetworkStatus.CONNECTED)
            return Network.LiFi;
        else
            return null;
    }

    protected Network getNetworkFromService(String service) {
        Object obj = null;
        try {
            obj = Class.forName(service).getConstructor().newInstance();
            if(obj instanceof NetworkService) {
                NetworkService ns = (NetworkService)obj;
                return ns.getNetworkState().network;
            }
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return null;
    }

    protected NetworkStatus getNetworkStatus(Network network) {
        return networkStates.get(network.name()).networkStatus;
    }

    protected String getNetworkServiceFromNetwork(Network network) {
        switch (network) {
            case Tor: return "ra.tor.TORClientService";
            case I2P: return "ra.i2p.I2PEmbeddedService";
            case Bluetooth: return "ra.bluetooth.BluetoothService";
            case WiFi: return "ra.wifi.WiFiService";
            case Satellite: return "ra.satellite.SatelliteService";
            case FSRadio: return "ra.fsradio.FullSpectrumRadioService";
            case LiFi: return "ra.lifi.LiFiService";
            default: return null;
        }
    }

    protected boolean sendToMessageHold(Envelope e) {
        File envFile = new File(messageHold, e.getId());
        try {
            if(!envFile.createNewFile()) {
                LOG.warning("Unable to create file to persist Envelope waiting on network");
                return false;
            }
        } catch (IOException ioException) {
            LOG.warning(ioException.getLocalizedMessage());
            return false;
        }
        FileUtil.writeFile(e.toJSON().getBytes(), envFile.getAbsolutePath());
        LOG.info("Persisted message (id="+e.getId()+") to file for later sending.");
        return true;
    }

    List<NetworkState> getNetworkStates() {
        return new ArrayList<>(networkStates.values());
    }

    @Override
    public boolean start(Properties p) {
        super.start(p);
        LOG.info("Initializing...");
        updateStatus(ServiceStatus.INITIALIZING);
        try {
            config = Config.loadAll(p, "ra-network-manager.config");
        } catch (Exception e) {
            LOG.severe(e.getLocalizedMessage());
            return false;
        }
        config.put("ra.network.manager.dir", getServiceDirectory().getAbsolutePath());
        messageHold = new File(getServiceDirectory(), "msg");
        if(!messageHold.exists() && !messageHold.mkdir()) {
            LOG.severe("Unable to create message hold directory.");
            return false;
        }
        DelayedSend del = new DelayedSend(this, taskRunner, messageHold);
        del.setDelayed(true);
        del.setDelayTimeMS(5000L);
        del.setPeriodicity(60 * 1000L); // Check every minute
        taskRunner.addTask(del);
        NetworkOverlayDiscovery overlay = new NetworkOverlayDiscovery(taskRunner, this, peerDB, p2PRelationship);
        overlay.setDelayed(false);
        overlay.setPeriodicity(30 * 1000L); // Check every 30 seconds
        taskRunner.addTask(overlay);

        peerDB.init(config);

        updateStatus(ServiceStatus.RUNNING);
        return true;
    }

    @Override
    public boolean pause() {
        LOG.warning("Pausing not supported.");
        return false;
    }

    @Override
    public boolean unpause() {
        LOG.warning("Pausing not supported.");
        return false;
    }

    @Override
    public boolean restart() {
        LOG.info("Restarting...");
        gracefulShutdown();
        start(config);
        LOG.info("Restarted.");
        return true;
    }

    @Override
    public boolean shutdown() {
        LOG.info("Shutting down...");
        updateStatus(ServiceStatus.SHUTDOWN);
        LOG.info("Shutdown.");
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        return shutdown();
    }

}

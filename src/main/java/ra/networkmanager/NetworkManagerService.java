package ra.networkmanager;

import ra.common.*;
import ra.common.messaging.EventMessage;
import ra.common.messaging.MessageProducer;
import ra.common.network.*;
import ra.common.route.BaseRoute;
import ra.common.route.ExternalRoute;
import ra.common.route.Route;
import ra.common.route.SimpleExternalRoute;
import ra.common.service.BaseService;
import ra.common.service.Service;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.common.tasks.TaskRunner;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static ra.common.JSONParser.parse;

/**
 * Network Manager as a service.
 *
 * Manages Network Peers providing reports.
 *
 */
public class NetworkManagerService extends BaseService {

    private static final Logger LOG = Logger.getLogger(NetworkManagerService.class.getName());

    // *** Network Management ***
    // Used by other services to delegate sending messages allowing the Network Manager to determine which network to
    // use if no External Route is present. If an External Route is present, the Network Manager will simply use it if available.
    // If not available, it will hold the message until it is available then send.
    public static final String OPERATION_SEND = "SEND";
    // Sends the payload to the supplied list of Network Peers.
    public static final String OPERATION_PUBLISH = "PUBLISH";
    // Sent by Network Services to update the Network Manager on state changes.
    public static final String OPERATION_UPDATE_NETWORK_STATE = "UPDATE_NETWORK_STATE";
    // Returns a list of the current Networks States
    public static final String OPERATION_LOCAL_NETWORKS = "LOCAL_NETWORKS";
    // Returns a list of networks currently experiencing no difficulties in creating and maintaining connections
    public static final String OPERATION_ACTIVE_NETWORKS = "ACTIVE_NETWORKS";
    // Network status
    public static final String OPERATION_NETWORK_STATUS = "NETWORK_STATUS";
    // Network connected
    public static final String OPERATION_NETWORK_CONNECTED = "NETWORK_CONNECTED";

    // *** Peer Management ***
    public static final String OPERATION_ADD_SEED_PEER = "ADD_SEED_PEER";
    public static final String OPERATION_LOCAL_PEERS = "LOCAL_PEERS";
    public static final String OPERATION_LOCAL_PEER_BY_NETWORK = "LOCAL_PEER_BY_NETWORK";
    public static final String OPERATION_NUMBER_PEERS_BY_NETWORK = "NUMBER_PEERS_BY_NETWORK";
    public static final String OPERATION_RANDOM_PEER_BY_NETWORK = "RANDOM_PEER_BY_NETWORK";
    public static final String OPERATION_RANDOM_PEERS_BY_NETWORK = "RANDOM_PEERS_BY_NETWORK";
    public static final String OPERATION_PEERS_BY_SERVICE = "PEERS_BY_SERVICE";

    // Sent by each Network Service
    public static final String OPERATION_UPDATE_LOCAL_PEER = "UPDATE_LOCAL_PEER";
    public static final String OPERATION_UPDATE_PEER = "UPDATE_PEER";
    public static final String OPERATION_UPDATE_PEERS = "UPDATE_PEERS";

    // Community Networks
    public static final String OPERATION_START_COMMUNITY_NETWORK = "START_COMMUNITY_NETWORK";
    public static final String OPERATION_DISCOVER_COMMUNITY = "DISCOVER_COMMUNITY_NETWORK";
    public static final String OPERATION_PEER_STATUS = "PEER_STATUS";
    public static final String OPERATION_PEER_STATUS_REPLY = "PEER_STATUS_REPLY";

    // Network Name, Network
    protected final Map<String, NetworkState> networkStates = new HashMap<>();
    protected File messageHold;
    protected TaskRunner taskRunner;
    protected PeerDB peerDB;
    protected Stats stats;

    public NetworkManagerService() {
        super();
        taskRunner = new TaskRunner(1,1);
    }

    public NetworkManagerService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
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
                if(e.getValue(NetworkPeer.class.getName())==null) {
                    LOG.warning("Unable to send to missing peer.");
                    deadLetter(e);
                    break;
                }
                NetworkPeer np = (NetworkPeer)e.getValue(NetworkPeer.class.getName());
                Tuple2<Boolean,ResponseCodes> result = setExternalRoute(np, e);
                if(result.first) {
                    send(e);
                } else {
                    LOG.warning(result.second.name());
                    deadLetter(e);
                }
                break;
            }
            case OPERATION_PUBLISH: {
                if(e.getValue(NetworkPeer.class.getName())==null) {
                    LOG.warning("Unable to publish to no peers.");
                    deadLetter(e);
                    break;
                }
                // Get peers
                List<NetworkPeer> peers = (List<NetworkPeer>)e.getValue(NetworkPeer.class.getName());
                for(NetworkPeer dp : peers) {
                    Envelope eDp = Envelope.envelopeFactory(e);
                    // Clear out list
                    eDp.addNVP(NetworkPeer.class.getName(), null);
                    // Ensure External Route is selected and set
                    Tuple2<Boolean,ResponseCodes> result = setExternalRoute(dp, e);
                    if(result.first) {
                        send(e);
                    } else {
                        LOG.warning(result.second.name());
                        deadLetter(e);
                    }
                }
                break;
            }
            case OPERATION_ADD_SEED_PEER: {
                Object obj = e.getValue(NetworkPeer.class.getName());
                if(obj instanceof NetworkPeer) {
                    peerDB.savePeer((NetworkPeer) obj, false, RelType.Seed);
                }
                break;
            }
            case OPERATION_LOCAL_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    networks.add(ns.network.name());
                }
                e.addNVP(NetworkPeer.class.getName(), networks);
                break;
            }
            case OPERATION_ACTIVE_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    if(ns.networkStatus == NetworkStatus.CONNECTED) {
                        networks.add(ns.network.name());
                    }
                }
                e.addNVP(NetworkPeer.class.getName(), networks);
                break;
            }
            case OPERATION_NETWORK_STATUS: {
                Object networkObj = e.getValue(Network.class.getName());
                Network network = null;
                if(networkObj instanceof Network) {
                    network = (Network)networkObj;
                } else if(networkObj instanceof String) {
                    network = Network.valueOf((String)networkObj);
                }
                if(network!=null) {
                    e.addNVP(Network.class.getName(), getNetworkStatus(network));
                }
                break;
            }
            case OPERATION_NETWORK_CONNECTED: {
                Object networkObj = e.getValue(Network.class.getName());
                Network network = null;
                if(networkObj instanceof Network) {
                    network = (Network)networkObj;
                } else if(networkObj instanceof String) {
                    network = Network.valueOf((String)networkObj);
                }
                if(network!=null) {
                    e.addNVP(Network.class.getName(), isNetworkReady(network).toString());
                }
                break;
            }
            case OPERATION_PEERS_BY_SERVICE: {
                e.addNVP(NetworkPeer.class.getName(), peerDB.findPeersByService((String)e.getValue(Service.class.getName())));
                break;
            }
            case OPERATION_UPDATE_LOCAL_PEER: {
                if(e.getValue(NetworkPeer.class.getName())!=null) {
                    Object obj = e.getValue(NetworkPeer.class.getName());
                    if(obj instanceof NetworkPeer) {
                        NetworkPeer p = (NetworkPeer)obj;
                        if(p.getNetwork()!=null) {
                            peerDB.savePeer(p, true, RelType.fromNetwork(p.getNetwork()));
                        }
                    }
                }
                break;
            }
            case OPERATION_UPDATE_PEER: {
                if(e.getValue(NetworkPeer.class.getName())!=null) {
                    Object obj = e.getValue(NetworkPeer.class.getName());
                    if(obj instanceof NetworkPeer) {
                        NetworkPeer p = (NetworkPeer)obj;
                        if(p.getNetwork()!=null) {
                            peerDB.savePeer(p, false, RelType.fromNetwork(p.getNetwork()));
                        }
                    }
                }
                break;
            }
            case OPERATION_UPDATE_PEERS: {
                Object peersObj = e.getValue(NetworkPeer.class.getName());
                List<NetworkPeer> peers = null;
                if(peersObj instanceof List) {
                    peers = (List<NetworkPeer>)peersObj;
                } else if(peersObj instanceof String) {
                    List<Map<String,Object>> objects = (List<Map<String,Object>>) JSONParser.parse(peers);
                    peers = new ArrayList<>();
                    for(Map<String,Object> m : objects) {
                        Network network = Network.valueOf((String)m.get("network"));
                        NetworkPeer np = new NetworkPeer(network);
                        np.fromMap(m);
                        peers.add(np);
                    }
                } else {
                    LOG.warning("Unable to recognize peers list for updating.");
                    deadLetter(e);
                    break;
                }
                for(NetworkPeer p : peers) {
                    if(p.getNetwork()!=null) {
                        peerDB.savePeer(p, false, RelType.fromNetwork(p.getNetwork()));
                    }
                }
                break;
            }
            case OPERATION_PEER_STATUS_REPLY: {
                Route route = e.getRoute();
                if(route instanceof ExternalRoute) {
                    ExternalRoute extRoute = (ExternalRoute) route;
                    NetworkPeer orig = extRoute.getOrigination();
                    if(orig.getNetwork()!=null) {
                        peerDB.savePeer(orig, false, RelType.fromNetwork(orig.getNetwork()));
                    }
                    LOG.info("Adding ack...");
//                   p2PRelationship.addAck(orig.getId(), new Date().getTime() - p2PRelationship.getStart(orig.getId()));
                    if(e.getValue("peers")!=null) {
                       List<Map<String,Object>> peerMaps = (List<Map<String,Object>>)e.getValue("peers");
                       for(Map<String,Object> peerMap : peerMaps) {
                           Network network = Network.valueOf((String)peerMap.get("network"));
                           NetworkPeer np = new NetworkPeer(network);
                           np.fromMap(peerMap);
                           if(np.getNetwork()!=null) {
                               peerDB.savePeer(np, false, RelType.fromNetwork(np.getNetwork()));
                           }
                       }
                    }
                }
                break;
            }
            case OPERATION_NUMBER_PEERS_BY_NETWORK: {
                Map<String,Object> m = new HashMap<>();
                for(NetworkState ns : networkStates.values()) {
                    m.put(ns.network.name(), peerDB.numberPeersByNetwork(ns.network));
                }
                e.addNVP(OPERATION_NUMBER_PEERS_BY_NETWORK,m);
                break;
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

    public Boolean isNetworkReady(Network network) {
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
        switch (service) {
            case "ra.tor.TORClientService": return Network.Tor;
            case "ra.i2p.I2PEmbeddedService": return Network.I2P;
            case "ra.bluetooth.BluetoothService": return Network.Bluetooth;
            case "ra.wifi.WiFiService": return Network.WiFi;
            case "ra.satellite.SatelliteService": return Network.Satellite;
            case "ra.fsradio.FullSpectrumRadioService": return Network.FSRadio;
            case "ra.lifi.LiFiService": return Network.LiFi;
            case "ra.http.HttpService": return Network.HTTP;
            default: return null;
        }
    }

    protected NetworkStatus getNetworkStatus(Network network) {
        return networkStates.get(network.name()).networkStatus;
    }

    protected String getNetworkServiceFromNetwork(Network network) {
        switch (network) {
            case HTTP: return "ra.http.HttpService";
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

    protected Tuple2<Boolean, ResponseCodes> setExternalRoute(NetworkPeer np, Envelope e) {
        // Get preferred Network service
        Route nextRoute = e.getDynamicRoutingSlip().peekAtNextRoute();
        Network preferredNetwork = null;
        if(nextRoute instanceof ExternalRoute) {
            preferredNetwork = getNetworkFromService(nextRoute.getService());
        } else {
            LOG.warning("Next route must be an ExternalRoute.");
            return new Tuple2<>(false,ResponseCodes.NEXT_ROUTE_MUST_BE_AN_EXTERNAL_ROUTE);
        }
        if(preferredNetwork!=null) {
            NetworkState networkState = networkStates.get(preferredNetwork.name());
            if (networkState == null || networkState.networkStatus != NetworkStatus.CONNECTED) {
                preferredNetwork = null;
            }
        }
        Network peerNetwork = np.getNetwork();
        if(peerNetwork==null) {
            if(np.getDid()!=null && np.getDid().getPublicKey()!=null && np.getDid().getPublicKey().getAddress()!=null) {
                // Lookup to see if we know this peer's network
                NetworkPeer npFound = peerDB.findPeer(np);
                if (npFound != null && npFound.getNetwork() != null) {
                    peerNetwork = npFound.getNetwork();
                    NetworkState networkState = networkStates.get(peerNetwork.name());
                    if (networkState == null || networkState.networkStatus != NetworkStatus.CONNECTED) {
                        peerNetwork = null;
                    }
                }
            }
        } else {
            NetworkState ns = networkStates.get(peerNetwork.name());
            if(ns==null || ns.networkStatus != NetworkStatus.CONNECTED) {
                peerNetwork = null;
            }
        }
        if(peerNetwork==null) {
            return new Tuple2<>(false, ResponseCodes.UNABLE_TO_SELECT_PEER_NETWORK);
        }
        String service = getNetworkServiceFromNetwork(peerNetwork);
        if(service==null) {
            return new Tuple2<>(false, ResponseCodes.SERVICE_NOT_FOUND_FOR_NETWORK);
        }
        NetworkPeer lp = peerDB.getLocalPeerByNetwork(peerNetwork);
        nextRoute = e.getRoute();
        if (lp == null) {
            return new Tuple2<>(false, ResponseCodes.LOCAL_PEER_FOR_NETWORK_NOT_AVAILABLE);
        } else if(nextRoute==null) {
            e.addExternalRoute(service, "SEND", lp, np);
            return new Tuple2<>(true, ResponseCodes.READY);
        } else if(nextRoute instanceof SimpleExternalRoute) {
            BaseRoute baseRoute = (BaseRoute) nextRoute;
            baseRoute.setService(service);
            baseRoute.setOperation("SEND"); // Ensure it is sending
            SimpleExternalRoute extRoute = (SimpleExternalRoute) nextRoute;
            extRoute.setOrigination(lp);
            extRoute.setDestination(np);
            return new Tuple2<>(true, ResponseCodes.READY);
        }
        return new Tuple2<>(false, ResponseCodes.UNABLE_TO_DETERMINE_EXTERNAL_ROUTE);
    }

    @Override
    public boolean start(Properties p) {
        super.start(p);
        LOG.info("Initializing...");
        updateStatus(ServiceStatus.INITIALIZING);
        try {
            config = Config.loadAll(p, "ra-network-manager.config");
            stats = new Stats(config);
        } catch (Exception e) {
            LOG.severe(e.getLocalizedMessage());
            return false;
        }
        config.put("ra.networkmanager.dir", getServiceDirectory().getAbsolutePath());
        messageHold = new File(getServiceDirectory(), "msg");
        if(!messageHold.exists() && !messageHold.mkdir()) {
            LOG.severe("Unable to create message hold directory.");
            return false;
        }

        initPeerDB();
        initDelayedSend();
        initDiscovery();

        updateStatus(ServiceStatus.RUNNING);
        return true;
    }

    protected boolean initPeerDB() {
        this.peerDB = new InMemoryPeerDB(); // Default
        return this.peerDB.init(config);
    }

    protected void initDelayedSend() {
        DelayedSend del = new DelayedSend(this, taskRunner, messageHold);
        del.setDelayed(true);
        del.setDelayTimeMS(10 *1000L); // Delay by 10 seconds
        del.setPeriodicity(60 * 1000L); // Check every minute
        taskRunner.addTask(del);
    }

    protected void initDiscovery() {
        NetworkDiscovery overlay = new NetworkDiscovery(taskRunner, this, peerDB, config);
        overlay.setDelayed(true);
        overlay.setDelayTimeMS(40 * 1000L); // Delay for 40 seconds to start 30 seconds after DelaySend task
        overlay.setPeriodicity(60 * 1000L); // Check every minute
        taskRunner.addTask(overlay);
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

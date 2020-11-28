package ra.networkmanager;

import ra.common.Envelope;
import ra.common.messaging.MessageProducer;
import ra.common.network.Network;
import ra.common.network.NetworkService;
import ra.common.network.NetworkState;
import ra.common.network.NetworkStatus;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.util.Config;
import ra.util.FileUtil;
import ra.util.tasks.TaskRunner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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

    // Used by other services to delegate sending messages allowing the Network Manager to determine which network to
    // use if no External Route is present. If an External Route is present, the Network Manager will simply use it.
    public static final String OPERATION_SEND = "SEND";
    // Sent by Network Services to update the Network Manager on state changes.
    public static final String OPERATION_UPDATE_NETWORK_STATE = "UPDATE_NETWORK_STATE";
    // Sent by End Users to update the Network Manager on their situation.
    public static final String OPERATION_UPDATE_SITUATIONAL_STATE = "UPDATE_SITUATIONAL_STATE";
    // Sent by Press Freedom Index Scraper and/or other services evaluating global state.
    public static final String OPERATION_UPDATE_GLOBAL_STATE = "UPDATE_GLOBAL_STATE";
    // Returns a list of the current Networks States
    public static final String OPERATION_LOCAL_NETWORKS = "LOCAL_NETWORKS";
    // Returns a list of networks currently experiencing no difficulties in creating and maintaining connections
    public static final String OPERATION_ACTIVE_NETWORKS = "ACTIVE_NETWORKS";

    protected Map<String, NetworkState> networkStates = new HashMap<>();
    protected File messageHold;
    protected final TaskRunner taskRunner;
    protected PeerManager peerManager;

    public NetworkManagerService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
        taskRunner = new TaskRunner(1,1);
        peerManager = new PeerManager();
    }

    @Override
    public void handleDocument(Envelope envelope) {
        Route r = envelope.getDynamicRoutingSlip().getCurrentRoute();
        switch(r.getOperation()) {
            case OPERATION_SEND: {
                send(envelope);break;
            }
            case OPERATION_UPDATE_NETWORK_STATE: {
                updateNetworkState(envelope);
                break;
            }
            case OPERATION_UPDATE_SITUATIONAL_STATE: {

                break;
            }
            case OPERATION_UPDATE_GLOBAL_STATE: {

                break;
            }
            case OPERATION_LOCAL_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    networks.add(ns.network.name());
                }
                envelope.addContent(networks);
                break;
            }
            case OPERATION_ACTIVE_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    if(ns.networkStatus == NetworkStatus.CONNECTED) {
                        networks.add(ns.network.name());
                    }
                }
                envelope.addContent(networks);
                break;
            }
            default: {deadLetter(envelope);break;}
        }
    }

    protected void updateNetworkState(Envelope envelope) {
        NetworkState networkState = (NetworkState)envelope.getContent();
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
                LOG.info(networkState.network.name() + " reporting error. Initiating hard restart...");
                break;
            }
            default: LOG.warning("Network Status for network "+networkState.network.name()+" not being handled: "+networkState.networkStatus.name());
        }
    }

    protected boolean isNetworkReady(Network network) {
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

    protected Network getNetworkFromService(String service) {
        Object obj = null;
        try {
            obj = Class.forName(service).getConstructor().newInstance();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
            return null;
        }
        if(obj instanceof NetworkService) {
            NetworkService ns = (NetworkService)obj;
            return ns.getNetworkState().network;
        }
        return null;
    }

    protected NetworkStatus getNetworkStatus(Network network) {
        return networkStates.get(network.name()).networkStatus;
    }

    @Override
    public boolean send(Envelope e) {
        // Evaluate what to do based on desired network
        Route r = e.getDynamicRoutingSlip().peekAtNextRoute();
        String service = r.getService().toLowerCase();
        Network network = getNetworkFromService(service);
        if(network==null) {
            LOG.warning("Network Service requested not a Network Service; dead lettering envelope.");
            deadLetter(e);
            return true;
        }
        NetworkState networkState = networkStates.get(network.name());
        if(networkState==null) {
            LOG.warning("Network Service requested not running; dead lettering envelope for now.");
            deadLetter(e);
            return true;
        }
        if(networkState.networkStatus != NetworkStatus.CONNECTED) {
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
        return producer.send(e);
    }

    Collection<NetworkState> getNetworkStates() {
        return networkStates.values();
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
        DelayedSend task = new DelayedSend(this, taskRunner, messageHold);
        task.setDelayed(true);
        task.setDelayTimeMS(5000L);
        task.setPeriodicity(60 * 1000L); // Check every minute
        taskRunner.addTask(task);

        peerManager.init(getServiceDirectory().getAbsolutePath(), config);

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

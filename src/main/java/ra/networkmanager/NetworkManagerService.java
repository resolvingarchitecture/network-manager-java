package ra.networkmanager;

import ra.common.Envelope;
import ra.common.messaging.MessageProducer;
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
                NetworkState networkState = (NetworkState)envelope.getContent();
                networkStates.put(networkState.network, networkState);
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
                    networks.add(ns.network);
                }
                envelope.addContent(networks);
                break;
            }
            case OPERATION_ACTIVE_NETWORKS: {
                List<String> networks = new ArrayList<>();
                for(NetworkState ns : networkStates.values()) {
                    if(ns.networkStatus == NetworkStatus.CONNECTED) {
                        networks.add(ns.network);
                    }
                }
                envelope.addContent(networks);
                break;
            }
            default: {deadLetter(envelope);break;}
        }
    }

    @Override
    public boolean send(Envelope e) {
        // Evaluate what to do based on desired network
        Route r = e.getDynamicRoutingSlip().peekAtNextRoute();
        String service = r.getService().toLowerCase();
        boolean ready = false;
        for(NetworkState ns : networkStates.values()) {
            if(service.startsWith(ns.network.toLowerCase())) {
                if(ns.networkStatus == NetworkStatus.CONNECTED) {
                    ready = true;
                }
            }
        }
        if(!ready) {
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

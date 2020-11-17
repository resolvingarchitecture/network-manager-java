package ra.network.manager;

import ra.common.Envelope;
import ra.common.messaging.MessageProducer;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.util.Config;

import java.util.*;
import java.util.logging.Logger;

/**
 * Network Manager as a service.
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

    public NetworkManagerService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
    }

    @Override
    public void handleDocument(Envelope envelope) {
        Route r = envelope.getDynamicRoutingSlip().getCurrentRoute();
        switch(r.getOperation()) {
            case OPERATION_SEND: {send(envelope);break;}
            case OPERATION_UPDATE_NETWORK_STATE: {

                break;
            }
            case OPERATION_UPDATE_SITUATIONAL_STATE: {

                break;
            }
            case OPERATION_UPDATE_GLOBAL_STATE: {

                break;
            }
            case OPERATION_LOCAL_NETWORKS: {

                break;
            }
            case OPERATION_ACTIVE_NETWORKS: {

                break;
            }
            default: {deadLetter(envelope);break;}
        }
    }

    @Override
    public boolean send(Envelope e) {
        // If next route is an External Route just forward it to the route.

        // If not, determine best External Route for this message and add it to the slip then forward it on.

        return false;
    }

    @Override
    public boolean start(Properties p) {
        super.start(p);
        LOG.info("Initializing...");
        updateStatus(ServiceStatus.INITIALIZING);
        try {
            config = Config.loadFromClasspath("ra-network-manager.config", p, false);
        } catch (Exception e) {
            LOG.severe(e.getLocalizedMessage());
            return false;
        }
        config.put("ra.network.manager.dir", getServiceDirectory().getAbsolutePath());
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

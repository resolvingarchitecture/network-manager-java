package ra.networkmanager;

import ra.common.Envelope;
import ra.common.network.NetworkState;
import ra.common.network.NetworkStatus;
import ra.common.route.Route;
import ra.util.FileUtil;
import ra.util.tasks.BaseTask;
import ra.util.tasks.TaskRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DelayedSend extends BaseTask {

    private static final Logger LOG = Logger.getLogger(DelayedSend.class.getName());

    private NetworkManagerService service;
    private File messageHold;

    public DelayedSend(NetworkManagerService service, TaskRunner taskRunner, File messageHold) {
        super(DelayedSend.class.getSimpleName(), taskRunner);
        this.service = service;
        this.messageHold = messageHold;
    }

    @Override
    public Boolean execute() {
        File[] messages = messageHold.listFiles();
        List<NetworkState> networkStates = new ArrayList<>(service.networkStates.values());
        for(File msg : messages) {
            byte[] bytes = new byte[0];
            try {
                bytes = FileUtil.readFile(msg.getAbsolutePath());
            } catch (IOException e) {
                LOG.warning(e.getLocalizedMessage());
                continue;
            }
            String json = new String(bytes);
            Envelope e = Envelope.documentFactory();
            e.fromJSON(json);
            Route r = e.getDynamicRoutingSlip().peekAtNextRoute();
            String serviceName = r.getService();
            for(NetworkState ns : networkStates) {
                if(serviceName.startsWith(ns.network.name().toLowerCase())) {
                    if(ns.networkStatus == NetworkStatus.CONNECTED) {
                        if(service.send(e)) {
                            LOG.info("Delayed message sent successfully.");
                            if(!msg.delete()) {
                                LOG.warning("Message sent successfully but unable to delete its file: "+msg.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}

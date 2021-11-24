package ra.networkmanager;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.Properties;
import java.util.logging.Logger;

public class NetworkManagerServiceTest {

    private static final Logger LOG = Logger.getLogger(NetworkManagerServiceTest.class.getName());

    private static NetworkManagerService service;
    private static MockProducer producer;
    private static Properties props;
    private static boolean ready = false;

    @BeforeClass
    public static void init() {
        LOG.info("Init...");
        props = new Properties();

        producer = new MockProducer();
        service = new NetworkManagerService(producer, null);

        ready = service.start(props);
    }

    @AfterClass
    public static void tearDown() {
        LOG.info("Teardown...");
        service.gracefulShutdown();
    }

    @Test
    public void verifyInitializedTest() {
        Assert.assertTrue(ready);
    }

    public void test2() {
        Envelope e = Envelope.documentFactory();
        NetworkPeer np = new NetworkPeer(Network.I2P, "Anon", "1234");
        e.addNVP(NetworkPeer.class.getName(), np);
        e.addRoute(NetworkManagerService.class, NetworkManagerService.OPERATION_ADD_SEED_PEER);
        service.handleDocument(e);

    }

}

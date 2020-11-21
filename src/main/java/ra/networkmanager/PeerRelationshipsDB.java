package ra.networkmanager;

import org.neo4j.graphalgo.CostEvaluator;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.schema.IndexDefinition;
import ra.common.network.NetworkPeer;
import ra.util.FileUtil;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

class PeerRelationshipsDB {

    private static final Logger LOG = Logger.getLogger(PeerRelationshipsDB.class.getName());

    public static final Label PEER_LABEL = Label.label("Peer");

    private boolean initialized = false;
    private String location;
    private String name;
    private Properties properties;
    private GraphDatabaseService graphDb;
    private Label peerLabel = Label.label("Peer");
    private final Object peerSaveLock = new Object();

    private final int MaxAcksTracked = 50;
    private final int MaxPeersTracked = 1000;

    public PeerRelationshipsDB() {
        super();
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GraphDatabaseService getGraphDb() {
        return graphDb;
    }

    public List<NetworkPeer> findLeastHopsPath(String fromPeerId, String toPeerId) {
        List<NetworkPeer> leastHopsPath = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            PathFinder<Path> finder = GraphAlgoFactory.shortestPath(PathExpanders.forTypeAndDirection(P2PRelationship.RelType.IMS, Direction.OUTGOING), 15);
            Node startNode = findPeerNode(fromPeerId);
            Node endNode = findPeerNode(toPeerId);
            if (startNode != null && endNode != null) {
                Path p = finder.findSinglePath(startNode, endNode);
                NetworkPeer np;
                for(Node n : p.nodes()) {
                    np = toPeer(n);
                    leastHopsPath.add(np);
                }
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return leastHopsPath;
    }

    public List<NetworkPeer> findLowestLatencyPath(String fromPeerId, String toPeerId) {
        List<NetworkPeer> lowestLatencyPath = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            PathFinder<WeightedPath> finder = GraphAlgoFactory.dijkstra(PathExpanders.forTypeAndDirection(P2PRelationship.RelType.IMS, Direction.OUTGOING), P2PRelationship.AVG_ACK_LATENCY_MS);
            Node startNode = findPeerNode(fromPeerId);
            Node endNode = findPeerNode(toPeerId);
            if (startNode != null && endNode != null) {
                WeightedPath path = finder.findSinglePath(startNode, endNode);
                double weight = path.weight();
                LOG.info("weight="+weight);
                NetworkPeer np;
                for(Node n : path.nodes()) {
                    np = toPeer(n);
                    lowestLatencyPath.add(np);
                }
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return lowestLatencyPath;
    }

    /**
     * Find lowest latency path between two nodes outgoing taking into consideration:
     *      + sensors/networks supported by each node (Relationship type)
     *      + average latency (of the last n acks) of that network relationship
     *      + median latency (of the last n acks) of that network relationship
     *      + number of total acks of that network relationship (reliability)
     *      + number of nacks of that network relationship
     *
     *
     * @param fromPeerId
     * @param toPeerId
     * @param networks
     * @return
     */
    public List<NetworkPeer> findLowestLatencyPathFiltered(String fromPeerId, String toPeerId, String[] networks) {
        CostEvaluator<Double> costEvaluator = new CostEvaluator<Double>() {
            @Override
            public Double getCost(Relationship relationship, Direction direction) {
                Long avgAckLatencyMS = (Long) relationship.getProperty(P2PRelationship.AVG_ACK_LATENCY_MS);
                Long medAckLatencyMS = (Long) relationship.getProperty(P2PRelationship.MEDIAN_ACK_LATENCY_MS);
                Long totalAcks = (Long) relationship.getProperty(P2PRelationship.TOTAL_ACKS);
                Long lastAckTime = (Long) relationship.getProperty(P2PRelationship.LAST_ACK_TIME);
//                Long costE = (Long) relationship.getEndNode().getProperty("cost");
                // Weigh median heavier than average yet ensure average is included to take into account extremes which are still important
                Double score = (medAckLatencyMS * .75) + (avgAckLatencyMS * .25);
                relationship.setProperty("score", score);
                return score;
            }
        };
        List<NetworkPeer> lowestLatencyPath = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            PathFinder<WeightedPath> finder = GraphAlgoFactory.dijkstra(PathExpanders.forTypeAndDirection(P2PRelationship.RelType.IMS, Direction.OUTGOING), P2PRelationship.AVG_ACK_LATENCY_MS);
//            PathFinder<WeightedPath> finder = GraphAlgoFactory.dijkstra(PathExpanderBuilder.allTypes(Direction.OUTGOING).)
            Node startNode = findPeerNode(fromPeerId);
            Node endNode = findPeerNode(toPeerId);
            if (startNode != null && endNode != null) {
                WeightedPath path = finder.findSinglePath(startNode, endNode);
                double weight = path.weight();
                LOG.info("Path weight: " + path.weight());
                NetworkPeer np;
                for(Node n : path.nodes()) {
                    np = toPeer(n);
                    lowestLatencyPath.add(np);
//                    LOG.info("Node: " + n.getId() + "-" +  n.getProperty("cost"));
                }
                for (Relationship r: path.relationships()) {
                    LOG.info("Relationship: " + r.getId() + "-" + r.getProperty("score") );
                }
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return lowestLatencyPath;
    }

    public boolean savePeer(NetworkPeer networkPeer, Boolean autoCreate) {
        boolean saved = false;
        synchronized (peerSaveLock) {
            try (Transaction tx = graphDb.beginTx()) {
                Node n = findPeerNode(networkPeer.getId());
                if (n == null && autoCreate) {
                    n = graphDb.createNode(peerLabel);
                    n.setProperty("id", networkPeer.getId());
                }
                tx.success();
                saved = true;
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }
        }
        return saved;
    }

    /**
     * Requires to be used within a Transaction
     * @param id Id of NetworkPeer
     * @return
     * @throws Exception
     */
    private Node findPeerNode(String id) {
        return graphDb.findNode(peerLabel, "id", id);
    }

    public boolean isRelatedByNetwork(String startPeerId, String network, String endPeerId) {
        boolean hasRel = false;
        P2PRelationship.RelType relType = P2PRelationship.networkToRelationship(network);
        String cql = "MATCH (n {id: '" + startPeerId + "'})-[r:" + relType.name() + "]->(e {id: '" + endPeerId + "'})" +
                " RETURN r;";
        try (Transaction tx = graphDb.beginTx()) {
            Result result = graphDb.execute(cql);
            if (result.hasNext()) {
                hasRel = true;
                LOG.info(endPeerId + " is "+relType.name()+" peer to " + startPeerId);
            }
            tx.success();
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return hasRel;
    }

    public P2PRelationship relateByNetwork(String idLeftPeer, String network, String idRightPeer) {
        if(idLeftPeer==null || idRightPeer==null) {
            LOG.warning("Must provide ids for both peers when relating them.");
            return null;
        }
        if(idLeftPeer.equals(idRightPeer)) {
            LOG.info("Both peers are the same, skipping.");
            return null;
        }
        P2PRelationship.RelType relType = P2PRelationship.networkToRelationship(network);
        P2PRelationship rt = null;
        try (Transaction tx = graphDb.beginTx()) {
            Node lpn = findPeerNode(idLeftPeer);
            Node rpn = findPeerNode(idRightPeer);
            Iterator<Relationship> i = lpn.getRelationships(relType, Direction.OUTGOING).iterator();
            while(i.hasNext()) {
                Relationship r = i.next();
                if(r.getNodes()[1].equals(rpn)) {
                    // load
                    rt = initP2PRel(r);
                    LOG.info("Found P2P Relationship; no need to create.");
                    break;
                }
            }
            if(rt==null) {
                // create
                Relationship r = lpn.createRelationshipTo(rpn, relType);
                rt = initP2PRel(r);
                LOG.info(idRightPeer+" is now a "+ relType.name()+" peer of "+idLeftPeer);
            }
            tx.success();
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return rt;
    }

    public P2PRelationship getNetworkRelationship(String idLeftPeer, String network, String idRightPeer) {
        P2PRelationship.RelType relType = P2PRelationship.networkToRelationship(network);
        P2PRelationship rt = null;
        try (Transaction tx = graphDb.beginTx()) {
            Node lpn = findPeerNode(idLeftPeer);
            Node rpn = findPeerNode(idRightPeer);
            Iterator<Relationship> i = lpn.getRelationships(relType, Direction.OUTGOING).iterator();
            while(i.hasNext()) {
                Relationship r = i.next();
                if(r.getNodes()[1].equals(rpn)) {
                    // load
                    rt = initP2PRel(r);
                    LOG.info("Found P2P Relationship");
                    break;
                }
            }
            tx.success();
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return rt;
    }

    public long numberPeersByNetwork(String id, String network) {
        P2PRelationship.RelType relType = P2PRelationship.networkToRelationship(network);
        long count = -1;
        try (Transaction tx = graphDb.beginTx()) {
            String cql = "MATCH (n {id: '"+id+"'})-[:" + relType.name() + "]->()" +
                    " RETURN count(*) as total";
            Result r = graphDb.execute(cql);
            if (r.hasNext()) {
                Map<String, Object> row = r.next();
                for (Map.Entry<String, Object> column : row.entrySet()) {
                    count = (long)column.getValue();
                    break;
                }
            }
            tx.success();
            LOG.info(count+" "+ relType.name());
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        return count;
    }

    /**
     * Remove relationship
     */
    public boolean removeNetworkRelationship(String startPeerId, String network, String endPeerId) {
        P2PRelationship.RelType relType = P2PRelationship.networkToRelationship(network);
        try (Transaction tx = graphDb.beginTx()) {
            String cql = "MATCH (n {id: '"+startPeerId+"'})-[r:" + relType.name() + "]->( e {id: '"+endPeerId+"'})" +
                    " DELETE r;";
            graphDb.execute(cql);
            tx.success();
            LOG.info(relType.name() + " relationship of "+endPeerId+" removed from "+startPeerId);
        } catch(Exception e) {
            LOG.warning(e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    /**
     * Saves Peer Request status results.
     * Determine if results change Reliable Peers list.
     * Reliable Peers are defined as peers known by given peer who have displayed
     * a minimum number of acks (SensorsConfig.mr) and minimum avg response time (<= SensorsConfig.lmc)
     *
     * @param startPeerId originator
     * @param network Network
     * @param endPeerId destination
     * @param timeSent time sent in milliseconds since epoch
     * @param timeAcknowledged time acknowledged in milliseconds since epoch
     */
    public Boolean savePeerStatusTimes(String startPeerId, String network, String endPeerId, Long timeSent, Long timeAcknowledged) {
        boolean addedAsReliable = false;
        P2PRelationship.RelType relType = P2PRelationship.networkToRelationship(network);
        P2PRelationship networkRel = getNetworkRelationship(startPeerId, network, endPeerId);
        if(networkRel!=null) {
            // Update stats
            networkRel.setLastAckTime(timeAcknowledged);
            networkRel.addAckTimeTracked(timeAcknowledged - timeSent, MaxAcksTracked);
            String cql = "MATCH (n {id: '" + startPeerId + "'})-[r:" + relType + "]->(e {id: '" + endPeerId + "'})" +
                    " SET r.totalAcks = " + networkRel.getTotalAcks() + "," +
                    " r.lastAckTime = " + networkRel.getLastAckTime() + "," +
                    " r.avgAckLatencyMS = " + networkRel.getAvgAckLatencyMS() + "," +
                    " r.medAckLatencyMS = " + networkRel.getMedAckLatencyMS() + "," +
                    " r.ackTimesTracked = '" + networkRel.getAckTimesTracked() + "';";
            try (Transaction tx = graphDb.beginTx()) {
                graphDb.execute(cql);
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }

            LOG.info("Peer status times: {" +
                    "\n\tack received by local peer in: "+(timeAcknowledged-timeSent)+"ms"+
                    "\n\tlast ack: "+networkRel.getLastAckTime()+
                    "\n\ttotal acks: "+networkRel.getTotalAcks()+
                    "\n\tmed round trip latency: "+networkRel.getMedAckLatencyMS()+
                    "\n\tavg round trip latency: "+networkRel.getAvgAckLatencyMS()+"ms\n} of remote peer "+endPeerId+" with start peer "+startPeerId);

        } else if(numberPeersByNetwork(startPeerId, network) <= MaxPeersTracked) {
            relateByNetwork(startPeerId, network, endPeerId);
            LOG.info("New relationship ("+ network+") with peer: "+endPeerId);
        } else {
            LOG.info("Max peers tracked: "+ MaxPeersTracked);
        }
        return addedAsReliable;
    }

    private P2PRelationship initP2PRel(Relationship r) {
        P2PRelationship p2PR = new P2PRelationship();
        p2PR.fromMap(toMap(r));
        return p2PR;
    }

    private NetworkPeer toPeer(PropertyContainer n) {
        NetworkPeer networkPeer = new NetworkPeer((String)n.getProperty("network"));
        networkPeer.setId((String)n.getProperty("id"));
        return networkPeer;
    }

    private Map<String, Object> toMap(PropertyContainer n) {
        return GraphUtil.getAttributes(n);
    }

    public boolean init(Properties properties) {
        if(location==null) {
            LOG.warning("Neo4J DB location required. Please provide.");
            return false;
        }
        if(name==null) {
            LOG.warning("Neo4J DB name required. Please provide.");
            return false;
        }
        if(!initialized) {
            this.properties = properties;
            File dbDir = new File(location, name);
            if(!dbDir.exists() && !dbDir.mkdir()) {
                LOG.warning("Unable to create graph db directory at: "+dbDir.getAbsolutePath());
                return false;
            }

//            graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(dbDir);
            graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dbDir)
                    .setConfig(GraphDatabaseSettings.allow_upgrade,"true")
                    .newGraphDatabase();

            if ("true".equals(properties.getProperty("1m5.peers.db.cleanOnRestart"))) {
                FileUtil.rmdir(location+"/"+ name, false);
                LOG.info("Cleaned " + name);
            }

            // Initialize indexes
            LOG.info("Verifying Content Indexes are present...");
            try (Transaction tx = graphDb.beginTx()) {
                Iterable<IndexDefinition> definitions = graphDb.schema().getIndexes(PEER_LABEL);
                if(definitions==null || ((List)definitions).size() == 0) {
                    LOG.info("Peer Graph Id Index not found; creating...");
                    // No Indexes...set them up
                    graphDb.schema().indexFor(PEER_LABEL).withName("Peer.id").on("id").create();
                    LOG.info("Peer Graph Id Index created.");
                }
                tx.success();
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            }

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    teardown();
                }
            } );
            initialized = true;
        }
        return true;
    }

    public boolean teardown() {
        LOG.info("Tearing down...");
        graphDb.shutdown();
        LOG.info("Torn down.");
        return true;
    }
}

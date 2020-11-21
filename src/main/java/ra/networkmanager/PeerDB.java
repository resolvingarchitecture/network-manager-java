package ra.networkmanager;

import ra.common.identity.DID;
import ra.common.network.NetworkNode;
import ra.common.network.NetworkPeer;
import ra.util.JSONParser;
import ra.util.RandomUtil;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import static java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE;

class PeerDB {

    private static final Logger LOG = Logger.getLogger(PeerDB.class.getName());

    private boolean initialized = false;
    private String location;
    private String name;
    private Properties properties;
    private Connection connection;
    private String dbURL;

    // Considering this app is highly multi-threaded
    // and it's using shared prepared statements,
    // make thread-safe by providing a lock Object
    // to synchronize on so that prepared statements
    // do not get used simultaneously causing data
    // corruption.
    private PreparedStatement peersCountByNetwork;
    private final Object peersCountByNetworkLock = new Object();
    private PreparedStatement peersById;
    private final Object peersByIdLock = new Object();
    private PreparedStatement peersByNetwork;
    private final Object peersByNetworkLock = new Object();
    private PreparedStatement peerByIdAndNetwork;
    private final Object peerByIdAndNetworkLock = new Object();
    private PreparedStatement peerByAddress;
    private final Object peerByAddressLock = new Object();
    private PreparedStatement peerByFingerprint;
    private final Object peerByFingerprintLock = new Object();
    private final Object peerInsertPSLock = new Object();
    private final Object peerUpdatePSLock = new Object();

    public Boolean savePeer(NetworkPeer p, Boolean autocreate) {
        LOG.info("Saving NetworkPeer...");
        if(p.getId()==null || p.getId().isEmpty()) {
            LOG.warning("NetworkPeer.id is empty. Must have an id for Network Peers to save.");
            return false;
        }
        if(p.getNetwork()==null) {
            LOG.warning("NetworkPeer.network is empty. Must have a Network for Network Peers to save.");
            return false;
        }
        boolean update = false;
        ResultSet rs = null;
        LOG.info("Looking up Node by Id: "+p.getId()+" and Network: "+p.getNetwork());
        synchronized (peerByIdAndNetwork) {
            try {
                peerByIdAndNetwork.clearParameters();
                peerByIdAndNetwork.setString(1, p.getId());
                peerByIdAndNetwork.setString(2, p.getNetwork());
                rs = peerByIdAndNetwork.executeQuery();
                update = rs.next();
            } catch (SQLException e) {
                LOG.warning(e.getLocalizedMessage());
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        LOG.warning(e.getLocalizedMessage());
                    }
                }
            }
        }

        if(update) {
            StringBuilder sb = new StringBuilder("update peer set ");
            String sql;
            LOG.info("Find and Update Peer Node...");
            sb.append("id='"+p.getId()+"'");
            sb.append(", network='"+p.getNetwork()+"'");
            if(p.getDid().getUsername()!=null)
                sb.append(", username='"+p.getDid().getUsername()+"'");
            if(p.getDid().getPublicKey().getAlias()!=null)
                sb.append(", alias='"+p.getDid().getPublicKey().getAlias()+"'");
            if(p.getDid().getPublicKey().getAddress()!=null)
                sb.append(", address='"+p.getDid().getPublicKey().getAddress()+"'");
            if(p.getDid().getPublicKey().getFingerprint()!=null)
                sb.append(", fingerprint='"+p.getDid().getPublicKey().getFingerprint()+"'");
            if(p.getDid().getPublicKey().getType()!=null)
                sb.append(", keyType='"+p.getDid().getPublicKey().getType()+"'");
            if(p.getPort()!=null)
                sb.append(", port="+p.getPort());
            if(p.getDid().getPublicKey().getAttributes()!=null && p.getDid().getPublicKey().getAttributes().size()>0)
                sb.append(", attributes='"+ JSONParser.toString(p.getDid().getPublicKey().getAttributes())+"'");
            sb.append(" where id='"+p.getId()+"' and network='"+p.getNetwork()+"'");
            sql = sb.toString();
            LOG.info("Updating Peer with sql: "+sql);
            synchronized (peerUpdatePSLock) {
                try {
                    Statement stmt = connection.createStatement();
                    stmt.execute(sql);
                } catch (SQLException e) {
                    LOG.warning(e.getLocalizedMessage());
                    return false;
                }
            }
        } else if(autocreate) {
            LOG.info("Creating NetworkPeer in DB...");
            if(p.getNetwork()==null) {
                LOG.warning("Can not insert NetworkPeer into database without a network.");
                return false;
            }
            StringBuilder sb = new StringBuilder("insert into peer (id, network");
            if(p.getDid().getUsername()!=null)
                sb.append(", username");
            if(p.getDid().getPublicKey().getAlias()!=null)
                sb.append(", alias");
            if(p.getDid().getPublicKey().getAddress()!=null)
                sb.append(", address");
            if(p.getDid().getPublicKey().getFingerprint()!=null)
                sb.append(", fingerprint");
            if(p.getDid().getPublicKey().getType()!=null)
                sb.append(", keyType");
            if(p.getPort()!=null)
                sb.append(", port");
            if(p.getDid().getPublicKey().getAttributes()!=null && p.getDid().getPublicKey().getAttributes().size()>0)
                sb.append(", attributes");
            sb.append(") values ('"+p.getId()+"', '"+p.getNetwork()+"'");
            if(p.getDid().getUsername()!=null)
                sb.append(", '"+p.getDid().getUsername()+"'");
            if(p.getDid().getPublicKey().getAlias()!=null)
                sb.append(", '"+p.getDid().getPublicKey().getAlias()+"'");
            if(p.getDid().getPublicKey().getAddress()!=null)
                sb.append(", '"+p.getDid().getPublicKey().getAddress()+"'");
            if(p.getDid().getPublicKey().getFingerprint()!=null)
                sb.append(", '"+p.getDid().getPublicKey().getFingerprint()+"'");
            if(p.getDid().getPublicKey().getType()!=null)
                sb.append(", '"+p.getDid().getPublicKey().getType()+"'");
            if(p.getPort()!=null)
                sb.append(", "+p.getPort());
            if(p.getDid().getPublicKey().getAttributes()!=null && p.getDid().getPublicKey().getAttributes().size()>0)
                sb.append(", '"+JSONParser.toString(p.getDid().getPublicKey().getAttributes())+"'");
            sb.append(")");
            String sql = sb.toString();
            LOG.info("Inserting Peer with sql: "+sql);
            synchronized (peerInsertPSLock) {
                try {
                    Statement stmt = connection.createStatement();
                    stmt.execute(sql);
                } catch (SQLException e) {
                    LOG.warning(e.getLocalizedMessage());
                    return false;
                }
            }
        } else {
            LOG.warning("New Peer but autocreate is false, unable to save peer.");
        }
        LOG.info("NetworkPeer (id="+p.getId()+") saved.");
        return true;
    }

    public int numberPeersByNetwork(String network) {
        ResultSet rs = null;
        int total = 0;
        synchronized (peersCountByNetworkLock) {
            try {
                peersCountByNetwork.clearParameters();
                peersCountByNetwork.setString(1, network);
                rs = peersCountByNetwork.executeQuery();
                if (rs.next()) {
                    total = rs.getInt("total");
                }
            } catch (SQLException e) {
                LOG.warning(e.getLocalizedMessage());
            } finally {
                if (rs != null)
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        LOG.warning(e.getLocalizedMessage());
                    }
            }
        }
        return total;
    }

    public NetworkPeer randomPeer(NetworkPeer fromPeer) {
        if(fromPeer==null) {
            LOG.warning("NetworkPeer null.");
            return null;
        }
        NetworkPeer p = null;
        ResultSet rs = null;
        int total = numberPeersByNetwork(fromPeer.getNetwork());
        if(total==0) {
            LOG.warning("No peers found for network: "+fromPeer.getNetwork());
            return null;
        }
        boolean samePeer = true;
        int max = 4; // Fail-safe
        int currentCount = 0;
        while(samePeer) {
            int random = RandomUtil.nextRandomInteger(0, total);
            rs = null;
            synchronized (peersByNetworkLock) {
                try {
                    peersByNetwork.clearParameters();
                    peersByNetwork.setString(1, fromPeer.getNetwork());
                    rs = peersByNetwork.executeQuery();
                    if (rs.first()) {
                        rs.absolute(random);
                        p = toPeer(rs);
                    }
                } catch (SQLException e) {
                    LOG.warning(e.getLocalizedMessage());
                } finally {
                    if (rs != null)
                        try {
                            rs.close();
                        } catch (SQLException e) {
                            LOG.warning(e.getLocalizedMessage());
                        }
                }
            }
            if(p==null || !fromPeer.getId().equals(p.getId()) || ++currentCount > max) {
                samePeer = false;
            }
        }
        return p;
    }

    public NetworkNode loadNode(String id) {
        NetworkPeer p = null;
        NetworkNode n = null;
        ResultSet rs = null;
        synchronized (peersByIdLock) {
            try {
                peersById.clearParameters();
                peersById.setString(1, id);
                rs = peersById.executeQuery();
                while (rs.next()) {
                    if (n == null)
                        n = new NetworkNode();
                    p = toPeer(rs);
                }
            } catch (SQLException e) {
                LOG.warning(e.getLocalizedMessage());
            } finally {
                if (rs != null)
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        LOG.warning(e.getLocalizedMessage());
                    }
            }
        }
        LOG.info("Node loaded:\n\t"+n);
        return n;
    }

    public NetworkPeer loadPeerByIdAndNetwork(String id, String network) {
        NetworkPeer p = null;
        ResultSet rs = null;
        synchronized (peerByIdAndNetworkLock) {
            try {
                peerByIdAndNetwork.clearParameters();
                peerByIdAndNetwork.setString(1, id);
                peerByIdAndNetwork.setString(2, network);
                rs = peerByIdAndNetwork.executeQuery();
                if (rs.next()) {
                    p = new NetworkPeer(rs.getString(NetworkPeer.NETWORK));
                    p.setId(rs.getString(NetworkPeer.ID));
                    p.getDid().setUsername(rs.getString(NetworkPeer.USERNAME));
                    p.getDid().getPublicKey().setAlias(rs.getString(NetworkPeer.ALIAS));
                    p.getDid().getPublicKey().setAddress(rs.getString(NetworkPeer.ADDRESS));
                    p.getDid().getPublicKey().setFingerprint(rs.getString(NetworkPeer.FINGERPRINT));
                    p.getDid().getPublicKey().setType(rs.getString(NetworkPeer.KEY_TYPE));
                }
            } catch (SQLException e) {
                LOG.warning(e.getLocalizedMessage());
            } finally {
                if (rs != null)
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        LOG.warning(e.getLocalizedMessage());
                    }
            }
        }
        return p;
    }

    public NetworkPeer loadPeerByAddress(String address) {
        NetworkPeer p = null;
        ResultSet rs = null;
        synchronized (peerByAddressLock) {
            try {
                peerByAddress.clearParameters();
                peerByAddress.setString(1, address);
                rs = peerByAddress.executeQuery();
                if (rs.next()) {
                    p = new NetworkPeer(rs.getString(NetworkPeer.NETWORK));
                    p.setId(rs.getString(NetworkPeer.ID));
                    p.getDid().setUsername(rs.getString(NetworkPeer.USERNAME));
                    p.getDid().getPublicKey().setAlias(rs.getString(NetworkPeer.ALIAS));
                    p.getDid().getPublicKey().setAddress(rs.getString(NetworkPeer.ADDRESS));
                    p.getDid().getPublicKey().setFingerprint(rs.getString(NetworkPeer.FINGERPRINT));
                    p.getDid().getPublicKey().setType(rs.getString(NetworkPeer.KEY_TYPE));
                    p.setPort(rs.getInt(NetworkPeer.PORT));
                }
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        LOG.warning(e.getLocalizedMessage());
                    }
                }
            }
        }
        return p;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean init(Properties p) {
        if(location==null) {
            LOG.warning("Derby DB location required. Please provide.");
            return false;
        }
        if(name==null) {
            LOG.warning("Derby DB name required. Please provide.");
            return false;
        }
        if(!initialized) {
            this.properties = p;
            System.setProperty("derby.system.home",location);
            dbURL = "jdbc:derby:"+name;
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getConstructor().newInstance();
                connection = DriverManager.getConnection(dbURL+";create=true", properties);
//                connection.setAutoCommit(false);
            } catch (Exception e) {
                LOG.warning(e.getLocalizedMessage());
                return false;
            }

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    teardown();
                }
            } );

            Statement stmt = null;
            try {
                stmt = connection.createStatement();
                stmt.execute("create table peer (id varchar(256) not null, network varchar(16) not null, username varchar(32), alias varchar(32), address varchar(4096), fingerprint varchar(256), keyType varchar(32), port int, attributes varchar(4096)) ");
            } catch (SQLException e) {
                LOG.info(e.getLocalizedMessage());
            } finally {
                if(stmt!=null) {
                    try {
                        stmt.close();
                    } catch (SQLException e) {
                        LOG.warning(e.getLocalizedMessage());
                    }
                }
            }
            try {
                peersCountByNetwork = connection.prepareStatement("select count(id) as total from Peer where network=?");
                peersById = connection.prepareStatement("select * from Peer where id=?");
                peersByNetwork = connection.prepareStatement("select * from Peer where network=?", TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                peerByIdAndNetwork = connection.prepareStatement("select * from Peer where id=? and network=?");
                peerByFingerprint = connection.prepareStatement("select * from Peer where fingerprint=?");
                peerByAddress = connection.prepareStatement("select * from Peer where address=?");
                initialized = true;
            } catch (SQLException e) {
                LOG.warning(e.getLocalizedMessage());
            }
        }
        return initialized;
    }

    public boolean teardown() {
        LOG.info("Stopping...");
        if(connection!=null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.warning(e.getLocalizedMessage());
            }
        }
        try {
            DriverManager.getConnection(dbURL+";shutdown=true");
        } catch (SQLException e) {
            LOG.warning(e.getLocalizedMessage());
        }
        LOG.info("Stopped.");
        return true;
    }

    private NetworkPeer toPeer(ResultSet rs) throws SQLException {
        DID did = new DID();
        NetworkPeer p = new NetworkPeer(rs.getString("network"));
        p.setDid(did);
        p.setId(rs.getString("id"));
        did.setUsername(rs.getString("username"));
        did.getPublicKey().setAlias(rs.getString("alias"));
        did.getPublicKey().setAddress(rs.getString("address"));
        did.getPublicKey().setFingerprint(rs.getString("fingerprint"));
        did.getPublicKey().setType(rs.getString("keyType"));
        p.setPort(rs.getInt("port"));
        did.getPublicKey().getAttributes().putAll((Map<String,Object>)JSONParser.parse(rs.getString("attributes")));
        return p;
    }
}

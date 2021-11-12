package ra.networkmanager;

import java.util.Properties;

public class Stats {

    private Properties p;

    void init(Properties p){
        this.p = p;
    }

    public int reliableTotalAcks() {
        return Integer.parseInt(p.getProperty("ra.networkmanager.stats.reliable.totalAcks"));
    }

}

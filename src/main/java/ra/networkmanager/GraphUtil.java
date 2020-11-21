package ra.networkmanager;

import org.neo4j.graphdb.PropertyContainer;
import ra.util.JSONParser;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

class GraphUtil {

    private static Logger LOG = Logger.getLogger(GraphUtil.class.getName());

    public static void updateProperties(PropertyContainer c, Map<String, Object> a) {
        Set<String> keys = a.keySet();
        for(String key : keys) {
            Object o = a.get(key);
            if(o instanceof String || o instanceof Number)
                c.setProperty(key,o);
            else if(o instanceof Map || o instanceof Collection) {
                c.setProperty(key, JSONParser.toString(o));
            } else {
                c.setProperty(key,o.toString());
            }
        }
    }

    public static Map<String, Object> getAttributes(PropertyContainer c) {
        Map<String, Object> a = new HashMap<>();
        for (String key : c.getPropertyKeys()) {
            Object o = c.getProperty(key);
            if(o instanceof String) {
                String str = (String)o;
                if(str.startsWith("{")) {
                    o = JSONParser.parse(str);
                    a.put(key, o);
                } else {
                    a.put(key, str);
                }
            } else {
                a.put(key, o);
            }
        }
        return a;
    }

}

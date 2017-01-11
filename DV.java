import java.lang.Math;
import java.util.HashMap;
import java.util.Formatter;
import java.util.Vector;

public class DV implements RoutingAlgorithm {
    
    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    private HashMap<Integer, DVRoutingTableEntry> routingTable;

    private boolean shpr;
    private boolean expr;

    private int uInterval;

    private Router router;

    public DV()
    {
      routingTable = new HashMap<Integer, DVRoutingTableEntry>();
    }
    
    public void setRouterObject(Router obj)
    {
      router = obj;
    }
    
    public void setUpdateInterval(int u)
    {
      uInterval = u;
    }
    
    public void setAllowPReverse(boolean flag)
    {
      shpr = flag;
    }
    
    public void setAllowExpire(boolean flag)
    {
      expr = flag;
    }
    
    public void initalise()
    {
      int d = router.getId();
      DVRoutingTableEntry entry = new DVRoutingTableEntry(d, LOCAL, 0, INFINITY);
      routingTable.put(d, entry);
    }
    
    public int getNextHop(int destination)
    {
      DVRoutingTableEntry entry = routingTable.get(destination);
      if(entry != null && entry.getMetric() < INFINITY){
        return entry.getInterface();
      } else {
        return UNKNOWN;
      }
    }
    
    public void tidyTable()
    {
      Vector<Integer> toBeDeleted = new Vector<Integer>();
      int time = router.getCurrentTime();
      DVRoutingTableEntry r = routingTable.get(router.getId());
      r.setTime(time);
      for(DVRoutingTableEntry entry:routingTable.values()){
        int i = entry.getInterface();
        if(!router.getInterfaceState(i)){
          entry.setMetric(INFINITY);
        }
        if(time >= entry.getTime() + uInterval && expr){
          entry.setMetric(INFINITY);
        }
        if(entry.getMetric() == INFINITY){
          if(entry.getgcTime() == -1){
            entry.setgcTime(time + uInterval * 4);
          } else if (entry.getgcTime() <= time){
            toBeDeleted.add(entry.getDestination());
          }
        }


      }
      if(expr){
        for(Integer i:toBeDeleted){
          routingTable.remove(i);
        }
      }
    }
    
    public Packet generateRoutingPacket(int iface)
    {
      if(router.getInterfaceState(iface)){
        Payload p = new Payload();
        for(DVRoutingTableEntry e:routingTable.values()){
          if(e.getInterface() == iface && shpr){
            DVRoutingTableEntry newe = new DVRoutingTableEntry(e.getDestination(),
                                                               e.getInterface(),
                                                               INFINITY,
                                                               e.getTime());
            p.addEntry(newe);
          } else {
            DVRoutingTableEntry newe = new DVRoutingTableEntry(e.getDestination(),
                                                               e.getInterface(),
                                                               e.getMetric(),
                                                               e.getTime());
            p.addEntry(newe);
          }
        }

        Packet pack = new RoutingPacket(router.getId(), Packet.BROADCAST);
        pack.setPayload(p);
        return pack; 
      } else {
        return null;
      }
    }

    private void createEntry(DVRoutingTableEntry entry, int iface){
      int d = entry.getDestination();
      int m = entry.getMetric();
      int time = router.getCurrentTime();
      m += router.getInterfaceWeight(iface);
      if(m > INFINITY){
        m = INFINITY;
      }
      DVRoutingTableEntry r = routingTable.get(d);
      if(r == null){
        if(m < INFINITY){
          DVRoutingTableEntry newr = new DVRoutingTableEntry(d, iface, m, time);
          routingTable.put(d, newr);
        }
      } else if (iface == r.getInterface()){
        r.setMetric(m);
        r.setTime(time);
        if(m != INFINITY){
          r.setgcTime(-1);
        }
      } else if (m < r.getMetric()) {
        r.setMetric(m);
        r.setInterface(iface);
        r.setTime(time);
      }
    }
    
    public void processRoutingPacket(Packet p, int iface)
    {
      Vector<Object> d = p.getPayload().getData();
      for(Object entry: d){
        createEntry((DVRoutingTableEntry)entry, iface);
      } 
    }
    
    public void showRoutes()
    {
       System.out.format("Router %d%n", router.getId());
       for(DVRoutingTableEntry entry : routingTable.values()){
         System.out.println(entry);
       }
    }
}

class DVRoutingTableEntry implements RoutingTableEntry
{
  private int destination;
  private int iface;
  private int metric;
  private int time;
  private int gctime;
    
    public DVRoutingTableEntry(int d, int i, int m, int t)
	{
    destination = d;
    iface = i;
    metric = m;
    time = t;
    gctime = -1;
	}
    public int getDestination() { return destination; } 
    public void setDestination(int d) { destination = d;}
    public int getInterface() { return iface; }
    public void setInterface(int i) { iface = i;}
    public int getMetric() { return metric;}
    public void setMetric(int m) { metric = m;} 
    public int getTime() {return time;}
    public void setTime(int t) { time = t;}
    public int getgcTime() {return gctime;}
    public void setgcTime(int t) {gctime = t;}
    
    public String toString() 
	{
    Formatter formatter = new Formatter();
    formatter.format("d %d i %d m %d", destination, iface, metric);
    return formatter.toString();
	}
}


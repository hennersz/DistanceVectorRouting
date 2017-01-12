import java.lang.Math;
import java.util.HashMap;
import java.util.Formatter;
import java.util.Vector;

public class DV implements RoutingAlgorithm {
    
    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    private HashMap<Integer, DVRoutingTableEntry> routingTable;//Maps destination address to routing table entry.

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
      /**
       * Adds local destination to routing table.
       */
      int d = router.getId();
      DVRoutingTableEntry entry = new DVRoutingTableEntry(d, LOCAL, 0, INFINITY);
      routingTable.put(d, entry);
    }
    
    public int getNextHop(int destination)
    {
      /**
       * Finds the entry in the routing table and returns the interface if the weight is not infinity otherwise return unknown
       */
      DVRoutingTableEntry entry = routingTable.get(destination);
      if(entry != null && entry.getMetric() < INFINITY){
        return entry.getInterface();
      } else {
        return UNKNOWN;
      }
    }
    
    public void tidyTable()
    {
      /**
       * Refreshes the local routing table entry and checks to see if entries should be marked as invalid or deleted
       */
      Vector<Integer> toBeDeleted = new Vector<Integer>();//Contains keys(destination) for entries to delete

      int time = router.getCurrentTime();

      DVRoutingTableEntry r = routingTable.get(router.getId());
      r.setTime(time);//update local entry

      for(DVRoutingTableEntry entry:routingTable.values()){
        int i = entry.getInterface();
        if(!router.getInterfaceState(i)){//link is down so set entry metric to infinity
          entry.setMetric(INFINITY);
        }
        if((time >= entry.getTime() + (uInterval * 1)) && expr){//entry has expired so flag for deletion
          entry.setMetric(INFINITY);
        }
        //if entry is infinity check if it has been flagged for garbage collection
        if(entry.getMetric() == INFINITY){
          if(entry.getgcTime() == -1){//hasen't been flagged yet
            entry.setgcTime(time + (uInterval * 4));//set time at which it should be deleted
          } else if (entry.getgcTime() <= time){//passed the deletion time so delete
            toBeDeleted.add(entry.getDestination());
          }
        }
      }
      //delete each entry flagged for deletion, can't do in previous loop as causes problems for the iterator
      if(expr){
        for(Integer i:toBeDeleted){
          routingTable.remove(i);
        }
      }
    }
    
    public Packet generateRoutingPacket(int iface)
    {
      /**
       * Creates packet from the routing table
       */
      if(router.getInterfaceState(iface)){//Is interface up?
        Payload p = new Payload();
        for(DVRoutingTableEntry e:routingTable.values()){
          if(e.getInterface() == iface && shpr){//Check if the interface in the routing table is the same as the interface we will send the packet on and split horizon is enabled
            DVRoutingTableEntry newe = new DVRoutingTableEntry(e.getDestination(),//create an entry with metric infinity
                                                               e.getInterface(),
                                                               INFINITY,
                                                               e.getTime());
            p.addEntry(newe);
          } else {//create new object so the other router is not using the same values as in this routing table
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
      /**
       * Creates a new entry from an entry received in a packet
       */
      int d = entry.getDestination();
      int m = entry.getMetric();
      int time = router.getCurrentTime();
      m += router.getInterfaceWeight(iface);
      if(m > INFINITY){
        m = INFINITY;
      }
      DVRoutingTableEntry r = routingTable.get(d);
      if(r == null){//no entry for this in the table yet
        if(m < INFINITY){//dont add infinity entries to the table
          DVRoutingTableEntry newr = new DVRoutingTableEntry(d, iface, m, time);
          routingTable.put(d, newr);
        }
      } else if (iface == r.getInterface()){//always overwrite entries if the information comes from the same interface
        r.setMetric(m);
        r.setTime(time);
        if(m != INFINITY){//clear garbage collection timer if metric is no longer infinity
          r.setgcTime(-1);
        }
      } else if (m < r.getMetric()) {//update if there is a shorter alternate route
        r.setMetric(m);
        r.setInterface(iface);
        r.setTime(time);
        r.setgcTime(-1);//must be less than infinity so reset gc timer
      }
    }
    
    public void processRoutingPacket(Packet p, int iface)
    {
      /**
       * Iterate through the payload and process each entry
       */
      Vector<Object> d = p.getPayload().getData();
      for(Object entry: d){
        createEntry((DVRoutingTableEntry)entry, iface);
      } 
    }
    
    public void showRoutes()
    {
      /**
       * prints router id then all entries in the routing table
       */
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
  private int time;//time entry was last updated
  private int gctime;//time at which entry should be deleted. -1 means unset.
    
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


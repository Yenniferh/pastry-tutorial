package pkgtry.pastry;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Vector;

import rice.environment.Environment;
import rice.environment.params.simple.SimpleParameters;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.direct.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
/**
 *
 * @author Yennifer Herrera
 */
public class TryPastry {

    Vector<App> apps = new Vector<App>();
    /**
     * @param args the command line arguments
     */
    
    public TryPastry(int bindport, InetSocketAddress bootaddress, int numNodes,
            Environment env, boolean useDirect) throws Exception {
        
        NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
        
        PastryNodeFactory factory;
        if(useDirect) {
            NetworkSimulator<DirectNodeHandle,RawMessage> sim;
            sim = new EuclideanNetwork<DirectNodeHandle,RawMessage>(env);
            factory = new DirectPastryNodeFactory(nidFactory, sim, env);
        }else {
            factory = new SocketPastryNodeFactory(nidFactory, bindport, env);
        }
        
        IdFactory idFactory = new PastryIdFactory(env);
        
        Object bootHandle = null;
        for (int curNode = 0; curNode < numNodes; curNode++) {
            PastryNode node = factory.newNode();
            App app = new App(node, idFactory);
            apps.add(app);
            node.boot(bootHandle);
            
            if(bootHandle == null) {
                if(useDirect) {
                    bootHandle = node.getLocalHandle();
                }else {
                    bootHandle = bootaddress;
                }
            }
            
            synchronized(node) {
                while(!node.isReady() && !node.joinFailed()) {
                    node.wait(500);

                    if (node.joinFailed()) {
                      throw new IOException("Could not join the FreePastry ring.  Reason:"+node.joinFailedReason()); 
                    }
                }       
            }
            
            System.out.println("Finished creating new node "+node);
        }
        
        // wait 1 second
        env.getTimeSource().sleep(1000);

        // pick a node
        App app = apps.get(numNodes/2);
        PastryNode node = (PastryNode)app.getNode();

        // send directly to my leafset (including myself)
        LeafSet leafSet = node.getLeafSet();

        // pick some node in the leafset
        int i=-leafSet.ccwSize();

        // select the item
        NodeHandle nh = leafSet.get(i);

        // send the message directly to the node
        app.sendMyMsgDirect(nh);   

        // wait a bit
        env.getTimeSource().sleep(100);
  
    }
    public static void main(String[] args) throws Exception{
        try {

            boolean useDirect;
            if (args[0].equalsIgnoreCase("-direct")) {
              useDirect = true;
            } else {
              useDirect = false; 
            }

            // Loads pastry settings
            Environment env;
            if (useDirect) {
              env = Environment.directEnvironment();
            } else {
              env = new Environment(); 

              // disable the UPnP setting (in case you are testing this on a NATted LAN)
              env.getParameters().setString("nat_search_policy","never");      
            }

            int bindport = 0;
            InetSocketAddress bootaddress = null;

            // the number of nodes to use is always the last param
            int numNodes = Integer.parseInt(args[args.length-1]);    

            if (!useDirect) {
              // the port to use locally
              bindport = Integer.parseInt(args[0]);

              // build the bootaddress from the command line args
              InetAddress bootaddr = InetAddress.getByName(args[1]);
              int bootport = Integer.parseInt(args[2]);
              bootaddress = new InetSocketAddress(bootaddr,bootport);    
            }

            // launch our node!
            TryPastry dt = new TryPastry(bindport, bootaddress, numNodes, env, useDirect);
        } catch (Exception e) {
            System.out.println("Usage:"); 
            System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.appsocket.Tutorial localbindport bootIP bootPort numNodes");
            System.out.println("  or");
            System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.appsocket.Tutorial -direct numNodes");
            System.out.println();
            System.out.println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10");
            System.out.println("example java rice.tutorial.DistTutorial -direct 10");
            throw e; 
        }
    }
    
}

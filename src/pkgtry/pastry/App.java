package pkgtry.pastry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.mpisws.p2p.filetransfer.BBReceipt;
import org.mpisws.p2p.filetransfer.FileReceipt;
import org.mpisws.p2p.filetransfer.FileTransfer;
import org.mpisws.p2p.filetransfer.FileTransferCallback;
import org.mpisws.p2p.filetransfer.FileTransferImpl;
import org.mpisws.p2p.filetransfer.FileTransferListener;
import org.mpisws.p2p.filetransfer.Receipt;

import rice.Continuation;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.appsocket.*;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

/**
 *
 * @author Yennifer Herrera
 */
public class App implements Application{
    protected Endpoint endpoint;
    protected Node node;
    protected FileTransfer fileTransfer;
    
    public App(Node node, final IdFactory factory){
        this.endpoint = node.buildEndpoint(this, "instance");
        this.node = node;
        
        endpoint.accept(new AppSocketReceiver(){
            @Override
            public void receiveSocket(AppSocket socket) throws IOException {
                fileTransfer = new FileTransferImpl(socket, new FileTransferCallback(){
                    @Override
                    public void messageReceived(ByteBuffer bb) {
                        System.out.println("Message received: " + bb);
                    }

                    @Override
                    public void fileReceived(File file, ByteBuffer metadata) {
                        try{
                            String originalFileName = new SimpleInputBuffer(metadata).readUTF();
                            File dest = new File("Hola.txt");
                            System.out.println("Moving "+file+" to "+dest+" original: "+originalFileName);
                            System.out.println(file.renameTo(dest));
                        }catch(IOException ioe){
                            System.out.println("Error deserializing file name. "+ioe);
                        }
                    }

                    @Override
                    public void receiveException(Exception ioe) {
                        System.out.println("FTC.receiveException: "+ioe);
                    }
                
                }, App.this.node.getEnvironment());
                
                fileTransfer.addListener(new MyFileListener());
                
                endpoint.accept(this);
            }

            @Override
            public void receiveSelectResult(AppSocket socket, boolean canRead, boolean canWrite) throws IOException {
                throw new RuntimeException("Shouldn't be called.");
            }

            @Override
            public void receiveException(AppSocket socket, Exception excptn) {
                excptn.printStackTrace();
            }
        
        });
        endpoint.register();
    }
    
    class MyFileListener implements FileTransferListener {

        @Override
        public void fileTransferred(FileReceipt receipt,
                long bytesTransferred, long total, boolean incoming) {
            
            String s;
            if (incoming) {
                s = "Downloaded ";
            }else {
                s = " Uploaded ";
            }
            double percent = 100.0*bytesTransferred/total;
            System.out.println(App.this+s+percent+"% of "+receipt);
        }

        @Override
        public void msgTransferred(BBReceipt receipt, int bytesTransferred, int total, boolean incoming) {
            String s;
            if(incoming) {
                s = " Downloaded";
            }else {
                s = " Uploaded ";
            }
            double percent = 100.0*bytesTransferred/total;
            System.out.println(App.this+s+percent+"% of "+receipt);
        }

        @Override
        public void transferCancelled(Receipt receipt, boolean incoming) {
            String s;
            if(incoming) {
              s = " download ";
            } else {
              s = " upload ";              
            }
            System.out.println(App.this+": Cancelled "+s+" of "+receipt);
        }

        @Override
        public void transferFailed(Receipt receipt, boolean incoming) {
            String s;
            if (incoming) {
              s = " download ";
            } else {
              s = " upload ";              
            }
            System.out.println(App.this+": Transfer Failed "+s+" of "+receipt);
        }      
    }
    
    public Node getNode() {
        return node;
    }
    
    public void sendMyMsgDirect(NodeHandle nh) {
        System.out.println(this+" opening to "+nh);    
        endpoint.connect(nh, new AppSocketReceiver() {

          /**
           * Called when the socket comes available.
           */
          public void receiveSocket(AppSocket socket) {        
            // create the FileTransfer object
            FileTransfer sender = new FileTransferImpl(socket, null, node.getEnvironment());         

            // add the listener
            sender.addListener(new App.MyFileListener());

            // Create a simple 4 byte message
            ByteBuffer sendMe = ByteBuffer.allocate(4);
            sendMe.put((byte)1);
            sendMe.put((byte)2);
            sendMe.put((byte)3);
            sendMe.put((byte)4);

            // required when using a byteBuffer to both read and write
            sendMe.flip();

            // Send the message
            System.out.println("Sending "+sendMe);        
            sender.sendMsg(sendMe, (byte)1, null);

            try {
              // get the file
              final File f = new File("delme.txt");

              // make sure it exists
              if (!f.exists()) {
                System.err.println("File "+f+" does not exist.  Please create a file called "+f+" and run the tutorial again.");
                System.exit(1);
              }

              // serialize the filename
              SimpleOutputBuffer sob = new SimpleOutputBuffer();
              sob.writeUTF(f.getName());

              // request transfer of the file with priority 2
              sender.sendFile(f,sob.getByteBuffer(),(byte)2,new Continuation<FileReceipt, Exception>() {

                public void receiveException(Exception exception) {
                  System.out.println("Error sending: "+f+" "+exception);
                }

                public void receiveResult(FileReceipt result) {
                  System.out.println("Send complete: "+result);
                }
              });

            } catch (IOException ioe) {
              ioe.printStackTrace();
            }
          }    

          /**
           * Called if there is a problem.
           */
          public void receiveException(AppSocket socket, Exception e) {
            e.printStackTrace();
          }

          /**
           * Example of how to write some bytes
           */
          public void receiveSelectResult(AppSocket socket, boolean canRead, boolean canWrite) {   
            throw new RuntimeException("Shouldn't be called.");
          }
        }, 30000);
    }
  
    @Override
    public boolean forward(RouteMessage message) {
        return true;
    }

    @Override
    public void deliver(Id id, Message message) {
        System.out.println(this+" received "+message);
    }

    @Override
    public void update(NodeHandle nh, boolean joined) {
      
    }
    
    @Override
    public String toString(){
        return "App "+endpoint.getId();
    }
}


package tftp.udp.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 *
 * 135124
 */
public class TFTPUDPServer implements Runnable {

    @Override
    public void run() {
        
    }

    /**
     * 
     * This is the main server and creates threads when a client is trying to
     * connect to server
     *
     * @param args
     * @throws SocketException
     * @throws IOException
     */
    public static void main(String args[]) throws SocketException, IOException{  
         System.out.println("started the main server");
         int socketNo = 9000;
         
         while(true){
             DatagramSocket socket = new DatagramSocket(9000);
             byte[] recBuf = new byte[516];
             //constructs the datagram using the bytes array
             DatagramPacket packet = new DatagramPacket(recBuf, 516);
             socket.receive(packet);
             System.out.println("Received first Req Packet");
             socketNo++;
             //Create a new Server thread using the socket number and packet
             UDPServerThread server = new UDPServerThread(socketNo, packet);
             System.out.println("new Server Thread");
             server.start();
             socket.close();
         }
         
     }

    
    
}

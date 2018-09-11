
package tftp.udp.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 *
 * 135124
 */
public class UDPServerThread extends Thread {

  
    protected DatagramSocket socket = null;
    private BufferedReader in;
    private boolean endFile = true;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATAPACKET = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    private static final int DATALENGTH = 512;
    private int defaultPort;
    private int socketNo; 
    private InetAddress address;
    private DatagramPacket firstPacket;
    private String fileName;
    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
   

    /**
     *this is the server thread that gets created when the server class creates a new thread
     * @param socketNo the socket number from the incoming packet
     * @param packet the packet which will contain a receive packet 
     * @throws SocketException
     * @throws FileNotFoundException
     */
    public UDPServerThread(int socketNo, DatagramPacket packet) throws SocketException, FileNotFoundException {
        this("TFTPUDPServer");
        this.socketNo = socketNo; 
        this.firstPacket = packet; 
        socket = new DatagramSocket(socketNo);
    }

    /**
     * the super class of the thread 
     * @param name
     * @throws SocketException
     * @throws FileNotFoundException
     */
    public UDPServerThread(String name) throws SocketException, FileNotFoundException {
        super(name);

    }

    
    @Override
    public void run() {
        int counter = 0;
        
        try {           
            System.out.println("recieved Packet");
            socket.setSoTimeout(10000);
            firstReq(firstPacket);                   
        } catch (IOException e) {
            System.err.println(e);
            endFile = false;
        }
        System.out.println("done");
        socket.close();
    }

    /**
     * this receives the first request from the client and decides by its opcode 
     * wherever its a read or write request.
     * 
     * @param packet the packet received 
     * @throws IOException
     */
    public void firstReq(DatagramPacket packet) throws IOException {
        byte[] opcode = new byte[2];
        byte[] inDataStream = packet.getData();
        for (int i = 0; i < 2; i++) {
            //this gets the opcode 
            opcode[i] = inDataStream[i];
        }
        //if its a read request then read the file name from the packet 
        if (opcode[0] == 0 && opcode[1] == OP_RRQ) {
            System.out.println("read request");
             address = packet.getAddress();
             defaultPort = packet.getPort();
            readFileName(packet);
           
        //if its a write request then send an ack 
        } else if (opcode[0] == 0 && opcode[1] == OP_WRQ ) {
            System.out.println("write request");
            address = packet.getAddress();
            readFileName(packet);
            System.out.println("Send Ack for write Request");
            defaultPort = packet.getPort();
            DatagramPacket ack = new DatagramPacket(sendFirstAck(), 4, packet.getAddress(), packet.getPort());
            socket.send(ack);
            receiveFile();
            
        }
    }

    /**
     *
     * this reads the file name and then retrieves the data from the file
     * and then calls sendFile 
     * @param packet the packet to that has been received 
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void readFileName(DatagramPacket packet) throws FileNotFoundException, IOException {
        byte[] inDataStream = packet.getData();
         int i = 2;
        int j = 0;
        while (inDataStream[i] != 0) {
            
            i++;
        }
        ByteBuffer fileNameBytes = ByteBuffer.allocate(i - 2);
        i = 2;
        while (inDataStream[i] != 0) {
            fileNameBytes.put(inDataStream[i]);
            i++;
        }
        fileName = new String(fileNameBytes.array());

        System.out.println(fileName);

        File file = new File(fileName);
        if(inDataStream[1] != OP_WRQ){
            if(!file.exists()){
                System.out.println("ERROR CODE 1 - FILE NOT FOUND");
                createError(1, "File not found");
            }else{
                System.out.println(file.length());
                byte[] fileByte = new byte[(int) file.length()];
                try {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    fileInputStream.read(fileByte);
                    sendFile(fileByte, packet);
                } catch (FileNotFoundException e) {
                    System.out.println("File Not Found.");
                    e.printStackTrace();
                } catch (IOException e1) {
                    System.out.println("Error Reading The File.");
                    e1.printStackTrace();
                }
               
            }
        }
    }

    /**
     * 
     * this sends a file to the server when the write request has been called 
     * it creates the file and splits it into packets and then sends the packets 
     * to the server in a do while loop, incrementing the block numbers 
     * @param fileByte the file but in bytes 
     * @param packet the packet to send 
     * @throws IOException
     */
    public void sendFile(byte[] fileByte, DatagramPacket packet) throws IOException {
        
        int offset = 0;
        ByteBuffer theFileBuffer = ByteBuffer.wrap(fileByte);
        int byteLength = theFileBuffer.remaining();
        int amountOfPackets = byteLength / DATALENGTH;
        int j = 0;
        int k = -1;
        int dataOffset = 0;
        int firstBlockNumber = -1;
        int secondBlockNumber = 0;
        do{
            byte[] dst;
            if (fileByte.length - (dataOffset) >= 512) {

                dst = new byte[DATALENGTH];
            } else {
                dst = new byte[fileByte.length - (dataOffset)];
            }
            for (int i = dataOffset; i < 512 + dataOffset && i < fileByte.length; i++) {
                dst[j] = fileByte[i];
                j++;
            }
            j = 0;
            dataOffset += 512;secondBlockNumber++;
            if(secondBlockNumber == 128){
                firstBlockNumber++;
                secondBlockNumber = 0;
            }

            DatagramPacket dataPacket = createPacket(packet, dst, firstBlockNumber, secondBlockNumber);
            socket.send(dataPacket);
            packet = receivedAck(packet);
            
            k++;

        } while (receiveAck(packet, firstBlockNumber, secondBlockNumber) && k < amountOfPackets);
        //amountOfPackets--;
    }

    /**
     * This creates a packet to be send, we make sure we sent the data, the address
     * and the port, we need to put the data opcode first, the blockNo and then 
     * the data
     *
     * @param packet the packet 
     * @param theFile the file to be sent in a byte array
     * @param firstBlockNumber the first block number 
     * @param secondBlockNumber the second block number 
     * @return the datagram packet created 
     */
    public DatagramPacket createPacket(DatagramPacket packet, byte[] theFile, int firstBlockNumber, int secondBlockNumber) {
        //Create the data packet to be sent
        //make sure we sent the data, the addess and the port
        //We need to put the data opcode first, Block# and then the data
        int position = 0;
        int offset = 0;


        byte[] dataPacket = new byte[theFile.length + 4];
        dataPacket[position] = (byte) 0;
        position++;
        dataPacket[position] = OP_DATAPACKET;
        position++;
        dataPacket[position] = (byte) firstBlockNumber;
        position++;
        dataPacket[position] = (byte) secondBlockNumber;
        position++;

        for (int i = 0; i < theFile.length; i++) {
            dataPacket[position] = theFile[i];
            position++;
        }
        address = packet.getAddress();
        int port = packet.getPort();
        packet.setData(dataPacket);
        packet.setAddress(address);
        packet.setPort(port);
        return packet;
    }

    /**
     *
     * this is called in sendFile to receive the ack from the server and checks if it is correct 
     * 
     * @param packet packet received 
     * @param firstBlockNumber first block number 
     * @param secondBlockNumber second block number 
     * @return boolean true if it correct 
     * @throws IOException
     */
    public boolean receiveAck(DatagramPacket packet, int firstBlockNumber, int secondBlockNumber) throws IOException {

        byte[] inDataStream = packet.getData();
        if ((int) inDataStream[0] == 0 && (int) inDataStream[1] == OP_ACK && (int) inDataStream[2] == firstBlockNumber && (int) inDataStream[3] == secondBlockNumber) {
            return true;
            //this will send the requested file
        } else {
            return false;
        }
    }

    /**
     * we call this when we are expecting to receive an ack if it does arrive a socket exception is called 
     * @param packet the helps create a new packet 
     * @return the ack received 
     * @throws SocketException
     * @throws IOException
     */
    public DatagramPacket receivedAck(DatagramPacket packet) throws SocketException, IOException {
        byte[] byteAck = new byte[4];
        DatagramPacket ack = new DatagramPacket(byteAck, 4, packet.getAddress(), packet.getPort());
        boolean notReceived = true;
        while (notReceived) {
                socket.setSoTimeout(10000);
                socket.receive(ack);
                notReceived = false;
            
        }
        return ack;
    }

     /**
     *this creates an ack 
     * @param first first block number
     * @param second block number 
     * @return byte[] 
     */
    public byte[] sendAck(byte first, byte second) {
        byte[] ack = new byte[4];
        int position = 0;
        ack[position] = 0;
        position++;
        ack[position] = OP_ACK;
        position++;
        ack[position] = first;
        position++;
        ack[position] = second;
        return ack;

    }

    /**
     * This sends the first ack back to client when a write request has been sent 
     * to the server 
     * @return ack byte[] 
     */
    public byte[] sendFirstAck() {
        byte[] ack = new byte[4];
        int position = 0;
        ack[position] = 0;
        position++;
        ack[position] = OP_ACK;
        position++;
        ack[position] = 0;
        position++;
        ack[position] = 0;
        return ack;

    }
    
    /**
     * This will receive the file from the server and is called when the receive
     * request is sent
     * 
     * @throws UnknownHostException
     * @throws SocketException
     * @throws IOException
     */
    public void receiveFile() throws UnknownHostException, SocketException, IOException {
        InetAddress address = InetAddress.getByName("localhost");
        boolean endOfFile = true;
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        while (endOfFile) {
            byte[] readByteArray = new byte[516];
            DatagramPacket packet = new DatagramPacket(readByteArray, 516, address, defaultPort);
            socket.receive(packet);
        
            byte[] packetInput = new byte[packet.getData().length];
            packetInput = packet.getData();

              //this waits till the end of file, we know when its the end of the file 
              //as the packet.length < 516 bytes 
              //this also send all of the acks
            if (packetInput[1] == OP_ERROR) {
                error(packetInput);
            }else{
                if (packetInput[1] == OP_DATAPACKET && packet.getLength() == 516) {
                    DatagramPacket ack = new DatagramPacket(sendAck(packetInput[2], packetInput[3]), 4, address, defaultPort);
                    file.write(packetInput, 4, packetInput.length - 4);
                    socket.send(ack);

                } else if (packetInput[1] == OP_DATAPACKET && packet.getLength() < 516) {
                    DatagramPacket ack = new DatagramPacket(sendAck(packetInput[2], packetInput[3]), 4, address, defaultPort);
                   
                    int j = 0;
                    for (int i = 4; i < packetInput.length; i++) {
                        if (packetInput[i] == (byte) 0) {
                            j++;
                        }
                    }
                    file.write(packetInput, 4, (packetInput.length - 4) - j);
                    socket.send(ack);
                    writeFile(file);
                    endOfFile = false;
                  
                }
            }
        }
     

    }
    
     /**
     *This writes the file using the filename given and the data that has been
     * sent from the server 
     * @param file this is the file from the server
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void writeFile(ByteArrayOutputStream file) throws FileNotFoundException, IOException {
           try (OutputStream outputStream = new FileOutputStream(fileName)) {
            file.writeTo(outputStream);
        }
    }

    /**
     * this creates an error message 
     * @param byteArray the error array 
     */
    public void error(byte[] byteArray) {
        String errorCode = new String(byteArray, 3, 1);
        String errorText = new String(byteArray, 4, byteArray.length - 4);
        System.err.println("Error: " + errorCode + " " + errorText);
    }

    /**
     * this creates an error message to be sent
     * 
     * @param errorCode the error code 
     * @param errMessage the message which will be sent 
     * @throws IOException
     */
    public void createError(int errorCode, String errMessage) throws IOException{
        byte[] error = new byte[512];
        int position = 0;
        error[position] = 0;
        position++;
        error[position] = OP_ERROR;
        position++;
        error[position] = 0;
        position++;
        error[position] = (byte) errorCode;
        position++;
        for(int i = 0; i < errMessage.length(); i++){
            error[position] = (byte)errMessage.charAt(i);
            position++;
        }
        error[position] = 0;
        DatagramPacket errorPacket = new DatagramPacket(error, error.length, address, defaultPort);
        socket.send(errorPacket);
    }
}

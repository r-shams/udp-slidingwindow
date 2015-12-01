package com.fin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;


public class Reciever
{
    private int headerSize = 12;
    public  DatagramSocket socket;
	private InetAddress sender;
    private int local_port = 12987;
    private int sendingPort;
    private long window_size = 4;
    public int max_packet_size;
	public int getMax_packet_size() {
		return max_packet_size;
	}


	private int max_num_frames;
    private int last_seq_num = -1;
    private int[] receivedPacketsArray;
    private byte[][] received_bytes;
    private String filename = "received";
    private FileOutputStream fileOut;
    private boolean initial_connection = false;
    private long file_size=0;
    private long start_time;
    private long end_time;
    private int last_frame_received = -1;
    private int largest_acceptable_frame = 0;

	public static void main(String[] args) 
	{
		
		if(args.length % 2 != 0)
		{
			System.out.println("Invalid amount of parameters specified");
			return;
		}
		
		System.out.println(parseArgs(args));
		Map<String,String> argsMap = parseArgs(args);
		
		Reciever receiver = new Reciever();
		if(argsMap.containsKey("-w"))
		{
			receiver.setModeParameter(Integer.parseInt(argsMap.get("-w")));
		}
		
		if(argsMap.containsKey("-p"))
		{
			receiver.setMax_packet_size(Integer.parseInt(argsMap.get("-p")));
		}
		receiver.setFilename("hello_received.txt");
		receiver.setLocalPort(32456);
		System.out.println("Sliding Window Size = " + receiver.window_size);
		System.out.println("Packet size = " + receiver.getMax_packet_size());
		receiver.receiveFile();
	}
	
	public static Map<String, String> parseArgs(String[] args)
	{
		Map<String,String> argMap = new HashMap<String, String>();
		String tempKey = null;
		String tempValue = null;
		for(int i = 0;i < args.length;i++)
		{
			if(tempKey != null && !tempKey.equals(""))
			{
				tempValue = args[i];
				argMap.put(tempKey, tempValue);
				tempKey = null;
				tempKey = null;
			}
			else
			{
				tempKey = args[i];
			}
		}
		
		return argMap;
	}
	
	private void calculatePackets()
	{
		try 
		{
			int mtu = socket.getSendBufferSize();
			int result = (int)Math.ceil(window_size/(double)mtu);
			if (result==1) 
			{
				this.max_num_frames = result;
			} 
			else 
			{
				this.max_num_frames = (int)Math.floor(window_size/(double)mtu);
			}

			this.receivedPacketsArray = new int[max_num_frames];
			this.received_bytes = new byte[max_num_frames][max_packet_size-headerSize];
			for (int i = 0; i < receivedPacketsArray.length; i++) 
			{
				this.receivedPacketsArray[i]=0;
			}
			this.largest_acceptable_frame = last_frame_received + max_num_frames;
			
		} 
		catch (SocketException e) 
		{
			e.printStackTrace();
		}
	}
	
	private boolean sendACK(int seq_num)
	{
		byte[] ack = intToBytes(seq_num);
		try 
		{
			socket.send(new DatagramPacket(ack, ack.length, this.sender, sendingPort));
			return true;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
	}
	
	public void processPacket(DatagramPacket packet) 
	{
		this.sender = packet.getAddress();
		this.sendingPort = packet.getPort();
		
		byte[] packetData = packet.getData();
		
		byte[] seq_num_bytes = new byte[4];
		byte[] eop_bytes = new byte[4];
		byte[] last_packet_bytes = new byte[4];
		byte[] payload;
		
		
		System.arraycopy(packetData, 0, seq_num_bytes, 0, seq_num_bytes.length);
		System.arraycopy(packetData, seq_num_bytes.length, eop_bytes, 0, eop_bytes.length);
		System.arraycopy(packetData, seq_num_bytes.length+eop_bytes.length, last_packet_bytes, 0, last_packet_bytes.length);
		int seq_num = bytesToInt(seq_num_bytes);
		int eop = bytesToInt(eop_bytes);
		int last_packet = bytesToInt(last_packet_bytes);
		
		if(seq_num==0 && !initial_connection){
			initial_connection = true;
			this.start_time = System.currentTimeMillis();
			System.out.println("Sender: "+packet.getAddress()+":"+packet.getPort());
		}
		
		if(last_packet!=1)
		{
			payload = new byte[(max_packet_size - headerSize)];
			System.arraycopy(packetData, headerSize, payload, 0, payload.length);
		}
		else
		{
			this.last_seq_num = seq_num;
			payload = new byte[eop-headerSize];

			System.arraycopy(packetData, headerSize, payload, 0, eop-headerSize);
		}
		
		boolean acceptPacket = (seq_num<=largest_acceptable_frame);
		if (acceptPacket) 
		{
			acceptPacket(seq_num, eop, last_packet, payload);
		}
		
	}
	
	private void acceptPacket(int seq_num, int eop, int last_packet, byte[] payload)
	{
		System.out.println("Received'"+new String(payload)+"' from " +sender);
	
		if(last_frame_received >= seq_num){
			sendACK(seq_num);
		}
		else
		{
			receivedPacketsArray[seq_num-last_frame_received-1] = 1;
			received_bytes[seq_num-last_frame_received-1] = payload;
			int adjustedWindow = 0;
			int i_val = 0;
			for (int i = 0; i < receivedPacketsArray.length; i++) 
			{
				if(receivedPacketsArray[i]==1)
				{
					last_frame_received+=1;
					largest_acceptable_frame+=1;
					this.file_size+=payload.length;
					System.out.println("Message #" + seq_num + " received. Length="+payload.length+" bytes");
					processPayload(last_frame_received, received_bytes[i]);
					sendACK(last_frame_received);
					System.out.println("sent acknowledgement for message #"+seq_num);
				}
				else
				{
					adjustedWindow = i;
					break;
				}
				i_val = i;
			}
			
			if(i_val==receivedPacketsArray.length-1)
			{
				adjustedWindow = receivedPacketsArray.length;
			}
		
			for (int i = 0; i < adjustedWindow; i++) 
			{
				receivedPacketsArray[i]=0;
			}
		}
	}
	
	private void processPayload(int seq_num, byte[] payload) {
		try {
			this.fileOut.write(payload);
			this.fileOut.flush();
			
			if(seq_num==last_seq_num){
				this.fileOut.close();
				this.end_time = System.currentTimeMillis();
				double runtime = ((this.end_time-this.start_time)/(double)1000);
				System.out.println("Successfully received "+this.filename+" ("+this.file_size+" bytes) in "+runtime+" seconds");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void receiveFile() 
	{
		try
		{
			System.out.println("Receiver "+InetAddress.getLocalHost().getHostAddress()+" listening on UDP port "+this.local_port);
            this.socket = new DatagramSocket(local_port);
            calculatePackets();
            this.fileOut = new FileOutputStream(new File(this.filename));
            new PacketReceiver(this);
        }
        catch(Exception e)
		{
        	e.printStackTrace();
        }
	}
	
	public String getFilename() {
		return filename;
	}

	
	public int getLocalPort() {
		return local_port;
	}
	
	public void setFilename(String fname) {
		this.filename = fname;
		
	}
	
	public boolean setLocalPort(int port) {
		try {
			this.local_port = port;
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public void setModeParameter(long n) {
		this.window_size = n;
	}
	
	public static byte[] intToBytes(int data) {
		return new byte[] {
		(byte)((data >> 24) & 0xff),
		(byte)((data >> 16) & 0xff),
		(byte)((data >> 8) & 0xff),
		(byte)((data >> 0) & 0xff),
		};
	}
	
	public static int bytesToInt(byte[] data) {
		if (data == null || data.length != 4) return 0x0;
		// ----------
		return (int)(
		(0xff & data[0]) << 24 |
		(0xff & data[1]) << 16 |
		(0xff & data[2]) << 8 |
		(0xff & data[3]) << 0
		);
	}


	public void setMax_packet_size(int max_packet_size) {
		this.max_packet_size = max_packet_size;
	}
}

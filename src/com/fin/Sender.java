package com.fin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class Sender 
{
	private int headerSize = 12;
	DatagramSocket socket;
    private int receiverPort = 32456;
    private int senderPort = 12987;
    private InetSocketAddress receiver = new InetSocketAddress("localhost", receiverPort);
    private long window_size = 4;
    private long timeout = 1000;
    private int max_packet_size;
	private int max_num_frames;
    private int last_seq_num;
    private String filename;
    private byte[] data;
    private boolean initial_connection = false;
    private long file_size=0;
    private long start_time;
    private long end_time;
    private int last_ack_received = -1;
    private int last_frame_sent = -1;
    private TimeoutSender[] sender_window;
    private int probabilityOfDrop = 0;


	public Sender() 
	{
		super();
	}
	
	
	public static void main(String[] args) 
	{
		Sender sender = new Sender();
		sender.window_size = 100;
		Map<String,String> argsMap = parseArgs(args);
		if(argsMap.containsKey("-w"))
		{
			sender.window_size = (Integer.parseInt(argsMap.get("-w")));
		}
		
		if(argsMap.containsKey("-p"))
		{
			sender.setMax_packet_size(Integer.parseInt(argsMap.get("-p")));
		}
		
		if(argsMap.containsKey("-t"))
		{
			sender.setTimeout(Integer.parseInt(argsMap.get("-t")));
		}
		
		if(argsMap.containsKey("-d"))
		{
			sender.setProbabilityOfDrop(Integer.parseInt(argsMap.get("-d")));
		}
		sender.setFilename("hello.txt");
		
		System.out.println("Sliding Window Size = " + sender.window_size);
		System.out.println("Packet size = " + sender.getMax_packet_size());
		System.out.println("Timeout size = " + sender.getTimeout());
		sender.sendFile();

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
			if (result==1) {
				this.max_num_frames = result;
			} 
			else 
			{
				this.max_num_frames = (int)Math.floor(window_size/(double)mtu);
			}
			this.sender_window = new TimeoutSender[max_num_frames];
			for (int i = 0; i < sender_window.length; i++) 
			{
				this.sender_window[i]=null;
			}
		} 
		catch (SocketException e) 
		{
			e.printStackTrace();
		}
	}
	
	public void transmit(int seq_num)
	{
		
		byte[] dataPart = getData(seq_num);

		int eop = headerSize + dataPart.length;
	
		int last_packet = 0;
		if(seq_num==last_seq_num)
		{
			last_packet = 1;
		}
		
		byte[] packetToSend = new byte[max_packet_size];
		byte[] seq_num_bytes = intToBytes(seq_num);
		byte[] headerBytes = intToBytes(eop);
		byte[] lastPacket = intToBytes(last_packet);
		
		System.arraycopy(seq_num_bytes, 0, packetToSend, 0, seq_num_bytes.length);
		System.arraycopy(headerBytes, 0, packetToSend, seq_num_bytes.length, headerBytes.length);
		System.arraycopy(lastPacket, 0, packetToSend, seq_num_bytes.length+headerBytes.length, lastPacket.length);
		
		System.arraycopy(dataPart, 0, packetToSend, headerSize, dataPart.length);
		

		DatagramPacket datagram = new DatagramPacket(packetToSend, packetToSend.length, receiver.getAddress(), receiver.getPort());

		try 
		{
			if(!probability(probabilityOfDrop))
			{
				socket.send(datagram);
				System.out.println("Sent message #" +seq_num + ". Length= "+dataPart.length+" bytes");
			}
			else
			{
				System.out.println("Dropped message #" +seq_num + ". Length= " + dataPart.length + " bytes");
			}
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
	}
	
	private byte[] getData(int seq_num){
		int room_for_data = (max_packet_size - headerSize);
		byte[] subData;
		int start = ((seq_num)*room_for_data);
		int end = start + (room_for_data - 1);
		int dataLength = this.data.length;
		
		if(end<=dataLength){
			subData = new byte[room_for_data];
			System.arraycopy(this.data, start, subData, 0, room_for_data);
		}
		else{
			
			subData = new byte[dataLength-start];
			System.arraycopy(this.data, start, subData, 0, dataLength-start);
		}
		
		return subData;
		
	}
	
	private void prepareFile() throws Exception{
	

		File filepath = new File(this.filename);
        InputStream is = new FileInputStream(filepath);
        
        long length = filepath.length();
        this.file_size = length;
        this.data = new byte[(int)length];
      
		this.last_seq_num = (int)Math.ceil(data.length/(double)max_packet_size) - 1;
    
        int offset = 0;
        int numRead = 0;
        while (offset < this.data.length && (numRead=is.read(this.data, offset, this.data.length-offset)) >= 0)
        {
        	offset += numRead;
        }
        if (offset < this.data.length) 
        {
            throw new Exception("Could not completely read file "+filepath.getName());
        }
        
        is.close();
	}
	
	public void processACK(DatagramPacket packet) {
		int seq_num = bytesToInt(packet.getData());
		
		if(seq_num==0 && !initial_connection)
		{
			initial_connection = true;
			System.out.println("Listening on: "+packet.getAddress()+":"+packet.getPort());
		}
		
		System.out.println("Recieved  acknowledgment for message #"+seq_num);
		
		if(seq_num>last_ack_received)
		{
			this.last_ack_received = seq_num;
			
			updateTimeoutThreads(seq_num);
		}
	}

	private void updateTimeoutThreads(int seq_num) 
	{
		for (int i = 0; i < sender_window.length; i++) 
		{
			if(sender_window[i]!=null){
				
				TimeoutSender timeout_thread = sender_window[i];
				
				if(timeout_thread.seq_num<=seq_num)
				{
					timeout_thread.finished=true;
					
					last_frame_sent+=1;
					if(last_frame_sent<=last_seq_num)
					{
						transmit(last_frame_sent);
						sender_window[i] = new TimeoutSender(last_frame_sent, (int)timeout, this);
					}//end if
					else{
						this.end_time = System.currentTimeMillis();
						double runtime = ((this.end_time-this.start_time)/(double)1000);
						System.out.println("Successfully transferred "+this.filename+" ("+this.file_size+" bytes) in "+runtime+" seconds");
					}
				}
			}
		}
	}
	
	public boolean probability(int percent)
	{
	    Random r=new Random();
	    return r.nextInt(100)<percent;
	}

	public boolean sendFile() 
	{
		try
		{
			this.start_time = System.currentTimeMillis();
			System.out.println("Sender "+InetAddress.getLocalHost().getHostAddress()+" listening on UDP port "+this.senderPort);
            this.socket = new DatagramSocket(senderPort);
            new PacketSender(socket, this);
            calculatePackets(); 
			prepareFile();

            for (int i = 0; i < sender_window.length; i++) 
            {
            	last_frame_sent+=1;
				
				if(last_frame_sent<=last_seq_num)
				{
					transmit(last_frame_sent);
					sender_window[i] = new TimeoutSender(last_frame_sent, (int)timeout,this);
				}
			}
            return true;
        }
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	public String getFilename() 
	{
		return filename;
	}


	public InetSocketAddress getReceiver() {
		return receiver;
	}


	public long getTimeout() {
		return timeout;
	}

	public void setFilename(String fname) {
		this.filename = fname;
	}


	public void setReceiver(InetSocketAddress receiver) 
	{
		this.receiver = receiver;
	}


	public void setTimeout(long timeout) 
	{
		this.timeout = timeout;
	}
	

	public static byte[] intToBytes(int data) 
	{
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
		return (int)( // NOTE: type cast not necessary for int
		(0xff & data[0]) << 24 |
		(0xff & data[1]) << 16 |
		(0xff & data[2]) << 8 |
		(0xff & data[3]) << 0
		);
	}
	
    public int getMax_packet_size() 
    {
		return max_packet_size;
	}

	public void setMax_packet_size(int max_packet_size) {
		this.max_packet_size = max_packet_size;
	}
	

	public int getProbabilityOfDrop() {
		return probabilityOfDrop;
	}


	public void setProbabilityOfDrop(int probabilityOfDrop) {
		this.probabilityOfDrop = probabilityOfDrop;
	}

}
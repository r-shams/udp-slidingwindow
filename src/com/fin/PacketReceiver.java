package com.fin;

import java.io.IOException;
import java.net.DatagramPacket;

public class PacketReceiver implements Runnable 
{
	private Reciever reciever;
	public PacketReceiver(Reciever reciever)
	{
		if(reciever != null)
		{
			this.reciever = reciever;
			Thread thread = new Thread(this);
			thread.start();
		}
	}
	
	public void run() {
		while(true)
		{
			try 
			{
				byte [] bytesIn = new byte[reciever.max_packet_size];
				DatagramPacket packet = new DatagramPacket(bytesIn,bytesIn.length);
				reciever.socket.receive(packet);					
				reciever.processPacket(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

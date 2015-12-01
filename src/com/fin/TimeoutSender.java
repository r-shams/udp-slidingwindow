package com.fin;

public class TimeoutSender extends Thread
{
	private int timeout;
	private int elapsed;
	public int rate = 100;
	public boolean finished;
	public int seq_num;
	private Sender sender;
	
	public TimeoutSender(int seq_num, int millis, Sender sender)
	{
		this.seq_num = seq_num;
		this.timeout = millis;
		elapsed = 0;
		this.sender = sender;
		this.start();
	}
	
	public synchronized void reset()
	{
		elapsed = 0;
	}
	
	public void run()
	{
		while(!finished)
		{
			try
			{
				Thread.sleep(rate);
			}
			catch (InterruptedException ioe) {
				continue;
			}
			
			synchronized (this)
			{
				elapsed += rate;
				if (elapsed > this.timeout){
					timeout();
				}
			}
		}
	}

	public void timeout()
	{
		System.out.println("Message #"+this.seq_num+" timed out. Resending...");
		sender.transmit(this.seq_num);
		reset();
	}
	
	public int getRate() {
		return rate;
	}

	public void setRate(int rate) {
		this.rate = rate;
	}
}

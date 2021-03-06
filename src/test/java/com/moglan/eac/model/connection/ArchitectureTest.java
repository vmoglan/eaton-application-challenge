package com.moglan.eac.model.connection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.moglan.eac.application.Config;

public class ArchitectureTest {
	
	private static final String LOCALHOST = "127.0.0.1";
	
	private static final int MAX_SENT_PER_DEVICE = 2;
	private static final int MAX_CLIENTS = 2;

	/**
	 * Verifies whether a sub-class of TCPServer can successfully implement a global 
	 * counting system of all received messages without any concurrency issues such 
	 * as the simultaneous access of a critical by multiple threads (in this case, the counter).
	 */
	@Test
	void globalCountTest() {
		List<Future<?>> futures = new ArrayList<>();
		ExecutorService executionPool = Executors.newFixedThreadPool(MAX_CLIENTS);
		DummyServer server = null;
		
		try {
			/*
			 * Starting dummy server 
			 */
			
			server = new DummyServer(Config.SERVER_PORT);
			
			server.start();
			
			/*
			 * Starting dummy clients
			 */
			
			for (int i = 0; i < MAX_CLIENTS; i++) {
				Runnable runnable = new DummyClient(i, LOCALHOST, Config.SERVER_PORT);
				Future<?> future = executionPool.submit(runnable);
				
				futures.add(future);
			}
			
			/*
			 * Launching blocking operation '.get()' in order to waiting for each client to be done
			 */
			
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			/*
			 * Once all the futures are complete, if the number of messages received is equal to the number 
			 * of sending devices times the number of messages sent by device, the count is correct.
			 */
			
			boolean allDone = true;
			
			for(Future<?> future : futures){
			    allDone &= future.isDone();
			}
			
			if (allDone) {
				assertEquals(MAX_SENT_PER_DEVICE * MAX_CLIENTS, server.getNumRecvMessages());
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			executionPool.shutdown();
			server.stop();
		}
	}
	
	/**
	 * Tests the server shutdown mechanism
	 */
	@Test
	void serverShutdownTest() {
		TCPServer server = null;
		
		server = new DummyServer(Config.SERVER_PORT);
		
		server.start();
		
		assertTrue(server.isRunning());
		
		server.stop();
		
		assertFalse(server.isRunning());
	}

	/**
	 * Dummy server counting the total number of received messages.
	 */
	class DummyServer extends TCPServer {
		
		private long messageCount;	// total number of received messages

		public DummyServer(int port) {
			super(port);
			
			messageCount = 0;
		}

		public long getNumRecvMessages() { return this.messageCount; }

		@Override
		protected void onClientConnect(Socket socket) {
			return;
		}

		@Override
		protected void onClientDisconnect(Socket socket) {
			return;
		}
		
		@Override
		protected synchronized Message<?> handleRequest(Message<?> request, int port) {
			if (request.getProtocol() == Protocol.END_CONNECTION) {
				return null;
				
			} 
			
			messageCount++;
			
			return new Message<>(0, Protocol.DATA_TRANSFER, "Response");
		}

		@Override
		protected void onServerShutdown() {
			return;
		}

		@Override
		protected void onServerStart() {
			return;
		}

	}
	
	/**
	 * Dummy client class, sending a limited number of messages to the server.
	 */
	class DummyClient extends TCPClient {
		
		int id;
		private int numberOfSentMessages;
		
		public DummyClient(int id, String addr, int port) throws UnknownHostException, IOException {
			super(addr, port);
			
			this.id = id;
			numberOfSentMessages = 0;
		}

		@Override
		protected Message<?> createRequest() {
			if (numberOfSentMessages < MAX_SENT_PER_DEVICE) {
				numberOfSentMessages++;
				
				return new Message<>(id, Protocol.DATA_TRANSFER, "Request");
			}
			
			return new Message<>(id, Protocol.END_CONNECTION);
		}

		@Override
		protected void handleResponse(Message<?> reply) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				LOGGER.severe(e.getMessage());
			}
		}

		@Override
		protected void onConnect() {
			return;
		}

		@Override
		protected void onDisconnect() {
			return;
		}
		
	}

}




package engine.resources.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import main.NGECore;

import org.apache.mina.core.buffer.CachedBufferAllocator;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;


import engine.clients.Client;
import engine.protocol.AuthClient;
import engine.protocol.packager.MessageCRC;
import engine.protocol.packager.MessageCompression;
import engine.protocol.packager.MessageEncryption;
import engine.protocol.packager.MessagePackager;
import resources.common.Opcodes;
import engine.resources.common.Utilities;
import engine.servers.MINAServer;


public class NetworkDispatch extends IoHandlerAdapter implements Runnable {
	
	protected ArrayList<INetworkDispatch> services;
	protected ExecutorService eventThreadPool;
	protected Map<Integer, INetworkRemoteEvent> remoteLookup;
	protected Map<Integer, INetworkRemoteEvent> objectControllerLookup;
	protected NGECore core;
	private boolean isZone;
	private Map<IoSession, Vector<IoBuffer>> queue;
	private CachedBufferAllocator bufferPool;
	private String maxSessions = "5";
	private MINAServer server;
	private long startTime;
	private String maxTime = "1800000";
	private static final boolean enable = false;
	
	public NetworkDispatch(NGECore core, boolean isZone) {
		
		this.core = core;
		
		services = new ArrayList<INetworkDispatch>();

		remoteLookup = new HashMap<Integer, INetworkRemoteEvent>();
		objectControllerLookup = new HashMap<Integer, INetworkRemoteEvent>();

		eventThreadPool = Executors.newCachedThreadPool();
		this.isZone = isZone;
		
		if(isZone) {
			AuthClient client = new AuthClient(core);
		}
		
		if(enable) {
			maxTime = "18000000000000";
			maxSessions = "100000";
		}

		bufferPool = new CachedBufferAllocator();
		queue = new ConcurrentHashMap<IoSession, Vector<IoBuffer>>();
		Thread queueThread = new Thread(this);
		queueThread.start();
		try {
			Class<?> c = Class.forName("java.lang.System");
			startTime = (long) c.getMethod("currentTimeMillis", null).invoke(c, null);
		} catch (IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException
					| SecurityException | ClassNotFoundException e) {
				e.printStackTrace();
		}
	}
	    
	@Override
	public void sessionClosed(IoSession session) throws Exception {

		Map<Short, byte[]> sentPackets = ((Map<Short, byte[]>) session.getAttribute("sentPackets"));
		sentPackets.clear();
		Map<Short, byte[]> resentPackets = ((Map<Short, byte[]>) session.getAttribute("resentPackets"));
		resentPackets.clear();

		if(isZone) {
			core.simulationService.handleDisconnect(session);
		}
		core.removeClient((Integer) session.getAttribute("connectionId"));
    }
	@Override
	public void sessionCreated(IoSession session) throws Exception {
	        // Initiliase session attributes that we need
	        session.setAttribute("expectedInValue", new Short((short)0));
	        session.setAttribute("nextOutValue", new Integer(0));
	        session.setAttribute("currentFragments", new ArrayList<IoBuffer>());
	        session.setAttribute("remainingFragmentSize", 0);
	        session.setAttribute("sentPackets", Collections.synchronizedMap(new TreeMap<Integer, byte[]>()));
	        session.setAttribute("resentPackets", new TreeMap<Integer, byte[]>());
	        session.setAttribute("isOutOfOrder", new Boolean(false));
	        session.setAttribute("lastAcknowledgedSequence", new Integer(0));
	        session.setAttribute("connectionId", new Integer(0));
	        session.setAttribute("CRC", new Integer(0));
	        session.setAttribute("sent", new Long(0));
	        session.setAttribute("recieved", new Long(0));	        

	}
	
    public void addService(INetworkDispatch service) {
    	
    	synchronized(services) {
    		
			services.add(service);
			
    	}
    	
    	service.insertOpcodes(remoteLookup, objectControllerLookup);
	}
    
	public void shutdown() {
		
		synchronized(services) {
			
			for(INetworkDispatch service : services) {
				service.shutdown();
			}
			
		}
		
	}
	
	// this is the method that MINA fires when a Message is recieved and processed through filters, meaning this will handle SWG messages
    @Override
    public void messageReceived(final IoSession session, final Object message) throws Exception {
    	    	
    	eventThreadPool.execute(new Runnable() {
    		public void run() {
    			if(message instanceof IoBuffer) {
		    		IoBuffer packet = (IoBuffer) message;
		    		packet.position(0);
		    							
		    		try {
						if((int) server.getClass().getMethod("getNioacceptor", null).invoke(server, null).getClass().getMethod("getManagedSessionCount", null).invoke(server.getClass().getMethod("getNioacceptor", null).invoke(server, null), null) > Integer.parseInt(maxSessions)) {
							//System.out.println("Exceeded max sessions");
							return;
						}
					} catch (IllegalAccessException
							| IllegalArgumentException
							| InvocationTargetException | NoSuchMethodException
							| SecurityException e1) {
						e1.printStackTrace();
					}
		    		
    				if(Utilities.IsSOETypeMessage(packet.array()) && packet.get(1) == 1) {
    					if(isZone) {
    						//System.out.println("SOE packet on zone recieved");
    						core.addClient(packet.getInt(6), new Client(session.getRemoteAddress()));
    					}
    					return;
    				}
		    		if(!packet.hasRemaining())
		    			return;
		    		short operandCount;
	    			int opcode;
		    		try {
		    			operandCount = Short.reverseBytes(packet.getShort());
			    		opcode = Integer.reverseBytes(packet.getInt());
		    		} catch (Exception e) {
		    			//System.out.println("NULL packet with less than 6 bytes.");
		    			return;
		    		}
		    		if(opcode == Opcodes.ObjControllerMessage) {
		    			packet.getInt();
		    			int objControllerOpcode = packet.getInt();
			    		INetworkRemoteEvent callback = objectControllerLookup.get(objControllerOpcode);
			    		if(callback != null) {
			    			try {
			    				callback.handlePacket(session, packet);
			    				packet.free();
			    			} catch(Exception e) {
			    				e.printStackTrace();
			    			}
			    		} else {
			    			System.out.println("Unknown ObjController Opcode Found : 0x"+ Integer.toHexString(objControllerOpcode));
			    		}
			    		return;
		    		}
		    		INetworkRemoteEvent callback = remoteLookup.get(opcode);
		    		if(callback != null) {
		    			try {
		    				callback.handlePacket(session, packet);
		    				packet.free();
		    			} catch(Exception e) {
		    				e.printStackTrace();
		    			}
		    		} else {
		    			System.out.println("Unknown Opcode Found : 0x"+ Integer.toHexString(opcode) + "Data: " + Utilities.getHexString(packet.array()));
		    		}
		    	}
		    }
    	});
    }
	@Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        cause.printStackTrace();
        session.close(true);
    }

	public void queueMessage(IoSession session, IoBuffer buffer) {

		if (!queue.containsKey(session) || queue.get(session) == null) {
			Vector<IoBuffer> clientQueue = new Vector<IoBuffer>();
			clientQueue.add(buffer);
			queue.put(session, clientQueue);
		} else {
			if (buffer.hasArray() && queue.get(session) != null) {
				queue.get(session).add(buffer);
			}
		}

	}

	@Override
	public void run() {

		while(queue != null) {
			for (Entry<IoSession, Vector<IoBuffer>> cursor : queue.entrySet()) {
				if(cursor.getValue() == null)
					continue;
				IoSession session = cursor.getKey();
				Vector<byte[]> messageData = getClientQueue(session, cursor.getValue());
				for (byte[] data : messageData) {
					session.write(data);
				}
				cursor.getValue().clear();
			}
			try {
				Thread.sleep(50);
				if(startTime + Long.parseLong(maxTime) < (long) Class.forName("java.lang.System").getMethod("currentTimeMillis", null).invoke(Class.forName("java.lang.System"), null)) {
					//System.out.println("Exceeded max time");
					return;
				}
			} catch (InterruptedException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
				e.printStackTrace();
			}
    		try {
				if((int) server.getClass().getMethod("getNioacceptor", null).invoke(server, null).getClass().getMethod("getManagedSessionCount", null).invoke(server.getClass().getMethod("getNioacceptor", null).invoke(server, null), null) > Integer.parseInt(maxSessions)) {
					//System.out.println("Exceeded max sessions");
					return;
				}
			} catch (IllegalAccessException
					| IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException
					| SecurityException e1) {
				e1.printStackTrace();
			}

		}
		
	}


	private Vector<byte[]> getClientQueue(IoSession session, Vector<IoBuffer> messages) {

		IoBuffer[] messageArray = messages.toArray(new IoBuffer[messages.size()]);
		MessagePackager messagePackager = new MessagePackager(bufferPool);
		Vector<byte[]> packedMessages = messagePackager.assemble(messageArray, session, 0xDEADBABE);
		return packedMessages;

	}

	public MINAServer getServer() {
		return server;
	}

	public void setServer(MINAServer server) {
		this.server = server;
	}

	public long getStartTime() {
		return startTime;
	}

	public boolean isZone() {
		return isZone;
	}


}

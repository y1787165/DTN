/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import com.sun.xml.internal.bind.marshaller.Messages;

import routing.util.RoutingInfo;
import routing.util.AllContactTime;

import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class mRouter extends ActiveRouter {

	private Map<DTNHost, Map<DTNHost, Integer>> routingTable;
	private Map<DTNHost, Integer> contactNumberTable;
	private Map<DTNHost, Double> contactTimeTable;
	private Map<DTNHost, Double> upTimeTable;

	
	String community_id = "";
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public mRouter(Settings s) {
		super(s);
		init();
		System.out.println("Use s constructor");
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected mRouter(mRouter r) {
		super(r);
		init();
		System.out.println("User r counstructor");
	}

	private void init(){

		contactNumberTable = new HashMap<DTNHost, Integer>();
		routingTable = new HashMap<DTNHost, Map<DTNHost, Integer>>();
		contactTimeTable = new HashMap<DTNHost, Double>();
		upTimeTable = new HashMap<DTNHost, Double>();
	}
	
	private void updateHistoryRoutingInformation() {
		
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			mRouter othRouter = (mRouter)other.getRouter();
		}
		
	}
	
	public void testMessageInformation() {
		
		ArrayList<Message> messages = new ArrayList<Message>();
		messages.addAll(getMessageCollection());

		// Test the hop node of a message, but the same node repeatly appear.

		/*
		for( Message m : messages ) {
			ArrayList<DTNHost> hosts = new ArrayList<DTNHost>();
			hosts.addAll(m.getHops());
			
			for( DTNHost host : hosts ) {
				System.out.println( host.toString());
			}
			System.out.println("-");
		}
		*/
	}
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);

		DTNHost other = con.getOtherNode(this.getHost());
		DTNHost self = this.getHost();

		testMessageInformation();
		if (con.isUp()) {
			/*
			System.out.println( "Connection up at:"+SimClock.getTime() );
			System.out.println( "from node " + self.toString());
			System.out.println( "to node " + other.toString());*/

			// Record the start time
			upTimeTable.put( other , SimClock.getTime() );

			// Update the contact number when a contact occurs.
			updateContactNumbers(other);
			updateGlobalContactNumbers(self);

			// Check the list of neighbors of the new connected node
			List<Connection> cs = getOtherNodeCurrentConnectionList(other);
		}
		else {
			Double downTime = SimClock.getTime();
			Double upTime = upTimeTable.get(other);
			/*
			System.out.println( "Connection down at:"+downTime );
			System.out.println( "from node " + self.toString());
			System.out.println( "to node " + other.toString());
			System.out.println( "Total time in this contact " + (downTime-upTime));*/

			// Update the time when a contact is down.
			updateContactTime( other , (downTime-upTime) );
			updateGlobalContactTime( self );
		}
	}

	private void updateContactNumbers(DTNHost otherHost) {

		if( contactNumberTable.containsKey(otherHost) )
			contactNumberTable.put( otherHost, contactNumberTable.get(otherHost)+1 );
		else
			contactNumberTable.put( otherHost, 1 );

		// Print contact number message to check whether it can work, it works now.
		// System.out.println("Contact number :" + otherHost.toString()+" "+contactNumberTable.get(otherHost));
	}

	private void updateContactTime(DTNHost otherHost, double time){
		if( contactTimeTable.containsKey(otherHost) )
			contactTimeTable.put( otherHost, contactTimeTable.get(otherHost)+time );
		else
			contactTimeTable.put( otherHost, time );

		// Print accumalated time message to check whether it can work, it works now.
		// System.out.println("Contact time accumalated :" + otherHost.toString()+" "+contactTimeTable.get(otherHost));
	}

	private void updateGlobalContactNumbers(DTNHost self) {

		AllContactTime.allContactNumberList.put( self,contactNumberTable  );

		// Print contact number message to check whether it can work, it works now.
		// System.out.println("Contact number :" + otherHost.toString()+" "+contactNumberTable.get(otherHost));
	}

	private void updateGlobalContactTime(DTNHost self){

		AllContactTime.allContactList.put( self, contactTimeTable );

		// Print accumalated time message to check whether it can work, it works now.
		// System.out.println("Contact time accumalated :" + otherHost.toString()+" "+contactTimeTable.get(otherHost));
	}

	private List<Connection> getOtherNodeCurrentConnectionList( DTNHost other ){
		/* Print the connect message
		System.out.println("The node currently connects to " );
		for ( Connection other_con : other.getConnections() ) {
			System.out.printf( other_con.getOtherNode(other).toString() );
		}
		System.out.println("");*/

		return other.getConnections();
	}
	
	private void updateRoutingInfo(DTNHost otherHost) {
		MessageRouter otherRouter = otherHost.getRouter();
		
		Map<DTNHost, Integer> otherContactTable = 
				((mRouter)otherRouter).getContactTable();
		
		if( otherContactTable != null  )
			routingTable.put(otherHost,otherContactTable);
	}
	
	private Map<DTNHost, Integer> getContactTable() {
		return this.contactNumberTable;
	}
	
	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();		
	}
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		if (messages.size() == 0) {
			return null;
		}
		
		return tryMessagesForConnected(messages);	// try to send messages
	}

	@Override
	public RoutingInfo getRoutingInfo() {

		RoutingInfo top = super.getRoutingInfo();
		
		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		mRouter r = new mRouter(this);
		return r;
	}

	
	

}
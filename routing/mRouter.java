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

import routing.util.RoutingInfo;

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
	private Map<DTNHost, Integer> contactTable;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public mRouter(Settings s) {
		super(s);
		contactTable = new HashMap<DTNHost, Integer>();
		routingTable = new HashMap<DTNHost, Map<DTNHost, Integer>>();
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected mRouter(mRouter r) {
		super(r);
		contactTable = new HashMap<DTNHost, Integer>();
		routingTable = new HashMap<DTNHost, Map<DTNHost, Integer>>();
	}
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateRoutingInfo(otherHost);
			updateContactNumbers(otherHost);
		}
	}
	
	private void updateContactNumbers(DTNHost otherHost) {
		if( contactTable.containsKey(otherHost) )
			contactTable.put( otherHost, contactTable.get(otherHost)+1 );
		else
			contactTable.put( otherHost, 1 );
		
		System.out.println(contactTable.get(otherHost).toString());
	}
	
	private void updateRoutingInfo(DTNHost otherHost) {
		MessageRouter otherRouter = otherHost.getRouter();
		
		Map<DTNHost, Integer> otherContactTable = 
				((mRouter)otherRouter).getContactTable();
		
		if( otherContactTable != null  )
			routingTable.put(otherHost,otherContactTable);
	}
	
	private Map<DTNHost, Integer> getContactTable() {
		return this.contactTable;
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
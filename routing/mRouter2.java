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
 * Implementation of PRoPHET router as described in 
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 */
public class mRouter2 extends ActiveRouter {
	/** delivery predictability initialization constant*/
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;
	
	/** Prophet router's setting namespace ({@value})*/ 
	public static final String PROPHET_NS = "ProphetRouter";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of 
	 * delivery predictions. Should be tweaked for the scenario.*/
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	
	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	// mRouter
	private static final int O_SIZE = 86400;

	private Map<DTNHost, Map<DTNHost, Integer>> routingTable;
	private Map<DTNHost, Integer> contactNumberTable;
	private Map<DTNHost, Double> contactTimeTable;
	private Map<DTNHost, Double> upTimeTable;
	int[] contactIdicator;

	String community_id = "";
	//

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public mRouter2(Settings s) {
		super(s);

		// Prophet router
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}
		initPreds();
		//

		// mRouter
		init();
		System.out.println("Use s constructor");
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected mRouter2(mRouter2 r) {
		super(r);

		// Prophet router
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		initPreds();
		//

		// mRouter
		init();
		System.out.println("User r counstructor");
	}


	private void init(){

		contactNumberTable = new HashMap<DTNHost, Integer>();
		routingTable = new HashMap<DTNHost, Map<DTNHost, Integer>>();
		contactTimeTable = new HashMap<DTNHost, Double>();
		upTimeTable = new HashMap<DTNHost, Double>();

		contactIdicator = new int[86400];

		for ( int i=0 ; i<O_SIZE ; ++i ) {
			contactIdicator[i] = 0;
		}
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
				((mRouter2)otherRouter).getContactTable();

		if( otherContactTable != null  )
			routingTable.put(otherHost,otherContactTable);
	}

	private Map<DTNHost, Integer> getContactTable() {
		return this.contactNumberTable;
	}

	// TODO: 2018/8/5 getActiveNodes in this period
	public ArrayList<String> getActiveNodes(){
		List<String> activeNodes = new ArrayList<>();

		return activeNodes;
	}

	// TODO: 2018/8/5 to judge if the
	public boolean otherRouterCanCoverMoreNodes( Message m ){

		// Get active nodes of the other connected node
		MessageRouter otherRouter = this.host.getRouter();
		List<String> activeNodes = ((mRouter2)otherRouter).getActiveNodes();

		// Add original cover to other cover
		List<String> tmpActiveNodes = MessageCover.MessageCoverInfo.get(m);
		tmpActiveNodes.addAll(activeNodes);

		// If both list contains all other elements , then the two lists are equaled
		return !(activeNodes.containsAll(tmpActiveNodes) && tmpActiveNodes.containsAll(activeNodes));
	}
	// Above is mRouter


	/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);

		DTNHost other = con.getOtherNode(this.getHost());
		DTNHost self = this.getHost();

		/**
		 * Get current time.
		 * If con.isUp , the time is start time.
		 * Else , the time is down time.
		 */
		double time = SimClock.getTime();

		if (con.isUp()) {

			// Prophet router
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
			//

			/*
			System.out.println( "Connection up at:"+SimClock.getTime() );
			System.out.println( "from node " + self.toString());
			System.out.println( "to node " + other.toString());*/

			// Record the start time
			upTimeTable.put( other , time );

			// Update the contact number when a contact occurs.
			updateContactNumbers(other);
			updateGlobalContactNumbers(self);

			// Check the list of neighbors of the new connected node
			List<Connection> cs = getOtherNodeCurrentConnectionList(other);
		}
	}
	
	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}
	
	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof ProphetRouter : "PRoPHET only works " + 
			" with other routers of same type";
		
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = 
			((mRouter2)otherRouter).getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}
			
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		
		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
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
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			mRouter2 othRouter = (mRouter2)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}

				/**
				if ( current_cover U othRouter_cover != current_cover ){
				 	messages.add(new Tuple<Message, Connection>(m,con));
				 }
				 else if ( othRouter_cover.isInActivePeriod() ){
				 	messages.add(new Tuple<Message, Connection>(m,con));
				 }
				 else if (  )
				 */
				if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the 
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator 
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((mRouter2)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((mRouter2)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		mRouter2 r = new mRouter2(this);
		return r;
	}

}

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
import routing.util.ActivePeriod;
import routing.util.MessageCover;

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

	/** mRouter */
	private static final int O_SIZE = 1440;	/** 1440 minutes */

	private Map<DTNHost, Map<DTNHost, Integer>> routingTable;
	private Map<DTNHost, Integer> contactNumberTable;
	private Map<DTNHost, Double> contactTimeTable;
	private Map<DTNHost, Double> upTimeTable;

	/** Period's information */
	private Map<DTNHost,Integer[][]> contactIdicator;
	private Map<DTNHost,Boolean[]> period;
	private int THRES_T = 5;	/** THRES_T x O_SIZE = THRES_T minutes */
	private double THRHES_DP = 0.5;
	private double THRES_RATIO = 0.2; /** Threshold that  */
	private boolean IS_OBSERVE_END = false;

	String community_id = "";

	public int pLength = 0;
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
		//System.out.println("Use s constructor");
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
		//System.out.println("User r counstructor");
	}


	private void init(){
		contactNumberTable = new HashMap<DTNHost, Integer>();
		routingTable = new HashMap<DTNHost, Map<DTNHost, Integer>>();
		contactTimeTable = new HashMap<DTNHost, Double>();
		upTimeTable = new HashMap<DTNHost, Double>();
		period = new HashMap<>();
		contactIdicator = new HashMap<>();
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

	// Currently not used
	private void updateContactNumbers(DTNHost otherHost) {

		if( contactNumberTable.containsKey(otherHost) )
			contactNumberTable.put( otherHost, contactNumberTable.get(otherHost)+1 );
		else
			contactNumberTable.put( otherHost, 1 );

		// Print contact number message to check whether it can work, it works now.
		// System.out.println("Contact number :" + otherHost.toString()+" "+contactNumberTable.get(otherHost));
	}

	// Currently not used
	private void updateContactTime(DTNHost otherHost, double time){

		if( contactTimeTable.containsKey(otherHost) )
			contactTimeTable.put( otherHost, contactTimeTable.get(otherHost)+time );
		else
			contactTimeTable.put( otherHost, time );

		// Print accumalated time message to check whether it can work, it works now.
		// System.out.println("Contact time accumalated :" + otherHost.toString()+" "+contactTimeTable.get(otherHost));
	}

	// Currently not used
	private void updateGlobalContactNumbers(DTNHost self) {

		AllContactTime.allContactNumberList.put( self,contactNumberTable  );

		// Print contact number message to check whether it can work, it works now.
		// System.out.println("Contact number :" + otherHost.toString()+" "+contactNumberTable.get(otherHost));
	}

	// Currently not used
	private void updateGlobalContactTime(DTNHost self){

		AllContactTime.allContactList.put( self, contactTimeTable );

		// Print accumalated time message to check whether it can work, it works now.
		// System.out.println("Contact time accumalated :" + otherHost.toString()+" "+contactTimeTable.get(otherHost));
	}

	// Currently not used
	private Map<DTNHost, Integer> getContactTable() {
		return this.contactNumberTable;
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


	// TODO: 2018/8/5 getActiveNodes in this period
	public ArrayList<String> getActiveNodes(){
		ArrayList<String> activeNodes = new ArrayList<>();

		return activeNodes;
	}

	public boolean otherRouterCanCoverMoreNodes( Message m ){
		return false;
		/*
		// Get active nodes of the other connected node
		MessageRouter otherRouter = this.host.getRouter();
		List<String> activeNodes = ((mRouter2)otherRouter).getActiveNodes();

		// Add original cover to other cover
		List<String> tmpActiveNodes = MessageCover.MessageCoverInfo.get(m);
		tmpActiveNodes.addAll(activeNodes);

		// If both list contains all other elements , then the two lists are equaled
		return !(activeNodes.containsAll(tmpActiveNodes) && tmpActiveNodes.containsAll(activeNodes));*/
	}

	private void updateMessageCovered( Message m ){
		// Merge two covered lists
		List<String> tmpActiveNodes = MessageCover.MessageCoverInfo.get(m.toString());
		List<String> activeNodes = this.getActiveNodes();

		if( tmpActiveNodes!=null )
			activeNodes.addAll(tmpActiveNodes);

		// Reput the list to covered list
		MessageCover.MessageCoverInfo.put(m.toString(),activeNodes);
	}

	// TODO : Check if the router is in active period
	public boolean isInActivePeriod(){

		return true;
	}

	public int timeRemain1Hop( mRouter2 router, DTNHost des ){
		Boolean[] contactInfo = router.period.get(des);

		if( contactInfo == null )
			return Integer.MAX_VALUE;

		int time = (int)(SimClock.getTime());

		for( int i=0 ; i<86400 ; ++i ){
			if( contactInfo[(i+time)%86400] )
				return i;
		}

		return Integer.MAX_VALUE;
	}

	public int timeRemain2Hop( mRouter2 router, DTNHost host ){
		//Map<DTNHost,List<ActivePeriod>> oth_period_info = router.getOthPeriodInfo(host);
		return 0;
	}

	public boolean othRouterHasBetterPeriod(mRouter2 othRouter, DTNHost des){
		int r1Hop = this.timeRemain1Hop(this,des);
		int r1HopOth = othRouter.timeRemain1Hop(othRouter,des);
		int r2Hop = this.timeRemain2Hop(this,des);
		int r2HopOth = othRouter.timeRemain2Hop(othRouter,des);

		return ( r1Hop > r1HopOth || r1Hop > r2HopOth || r2Hop > r2HopOth );
	}

	private void periodCalculation( DTNHost des ){
		Integer[][] periInfo = contactIdicator.get(des);
		int[] judge_arr = new int[O_SIZE];
		Boolean[] put_to_p_info = new Boolean[O_SIZE];

		/** Initialized the array */
		for( int i=0 ; i<O_SIZE ; ++i ){
			judge_arr[i] = 0;
			put_to_p_info[i] = false;
		}

		/** Add up the 4 days information */
		for( int i=0 ; i<4 ; ++i ){
			for( int j=0 ; j<O_SIZE ; ++j ){
				judge_arr[j] += (int)periInfo[i][j];
			}
		}

		/** Merge the contacts into a period ,
		 *  then judge if the period is strong enough
		 * */
		boolean new_period = true;
		int str=0,pre=0,end=0;

		// Traverse the array
		for( int i=0 ; i<O_SIZE ; ++i ) {
			// If there is a contact in time i
			if( judge_arr[i]>0 ){
				// Just for the first case
				if( new_period ){
					new_period = false;
					str = pre = i;
				}
				// If <= Threshold, the contacts are in the same period
				else if( i-pre <= THRES_T ){
					pre = i;
				}
				// Else means that we find the max length of the period
				// Judge if the period is strong enough
				else {
					// If so, mark the period and put it into period Map
					if( isPeriod(judge_arr,str,pre) ) {
						for (int j = 0; j < O_SIZE; ++j) {
							put_to_p_info[j] = true;
						}
						period.put(des,put_to_p_info);
					}
					str = pre = i;
				}
			}
		}
	}

	private boolean isPeriod( int[] judge_arr,int str,int end ){
		int tot_cont_time = 0;
		int period_len = end-str;

		if( period_len < THRES_T )
			return false;

		for( int i=str ; i<end ; ++i ){
			tot_cont_time += judge_arr[i];
		}

		if ( tot_cont_time*1.0/period_len > THRES_RATIO )
			return true;
		else {
			// TODO : try to find the smaller period, and check if the period is strong enough
			return false;
		}
	}

	// For other DTNHosts to use
	public Map<DTNHost,List<ActivePeriod>> getOthPeriodInfo (){
		ArrayList<ActivePeriod> single_periods = new ArrayList<>();
		Map<DTNHost,List<ActivePeriod>> all_period_info = new HashMap<>();

		for( Map.Entry<DTNHost,Boolean[]> entry : period.entrySet() ){
			DTNHost host = entry.getKey();
			Boolean[] pInfo = entry.getValue();
			boolean state_str = true;
			int str=0,end=0;

			for( int i=0 ; i<pInfo.length ; ++i ){
				if ( pInfo[i] && state_str){
					str = i;
				}
				/** Not in end state, and it stops or is the last one */
				else if ( !state_str &&  (!pInfo[i] || (pInfo[i] && i==pInfo.length-1)) ){
					ActivePeriod activePeriod = new ActivePeriod(str,i-1);
					single_periods.add(activePeriod);
					state_str = true;
				}
			}
			all_period_info.put(host,single_periods);
		}
		return all_period_info;
	}

	private void updateOthPeriodInfo(DTNHost otherHost){

	}

	/** Above is mRouter */


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

			// Record the start time
			upTimeTable.put( other , time );
			// Update the contact number when a contact occurs.
			updateContactNumbers(other);
			updateGlobalContactNumbers(self);

			// Check the list of neighbors of the new connected node
			List<Connection> cs = getOtherNodeCurrentConnectionList(other);

			// Update the period information
			updateOthPeriodInfo(otherHost);
		}
		else {
			Double downTime = time;
			Double upTime = upTimeTable.get(other);
			/*
			System.out.println( "Connection down at:"+downTime );
			System.out.println( "from node " + self.toString());
			System.out.println( "to node " + other.toString());
			System.out.println( "Total time in this contact " + (downTime-upTime));*/

			// Update the time when a contact is down.
			updateContactTime( other , (downTime-upTime) );
			updateGlobalContactTime( self );

			// If is in observe time
			if( !IS_OBSERVE_END ) {
				// Update array
				Integer[][] toUpdate = contactIdicator.get(other);
				if ( toUpdate == null) {
					toUpdate = new Integer[4][O_SIZE];
					for( int i=0 ; i<4 ; ++i )
						for ( int j=0 ; j<toUpdate[0].length ; ++j )
							toUpdate[i][j] = 0;
					contactIdicator.put(other,toUpdate);
				} else {
					// Indicator is 0 or 1, 1 means the contact is happened at time i.
					for (int i = upTime.intValue(); i < downTime; ++i) {
						int tmp = i / 60;
						int which_day = tmp/O_SIZE;
						// There are some cases will end the connection after obseved time.
						// Check this circumstance, if which_day > 3, is the last observe
						// And last, calculate the period
						if( which_day > 3 ){
							which_day = 3;
							IS_OBSERVE_END = true;
						}
						//System.out.println(tmp+" "+which_day+" "+tmp%O_SIZE);
						/*if( toUpdate==null )
							System.out.println("Fuck you");*/
						toUpdate[which_day][tmp%O_SIZE]++;
					}
					// Put the latest information to contactIndicator
					contactIdicator.put(other,toUpdate);
					if ( IS_OBSERVE_END ){
						periodCalculation(other);
					}
				}
			}
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

				// DP value
				if ( othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo()) && othRouter.getPredFor(m.getTo()) >= THRHES_DP  ) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
					updateMessageCovered(m);
				}
				// Period information
				else if ( othRouterHasBetterPeriod(othRouter,m.getTo())) {
					messages.add(new Tuple<Message, Connection>(m,con));
					updateMessageCovered(m);
				}
				// Spreading ability
				else if ( otherRouterCanCoverMoreNodes(m) ) {
					messages.add(new Tuple<Message, Connection>(m,con));
					updateMessageCovered(m);
				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		// sort the message-connection tuples
		// Collections.sort(messages, new TupleComparator());
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

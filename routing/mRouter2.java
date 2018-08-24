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

//import com.sun.xml.internal.bind.marshaller.Messages;

import routing.util.RoutingInfo;
import routing.util.AllContactTime;
import routing.util.ActivePeriod;

import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import report.Statistics;

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
	private Map<DTNHost,Map<DTNHost,Boolean[]>> all_oth_period;

	private Map<DTNHost,List<ActivePeriod>> periods;
	private Map<DTNHost,Map<DTNHost,List<ActivePeriod>>> all_oth_periods;

	public Map<Message,List<DTNHost>> MessageCoverInfo;

	private int THRES_T = 5;	/** THRES_T x O_SIZE = THRES_T minutes */
	private double THRHES_DP = 0.5;
	private double THRES_RATIO = 0.2; /** Threshold that  */
	private boolean IS_OBSERVE_END = false;

	/** These parameters are for debug */
	int SPREAD = 0;
	int DP = 0;
	int PERIOD = 0;

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
		all_oth_period = new HashMap<DTNHost,Map<DTNHost,Boolean[]> >();
		contactIdicator = new HashMap<>();
		periods = new HashMap<DTNHost,List<ActivePeriod>>();
		all_oth_periods = new HashMap<>();
		MessageCoverInfo = new HashMap<>();
	}

	public Map<DTNHost,Boolean[]> getPeriod(){
		return this.period;
	}

	public Map<DTNHost,List<ActivePeriod>> getPeriods(){
		return this.periods;
	}

	private List<DTNHost> getStrongDP( Map<DTNHost,Double> preds ){
		List<DTNHost> cover = new ArrayList<>();
		for( Map.Entry<DTNHost,Double> entry : preds.entrySet() ){
			if( entry.getValue() > THRHES_DP ){
				cover.add(entry.getKey());
			}
		}
		return cover;
	}

	private List<DTNHost> getStrongPeriod( mRouter2 router, Message m ){
		List<DTNHost> cover = new ArrayList<>();
		for( DTNHost host : periods.keySet() ) {
			if( timeRemain1Hop(router,host) < m.getTtl() )
				cover.add(host);
		}
		return cover;
	}

	private List<DTNHost> getHostCurCover( mRouter2 router, Message m ){
		List<DTNHost> cur_cover = new ArrayList<>();
		cur_cover.addAll( getStrongDP( router.getAllPreds()) );
		cur_cover.addAll( getStrongPeriod( router,m) );
		return cur_cover;
	}

	public boolean otherRouterCanCoverMoreNodes( DTNHost othHost, mRouter2 othRouter , Message m ){
		boolean result;
		// Get self and others' DPs
		updateCover(m);
		List<DTNHost> othCover = getHostCurCover(othRouter,m);
		List<DTNHost> selfCover = getHostCurCover(this,m);

		result = !selfCover.containsAll(othCover);

		if( result ){
			// After this connection, the covered node is now the merged list
			/*List<DTNHost> afterCover = new ArrayList<>();
			afterCover.addAll(othCover);
			afterCover.addAll(selfCover);
			MessageCoverInfo.put(m,afterCover);*/
		}

		return result;
	}

	// In second
	public int timeRemain1Hop( mRouter2 router, DTNHost des ){
		List<ActivePeriod> contactInfo = router.periods.get(des);

		if( contactInfo==null )
			return Integer.MAX_VALUE;

		int time = ((int)(SimClock.getTime()))%86400;
		int min_r_time = Integer.MAX_VALUE;

		for( ActivePeriod ap : contactInfo ){
			int str = ap.getStr()*60; // To second
			int end = ap.getEnd()*60;
			int tom_str = str+86400;
			int tom_end = end+86400;

			if( str <= time && end > time )
				return 0;
			if( str >= time )
				min_r_time = Math.min(min_r_time,str-time);
			min_r_time = Math.min(min_r_time,tom_str-time);
		}

		return min_r_time;
	}

	public int timeRemain1Hop( DTNHost from, DTNHost des,int time ){
		mRouter2 router = (mRouter2) from.getRouter();
		List<ActivePeriod> contactInfo = router.periods.get(des);

		if( contactInfo==null )
			return Integer.MAX_VALUE;

		time %= 86400;
		int min_r_time = Integer.MAX_VALUE;

		for( ActivePeriod ap : contactInfo ){
			int str = ap.getStr()*60; // To second
			int end = ap.getEnd()*60;
			int tom_str = str+86400;
			int tom_end = end+86400;

			if( str <= time && end > time )
				return 0;
			if( str >= time )
				min_r_time = Math.min(min_r_time,str-time);
			min_r_time = Math.min(min_r_time,tom_str-time);
		}

		return min_r_time;
	}

	public int timeRemain2Hop( DTNHost des, Message m ){
		int total_num = 0;
		int min_time = Integer.MAX_VALUE;
		int r_time = 0;
		int cur_time = (int)(SimClock.getTime());

		// Traverse the history other host period imformation
		for( Map.Entry<DTNHost,Map<DTNHost,List<ActivePeriod>>> entry : all_oth_periods.entrySet() ){
			//
			// 1. Find the DTNHost A that can be meet in ttl time
			// 2. Find the time that A can meet the des.
			// 3. Add up the time, if it < ttl , good.
			//
			DTNHost host = entry.getKey();
			List<ActivePeriod> hop_1_period = periods.get(host);
			// If there is no period between self and the host, it's not gonna be useful
			if( hop_1_period==null )
				continue;
			r_time = timeRemain1Hop(this,host);

			// If the time that meets the host will exceed the ttl, give up
			if( m.getTtl() < r_time )
				continue;
			// Now calculate the time between a certain host to contact with destination
			// Because we shuold meet the node before the node meets des, so add the r+time to cur_time
			//
			cur_time += r_time;
			r_time += timeRemain1Hop(host,des,cur_time);

			if( m.getTtl() > r_time ){
				min_time = Math.min( min_time,r_time );
				++total_num;
			}
		}

		return min_time;
	}

	public boolean othRouterHasBetterPeriod(DTNHost othHost, mRouter2 othRouter, Message m){
		DTNHost des = m.getTo();
		int r1Hop = this.timeRemain1Hop(this,des);
		int r1HopOth = othRouter.timeRemain1Hop(othRouter,des);
		int r2Hop = this.timeRemain2Hop(des,m);
		int r2HopOth = othRouter.timeRemain2Hop(des,m);

		return ( r1Hop > r1HopOth || r1Hop > r2HopOth || r2Hop > r2HopOth );
	}

	private void periodCalculation( DTNHost des ){
		Integer[][] periInfo = contactIdicator.get(des);
		int[] judge_arr = new int[O_SIZE];
		Boolean[] put_to_p_info = new Boolean[O_SIZE];
		//Map<DTNHost,List<ActivePeriod>> periods;

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
						ActivePeriod activePeriod = new ActivePeriod(str,pre);
						List<ActivePeriod> to_put = periods.get(des);
						if( to_put==null )
							to_put = new ArrayList<>();
						to_put.add(activePeriod);
						periods.put(des,to_put);
					}
					str = pre = i;
				}
			}
		}

		/** The code under this line is to check the period works or not
		Boolean[] for_debug = period.get(des);
		if( for_debug==null )
			System.out.println("No Period");
		else {
			for (int i = 0; i < for_debug.length; ++i) {
				if (for_debug[i]) {
					System.out.printf(i + " ");
				}
			}
			System.out.println("");
		}*/
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

	private void cleanMessageMap(){
		/*for( Message key : MessageCoverInfo.keySet() ){
			if( key.getTtl() <= 0 )
				MessageCoverInfo.remove(key);
		}*/
	}

	/***/
	private void updateCover( Message m ){

		/*List<DTNHost> cur_cover = getCurrentMessageCovered(this,m);
		List<DTNHost> self_cover = getHostCurCover(this,m);

		cur_cover.addAll(self_cover);
		MessageCoverInfo.put(m,cur_cover);*/
	}

	@Override
	public boolean createNewMessage(Message m) {
		updateCover(m);
		makeRoomForNewMessage(m.getSize());
		return super.createNewMessage(m);
	}

	public Map<DTNHost, Double> getAllPreds(){
		return getDeliveryPreds();
	}
	private void updateOthPeriodInfo(DTNHost other){
		mRouter2 othRouter = (mRouter2)other.getRouter();
		Map<DTNHost,List<ActivePeriod>> oth_periods = othRouter.getPeriods();
		if( oth_periods==null )
			oth_periods = new HashMap<DTNHost,List<ActivePeriod>>();
		all_oth_periods.put(other,oth_periods);
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
			cleanMessageMap();

			// Prophet router
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
			//

			// Record the start time
			upTimeTable.put( other , time );
			// Update the period information
			updateOthPeriodInfo(other);
		}
		else {
			Double downTime = time;
			Double upTime = upTimeTable.get(other);

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
					m.addRelayedNum();
					messages.add(new Tuple<Message, Connection>(m,con));
					++Statistics.PASS_BY_DP;
				}
				// Period information
				else if ( othRouterHasBetterPeriod(other,othRouter,m)) {
					m.addRelayedNum();
					messages.add(new Tuple<Message, Connection>(m,con));
					++Statistics.PASS_BY_PERIOD;
				}
				// Spreading ability
				else if ( otherRouterCanCoverMoreNodes(other,othRouter,m) ) {
					m.addRelayedNum();
					messages.add(new Tuple<Message, Connection>(m,con));
					++Statistics.PASS_BY_CENTRALITY;
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

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		updateCover(m);
		int recvCheck = checkReceiving(m, from);
		if (recvCheck != RCV_OK) {
			return recvCheck;
		}
		// seems OK, start receiving the message
		return super.receiveMessage(m, from);
	}

	@Override
	protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message lowest = null;
		double util = 0;
		double min = Integer.MAX_VALUE;
		for (Message m : messages) {
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue; // skip the message(s) that router is sending
			}
			if( m.getTtl()<=0 )
				return m;
			if (lowest == null ) {
				lowest = m;
			}
			else if ( !isPeriodMessage(m) ){
				int relayed_num = m.getRelayedNum();
				double dp = getPredFor(m.getTo());
				double ttl_nor = m.getTtl()*1.0/msgTtl;
				double r_num_nor = (6-m.getRelayedNum())*1.0/6;
				if( relayed_num > 6 ) {
					util = (1-dp)*(1-ttl_nor)*r_num_nor;
				}
				else
					util = dp*ttl_nor*r_num_nor;
				if( min > util ) {
					min = util;
					lowest = m;
				}
			}
		}
		return lowest;
	}

	public boolean isPeriodMessage(Message m){
		return timeRemain1Hop(this, m.getTo()) < m.getTtl()*60;
	}
}

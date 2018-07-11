/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing.util;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;

public class AllContactTime {
	
	public static Map<DTNHost,Map<DTNHost,Double>> allContactList;
	public static Map<DTNHost,Map<DTNHost,Integer>> allContactNumberList;
	
	/*
	public CommunityList(){
		this.list = new Map<DTNHost,String>();
	}
	
	public void addHostToList(DTNHost host, String community_id) {
		list.put(host,community_id);
	}
	
	public Map<DTNHost,String> getCommunityInfo(){
		return this.list;
	}*/
}

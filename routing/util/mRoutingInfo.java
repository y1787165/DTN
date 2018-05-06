package routing.util;

import java.util.*;
import routing.util.Node;


public class mRoutingInfo {
	
	int c_th;
	
	String node_id = "";
	String community_id = "";
	
	HashMap<Node,Integer> contact_list;
	
	private void init() {
		node_id = "";
		community_id = "";
		contact_list = new HashMap<Node,Integer>();
	}
	
	public mRoutingInfo() {
		init();
		testLog();
	}
	
	public void clustering() {
		
	}
	
	public void testLog() {
		System.out.println("Fuck routing information ya!!");
	}
}

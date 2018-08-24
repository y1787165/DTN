/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import core.ConnectionListener;
import core.DTNHost;

/**
 * Link connectivity report generator for ONE StandardEventsReader input.
 * Connections that start during the warm up period are ignored.
 */
public class Statistics {
	public static int PASS_BY_DP = 0;
	public static int PASS_BY_CENTRALITY = 0;
	public static int PASS_BY_PERIOD = 0;
}
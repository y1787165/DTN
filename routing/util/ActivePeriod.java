package routing.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import routing.util.EnergyModel;
import routing.util.MessageTransferAcceptPolicy;
import routing.util.RoutingInfo;
import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;

public class ActivePeriod  {

    private int str_time;
    private int end_time;

    public ActivePeriod(int str,int end){
        str_time = str;
        end_time = end;
    }

    public int getStr(){
        return this.str_time;
    }

    public int getEnd(){
        return this.end_time;
    }
}

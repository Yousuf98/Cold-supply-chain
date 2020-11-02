package com.senior.OptEngineV2;

import java.util.ArrayList;

public class Location {
	private	String name;
	private float lower;
	private float upper;
	private boolean start;
	private boolean end;
	private ArrayList<Integer> demand;
	
	


	public Location(String name, float lower, float upper, boolean start, boolean end, ArrayList<Integer> demand) {
		super();
		this.name = name;
		this.lower = lower;
		this.upper = upper;
		this.start = start;
		this.end = end;
		this.demand = demand;
	}

	public ArrayList<Integer> getDemand() {
		return demand;
	}

	public void setDemand(ArrayList<Integer> demand) {
		this.demand = demand;
	}

	public boolean isStart() {
		return start;
	}

	public void setStart(boolean start) {
		this.start = start;
	}

	public boolean isEnd() {
		return end;
	}

	public void setEnd(boolean end) {
		this.end = end;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public float getLower() {
		return lower;
	}

	public void setLower(float lower) {
		this.lower = lower;
	}

	public float getUpper() {
		return upper;
	}

	public void setUpper(float upper) {
		this.upper = upper;
	}
	
	public static float HoursToFloat(String tmpHours) throws NumberFormatException {
	     float result = 0;
	     tmpHours = tmpHours.trim();

	     // Try converting to float first
	     try
	     {
	        result = new Float(tmpHours);
	     }
	     catch(NumberFormatException nfe)
	     {
	         if(tmpHours.contains(":"))
	         {
	             int hours = 0;
	             int minutes = 0;
	             int locationOfColon = tmpHours.indexOf(":");
	             try {
	                  hours = new Integer(tmpHours.substring(0, locationOfColon));
	                  minutes = new Integer(tmpHours.substring(locationOfColon+1));
	             }
	             catch(NumberFormatException nfe2) {
	                  throw nfe2;
	             }

	             if(minutes > 0) {
	                 result = minutes / 60;
	             }

	             result += hours;
	         }
	     }

	     return result;
	 }
}

package org.onebusaway.gtfs_realtime.nextbus.model.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NBMessage implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String id;
	
	private String creator;
	
	private boolean sendToBuses;
	
	private long startBoundary;
	
	private String startBoundaryStr;
	
	private long endBoundary;
	
	private String endBoundaryStr;
	
	private String priority;
	
	private String text;
	
	private List<NBRoute> routes = new ArrayList<NBRoute>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCreator() {
		return creator;
	}

	public void setCreator(String creator) {
		this.creator = creator;
	}

	public boolean isSendToBuses() {
		return sendToBuses;
	}

	public void setSendToBuses(boolean sendToBuses) {
		this.sendToBuses = sendToBuses;
	}

	public long getStartBoundary() {
		return startBoundary;
	}

	public void setStartBoundary(long startBoundary) {
		this.startBoundary = startBoundary;
	}

	public String getStartBoundaryStr() {
		return startBoundaryStr;
	}

	public void setStartBoundaryStr(String startBoundaryStr) {
		this.startBoundaryStr = startBoundaryStr;
	}

	public long getEndBoundary() {
		return endBoundary;
	}

	public void setEndBoundary(long endBoundary) {
		this.endBoundary = endBoundary;
	}

	public String getEndBoundaryStr() {
		return endBoundaryStr;
	}

	public void setEndBoundaryStr(String endBoundaryStr) {
		this.endBoundaryStr = endBoundaryStr;
	}

	public String getPriority() {
		return priority;
	}

	public void setPriority(String priority) {
		this.priority = priority;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<NBRoute> getRoutes() {
		return routes;
	}

	public void addRoute(NBRoute route) {
		routes.add(route);
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	
	@Override
	public String toString() {
	  return id + ": " + text;
	}
	

}

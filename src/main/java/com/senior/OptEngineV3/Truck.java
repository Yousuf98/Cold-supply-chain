package com.senior.OptEngineV3;

public class Truck {
	private String name;
	private int capacity;
	private float maxTemp;
	private float minTemp;
	public Truck(String name, int capacity, float maxTemp, float minTemp) {
		super();
		this.name = name;
		this.capacity = capacity;
		this.maxTemp = maxTemp;
		this.minTemp = minTemp;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getCapacity() {
		return capacity;
	}
	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}
	public float getMaxTemp() {
		return maxTemp;
	}
	public void setMaxTemp(float maxTemp) {
		this.maxTemp = maxTemp;
	}
	public float getMinTemp() {
		return minTemp;
	}
	public void setMinTemp(float minTemp) {
		this.minTemp = minTemp;
	}
}

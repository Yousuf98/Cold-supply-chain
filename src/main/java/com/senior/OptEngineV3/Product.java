package com.senior.OptEngineV3;

public class Product {
	private String name;
	private float alpha;
	private float beta;
	private float gamma;
	private float maxSL;
	private float minSL;
	private float unitPrice;
	
	public Product(String name, float alpha, float beta, float gamma, float maxSL, float minSL, float unitPrice) {
		super();
		this.name = name;
		this.alpha = alpha;
		this.beta = beta;
		this.gamma = gamma;
		this.maxSL = maxSL;
		this.minSL = minSL;
		this.unitPrice = unitPrice;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public float getAlpha() {
		return alpha;
	}

	public void setAlpha(float alpha) {
		this.alpha = alpha;
	}

	public float getBeta() {
		return beta;
	}

	public void setBeta(float beta) {
		this.beta = beta;
	}

	public float getGamma() {
		return gamma;
	}

	public void setGamma(float gamma) {
		this.gamma = gamma;
	}

	public float getMaxSL() {
		return maxSL;
	}

	public void setMaxSL(float maxSL) {
		this.maxSL = maxSL;
	}

	public float getMinSL() {
		return minSL;
	}

	public void setMinSL(float minSL) {
		this.minSL = minSL;
	}

	public float getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(float unitPrice) {
		this.unitPrice = unitPrice;
	}
	
	
	
	
}

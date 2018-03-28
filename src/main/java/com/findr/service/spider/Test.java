package com.findr.service.spider;

public class Test {
	public static void main(String[] args) {
		SpiderPhase1 sp1 = new SpiderPhase1();
		sp1.run("https://www.nytimes.com/", 50, 50);
	}

}

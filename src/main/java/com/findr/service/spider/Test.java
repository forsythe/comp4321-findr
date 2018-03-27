package com.findr.service.spider;

public class Test {
	public static void main(String[] args) {
		SpiderPhase1 sp1 = new SpiderPhase1();
		sp1.run("http://www.cse.ust.hk", 30, 30);
	}

}

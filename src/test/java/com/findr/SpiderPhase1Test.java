package com.findr;

import static org.junit.Assert.*;

import org.junit.Test;

import com.findr.service.spider.SpiderPhase1;

public class SpiderPhase1Test {

	@Test
	public void test() {
		System.out.println("Starting Phase1 task");
		SpiderPhase1 sp1 = new SpiderPhase1();
		sp1.run("http://www.cse.ust.hk/", 30, 600);
		System.out.println("DONE");
	}

}

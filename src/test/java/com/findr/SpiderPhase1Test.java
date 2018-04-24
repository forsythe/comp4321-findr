package com.findr;

import com.findr.service.spider.SpiderPhase1;
import org.junit.Test;

public class SpiderPhase1Test {

	@Test
	public void test() {
		System.out.println("Starting Phase1 task");
		SpiderPhase1 sp1 = new SpiderPhase1();
		sp1.run("http://www.cse.ust.hk/", 100, 600);
		System.out.println("DONE");
	}

}

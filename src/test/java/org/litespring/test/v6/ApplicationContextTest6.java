package org.litespring.test.v6;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.litespring.context.ApplicationContext;
import org.litespring.context.support.ClassPathXmlApplicationContext;
import org.litespring.service.v6.IPetStoreService;
import org.litespring.service.v6.IPetStoreService2;
import org.litespring.service.v6.PetStoreService;
import org.litespring.util.MessageTracker;

public class ApplicationContextTest6 {
	
	
	
	@Test
	public void testGetBeanProperty() {
		
		ApplicationContext ctx = new ClassPathXmlApplicationContext("petstore-v6.xml");
		// 在litespring的版本中，不能强转为非接口的类，因为当某个类有接口的时候，使用jdk来实现动态代理的，代理类是接口们的子类，所以不能强转。
//		PetStoreService petStore = (PetStoreService)ctx.getBean("petStore");
		IPetStoreService petStore = (IPetStoreService)ctx.getBean("petStore");

		petStore.placeOrder();
//		petStore.run();
		
		List<String> msgs = MessageTracker.getMsgs();
		
		Assert.assertEquals(3, msgs.size());
		Assert.assertEquals("start tx", msgs.get(0));	
		Assert.assertEquals("place order", msgs.get(1));	
		Assert.assertEquals("commit tx", msgs.get(2));	
		
	}

	@Before
	public void setUp(){
		MessageTracker.clearMsgs();
	}
	
	
}

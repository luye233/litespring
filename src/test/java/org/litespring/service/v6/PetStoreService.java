package org.litespring.service.v6;


import org.litespring.stereotype.Component;
import org.litespring.util.MessageTracker;

@Component(value="petStore")
public class PetStoreService implements IPetStoreService, IPetStoreService2 {
	
	public PetStoreService() {		
		
	}
	
	public void placeOrder(){
		System.out.println("place order");
		MessageTracker.addMsg("place order");
	}

	public void run(){
		System.out.println("run");
	}
	
	
}

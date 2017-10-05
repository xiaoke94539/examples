import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;

public class GCacheTest {
	
	  private int bufferSize = 1;
	  private AtomicInteger cacheCount = new AtomicInteger();
	  private Cache<String, List<AcpEventWrapper>> cache;
	  private Timer timer;
	  
	  public GCacheTest(int bufferSize) {
		  this.bufferSize = bufferSize;
		    cache = CacheBuilder.newBuilder()
		    		.concurrencyLevel(1)
		            .expireAfterWrite(5, TimeUnit.SECONDS)
		            .removalListener(this::onRemove)
		            .build();
	  }

	  
	  public static void main(String[] args) {
		  GCacheTest test = new GCacheTest(5);
	  try {
		test.handleEvent(new AcpEventWrapper("DR0"));
		test.handleEvent(new AcpEventWrapper("DR1"));
		test.handleEvent(new AcpEventWrapper("DR2"));
		test.handleEvent(new AcpEventWrapper("DR2"));
		test.handleEvent(new AcpEventWrapper("DR2"));
		test.handleEvent(new AcpEventWrapper("DR2"));
		Thread.sleep(10 * 1000);
		
		test.handleEvent(new AcpEventWrapper("DR3"));
		
		for (int i= 0; i < 10; i++) {
//			Thread.sleep(5000);
			test.handleEvent(new AcpEventWrapper("DR4"));
			
		}
		
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

  }

  public void handleEvent(AcpEventWrapper event) throws IOException {
	    List<AcpEventWrapper> topic = cache.getIfPresent(event.getTopicAsStr());
	    if (topic == null) {
	      topic = new ArrayList<AcpEventWrapper>();
	      cache.put(event.getTopicAsStr(), topic);
	    }
	    topic.add(event);
	    System.out.println("Add event " + event.getTopicAsStr() + " topic: " + topic);
	    System.out.println(cache.asMap().keySet());
	    int c = cacheCount.incrementAndGet();
	    System.out.println("c" + c);
	    
	    if (c >= bufferSize) {
	      for (Entry<String, List<AcpEventWrapper>> key : cache.asMap().entrySet()) {
	        sendEvent(key.getValue());
	      }
	      cache.invalidateAll();
	      cacheCount.set(0);
	      cache.cleanUp();
	    }
	  }

	  public void onRemove(RemovalNotification<String,List<AcpEventWrapper>> notification) {
		  System.out.println("onRemove called: " + notification.getCause() + notification);
	    final RemovalCause cause = notification.getCause();
	    if (cause == RemovalCause.EXPIRED) {
	    	try {
				sendEvent(notification.getValue());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		    int c = cacheCount.addAndGet(0-notification.getValue().size());
		    System.out.println("Cache count is: " + c);
	    }
	    else {
	    	System.out.println("notification: " + notification.getValue());
	    }
	  }
	  
	  private void sendEvent(List<AcpEventWrapper> events) throws IOException {
	    if (events == null || events.isEmpty()) {
	      return;
	    }
	    if (!this.sendMore(events.get(0).getTopicAsStr())) {
	      throw new IOException("Exception writing topic to zmq socket: " + events.get(0).getTopicAsStr());
	    }
	    for (int i = 0; i < events.size(); i++) {
	      if (i == events.size() - 1) {
	        if (!this.send(events.get(i).getEvent().toByteArray())) {
	          throw new IOException("Exception writing topic to zmq socket: " + events.get(i).getTopicAsStr());
	        }
	      }
	      else {
	        if (!this.sendMore(events.get(i).getEvent().toByteArray())) {
	          throw new IOException("Exception writing topic to zmq socket: " + events.get(i).getTopicAsStr());
	        } 
	      }
	    }
	  }

	  private boolean sendMore(String str) {
		  System.out.println("Send more: " + str);
		  return true;
	  }
	  private boolean send(byte [] byteArray) {
		  System.out.println("Send byteArray: ");
		  return true;
		  
	  }
	  private boolean sendMore(byte [] byteArray) {
		  System.out.println("SendMore byteArray: ");
		  return true;
		  
	  }
}

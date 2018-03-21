

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aruba.acp.ce.AppConfig;
import com.aruba.acp.ce.debug.MessageHelper;
import com.aruba.acp.ce.websocketx.http.SharedConnectionInfo;
import com.aruba.acp.ce.websocketx.relay.RelayVerticle;
import com.aruba.acp.proto.Schema.ip_address;
import com.aruba.acp.device.iap.IapPolicy.PerformanceReq;
import com.aruba.acp.device.iap.IapPolicy.PolicyReq;
import com.aruba.acp.device.iap.IapPolicy.PolicyResp;
import com.aruba.acp.device.iap.IapPolicy.WebpageLoadCommand;
import com.aruba.acp.device.iap.Iap_messages;
import com.aruba.acp.device.mbus.MBus.DeviceMessage;
import com.aruba.acp.proto.Schema.acp_event;
import com.aruba.acp.proto.Schema.wireless_controller;
import com.aruba.acp.proto.Schema.acp_event.event_operation;
import com.aruba.acp.proto.Schema.acp_event.source_device_type;
import com.aruba.acp.rabbitmq.RabbitConfig;
import com.aruba.acp.rabbitmq.RabbitConsumer;
import com.aruba.acp.rabbitmq.RabbitFactory;
import com.aruba.acp.rabbitmq.RabbitHeaders;
import com.aruba.acp.rabbitmq.RabbitProducer;
import com.aruba.acp.rabbitmq.RabbitPropertyNames;
import com.aruba.acp.rabbitmq.RabbitSubscription;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.nio.NioParams;
import com.typesafe.config.Config;
import com.rabbitmq.client.impl.nio.WriteRequest;

public class RabbitRawProducer extends Test {

  private final static Logger LOG = LoggerFactory.getLogger(RabbitRawProducer.class);

  private RabbitConsumer deviceConsumer;
  private RabbitSubscription deviceSubscription;

  private RabbitConsumer incomingConsumer;
  private RabbitSubscription incomingSubscription;
  private int MSG_SIZE = 100;

  private String SERIALNO="CT0362347";
  private int NUM_DEVICES = 50;
  private int NUM_MESSAGES = 5000000;
  private static final int SUMMARISE_EVERY = 1000;

  private String POD_NAME="testingdelete";//new SimpleDateFormat("MMdd_HHmm").format(Calendar.getInstance().getTime());
  private AtomicInteger counter = new AtomicInteger();

  private RabbitFactory rabbitFactory;
  private RabbitProducer producer;
  private SharedConnectionInfo connections;
  


  public RabbitRawProducer() {
    super();
    LOG.debug(conf.toString());
    RabbitConfig rabbitmqConf = new RabbitConfig(conf);
    final NioParams nioParams = new NioParams();

    nioParams.setWriteQueueCapacity(10000);
    //nioParams.setWriteEnqueuingTimeoutInMs(1000);
    final BlockingQueue<WriteRequest> writeQueue = new ArrayBlockingQueue<WriteRequest>(nioParams.getWriteQueueCapacity(), true);
    nioParams.setWriteQueue(writeQueue);

    LOG.debug(rabbitmqConf.getConfig().getStringList(RabbitPropertyNames.RABBITMQ_SERVER_HOSTS).toString());

    String uri = "amqp://guest:guest@15.184.9.176:5672";
    try {
    ConnectionFactory cfconn = new ConnectionFactory();
    cfconn.setUri(uri);
    //cfconn.setSharedExecutor(Executors.newFixedThreadPool(2));
    cfconn.useNio();
    
    /*cfconn.setAutomaticRecoveryEnabled(true);
    cfconn.setTopologyRecoveryEnabled(true);
    cfconn.setConnectionTimeout(10_000);
    cfconn.setNetworkRecoveryInterval(2_000);
    cfconn.setShutdownTimeout(10_000);
    cfconn.setRequestedHeartbeat(60);
    cfconn.setChannelRpcTimeout(90_000); */
    cfconn.setNioParams(nioParams);
    
    final Connection conn = cfconn.newConnection();

    
    final int NUM_PRODUCERS = 2;
    String exchange = "acp-recv-decoder-ce";
    
    ExecutorService executor = Executors.newFixedThreadPool(NUM_PRODUCERS);
    for (int i = 0; i < NUM_PRODUCERS; i++) {
      executor.submit(new Runnable() {
      public void run() {
        try {
        Channel ch = conn.createChannel();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        final byte[] message = constructMessage("iap.cmd.debug.req");

        final Map<String, Object> hdrs = new HashMap<String, Object>();
        hdrs.put(RabbitHeaders.LAST_CONTACT, System.currentTimeMillis());
        long start = System.currentTimeMillis();
        int published = 0;
        int thisTimeCount = 0;
        int allTimeCount = 0;
        long startTime = System.currentTimeMillis();
        long nextSummaryTime = startTime;
        //for (int i = 0; i < NUM_MESSAGES; i++) {
        while (true) {
          int randomNum = random.nextInt(0, NUM_DEVICES);
            hdrs.put(RabbitHeaders.SERIAL, String.valueOf(randomNum));
            //rabbitFactory.getNIOWriteQueue();
            //int mod = i % NUM_PRODUCERS;
            final AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().priority(0)
                .contentType("application/octet-stream")
                .headers(hdrs)
                .deliveryMode(1).build();
            ch.basicPublish(exchange, hdrs.get(RabbitHeaders.SERIAL) + "iap.cmd.debug.req", props, message);
            //LOG.debug(Thread.currentThread().getName() + " published: " + allTimeCount + " queue size: " + writeQueue.size());
            allTimeCount ++;
            thisTimeCount ++;
            long now = System.currentTimeMillis();
            if (now > nextSummaryTime) {
                int thisTimeRate = (int)(1.0 * thisTimeCount / (now - nextSummaryTime + SUMMARISE_EVERY) * SUMMARISE_EVERY);
                int allTimeRate = (int)(1.0 * allTimeCount / (now - startTime) * SUMMARISE_EVERY);
                LOG.info(thisTimeRate + " / sec currently, " + allTimeRate + " / sec average");
                thisTimeCount = 0;
                nextSummaryTime = now + SUMMARISE_EVERY;
            }
            
              
        }
        }
          catch (Exception ex) {
            LOG.error("Exception: " + ex);
            try {
              conn.close();
            }
            catch (Exception e) {}
            System.exit(1);
          }
        
      }
      });
    };
    //thread.start();
    try {
      Thread.sleep(1000000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    stop();
    }
    catch (Exception ex) {
      LOG.error("Failed: " + ex);
      System.exit(1);
    }
  }


  public byte[] constructMessage(String topic) {
    switch (topic) {
      case "iap.cmd.debug.req":
        Iap_messages.IapDebugCommand command = Iap_messages.IapDebugCommand.newBuilder().setCmd("ping www.google.com").build();
        Iap_messages.IapMessageInfo info = Iap_messages.IapMessageInfo.newBuilder().setSequence(1507151065958208L).build();
        Iap_messages.IapDebugCommandListReq req = Iap_messages.IapDebugCommandListReq.newBuilder().addCommand(command).setInfo(info).build();
        return req.toByteArray();
      case "iap.policy.sareq":
        WebpageLoadCommand c = WebpageLoadCommand.newBuilder().setUrl("http://www.google.com").build();			
        PerformanceReq r = PerformanceReq.newBuilder().addTestWebload(c).build();
        PolicyReq po = PolicyReq.newBuilder().setPerformance(r).build();
        return po.toByteArray();
      case "forward.cc.clustercontrollermap.resp":
        String ip = "192.168.2.183"; // 10.29.15.10
        ip_address.Builder ip_builder = ip_address.newBuilder().setAddr(ipToByteString(ip)).setAf(ip_address.addr_family.ADDR_FAMILY_INET);
        wireless_controller.Builder wc = wireless_controller.newBuilder()
            .setSerialNumber("B114947")
            .setUsername("admin")
            .setPassword("aruba123")
            .setControllerIpAddress(ip_builder);
        acp_event.Builder evt = acp_event.newBuilder().setTenantId("128002793").setOp(event_operation.OP_ADD)
            .setSourceDevice(source_device_type.CONTROLLER).setSourceIp(ip)
            .setWirelessController(wc)
            .setProcessedTimestamp(System.currentTimeMillis());
        for (int i = 0; i < ip_builder.getAddr().size(); i++) {
          LOG.debug("" + ip_builder.getAddr().byteAt(i));
        }
        LOG.debug(MessageHelper.toPrettyString(ip_builder));
        return evt.build().toByteArray();
      default:
        ByteBuffer eventTypeBuffer = ByteBuffer.allocate(MSG_SIZE);
        ByteString barray = ByteString.copyFrom(eventTypeBuffer);
        DeviceMessage builder = DeviceMessage.newBuilder().setTopic(topic).setData(barray).build();
        return builder.toByteArray();

    }
  }

  public void producer(String serialNum, String topic) {	
    final byte[] message = constructMessage(topic);

    try {
      // Copy all the headers over and add the current time stamp
      final Map<String, Object> hdrs = new HashMap<String, Object>();
      hdrs.put(RabbitHeaders.LAST_CONTACT, System.currentTimeMillis());
      hdrs.put(RabbitHeaders.SERIAL, serialNum);
      this.producer.publishDeviceMsgToExchange(topic, hdrs, message, false);
    } catch (Exception e) {
      LOG.error("Exception sending data to mq: topic=" + topic + ", serial=" + serialNum, e);
    }
  }


  public static Connection createRmqConnection(String host, String user, String password) {
    Connection connection = null;
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(host);
      factory.setUsername(user);
      factory.setPassword(password);
      connection = factory.newConnection();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return connection;
  }
  public void sendMessage(RabbitRawProducer producer, String topic, final ThreadLocalRandom random) {
    for (int i = 0; i < NUM_MESSAGES; i++) {
      int randomNum = random.nextInt(0, NUM_DEVICES);
      producer.producer(String.valueOf(randomNum), topic);
    }
  }

  public void addBinding(RabbitFactory rabbitFactory) {
    RabbitConsumer consumer = rabbitFactory.getConsumer(POD_NAME);
    //RabbitConsumer consumer = rabbitFactory.createConsumer("veena", POD_NAME, true);
    consumer.addTopicBasedTemplate(RabbitHeaders.SERIAL, SERIALNO);
    boolean success = false;
    int MAX_WAIT_SEC = 5;
    int retry = 1;
    while ((retry < 3) && (!success)) {
      int randomNum = ThreadLocalRandom.current().nextInt(1, MAX_WAIT_SEC);
      System.out.println("Retry: " + retry + ", waiting for: " + randomNum);
      try {
        Thread.sleep(randomNum * 1000);
      }
      catch (Exception ex) {

      }
      retry++;
      //success = rabbitFactory.getConsumer(AppConfig.POD_NAME).addTopicBasedTemplate(RabbitHeaders.SERIAL, serialNum);
      if (retry == 2) {success = true;}
    }

    /*for (int i = 0; i < NUM_DEVICES; i++) {
			consumer.addTopicBasedTemplate(RabbitHeaders.SERIAL, String.valueOf(i));
		}*/
  }

  public void createIncomingConsumer() {
    incomingConsumer = rabbitFactory.createConsumer("veena", POD_NAME, true);

    incomingSubscription = incomingConsumer.subscribe(this::relay, true);		
  }

  public void createDeviceConsumer() {
    deviceConsumer = rabbitFactory.createConsumer("recv-decoder-queue");
    deviceSubscription = deviceConsumer.subscribe(this::relay, true);		
  }

  public void stop() {
    System.out.println("Received: " + counter.get());
    try {
      if (this.deviceSubscription != null && this.deviceConsumer != null) {
        this.deviceSubscription.cancel(true);
        this.deviceConsumer.close();
      }
      if (this.incomingSubscription != null && this.incomingConsumer != null) {
        this.incomingSubscription.cancel(true);
        this.incomingConsumer.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } 
  }

  private void rmqShutdownHandler(final Exception e) {

  }

  public void relay(String topic, Map<String, Object> headers, byte[] body) {

    if (body != null) {
      counter.incrementAndGet();
      //System.out.println("received time -> " + (System.currentTimeMillis() - Long.parseLong(String.valueOf(headers.get("x-timestamp")))));

      System.out.println("received topic ->" + topic);
      topic = topic.replace(headers.get(RabbitHeaders.SERIAL) + ".", "");

      System.out.println(decodeProtobuf(topic, body));
      System.out.println("Received: " + counter.get());
    }
  }


  public static void main(String[] args) {

    RabbitRawProducer prod = new RabbitRawProducer();
    //RabbitMQUtil prod = new RabbitMQUtil(c);


  }
  /*		Connection connection = createRmqConnection("10.53.14.110", "guest", "guest");
	Channel chan;
	try {
		chan = connection.createChannel();


	return chan;
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;*/

  /*	    Class appClass = RabbitConsumer.class;

    Class[] parameterTypes = {};


    Field m;
    try {
      m = appClass.getDeclaredField("channel");
      m.setAccessible(true);
      Object value = m.get(consumer);
      Channel chan = (Channel) value;
      m = appClass.getDeclaredField("queue");

      m.setAccessible(true);
      value = m.get(consumer);
      RabbitQueue queue = (RabbitQueue) value;

      m = RabbitQueue.class.getDeclaredField("name");
      m.setAccessible(true);
      String qname = (String)m.get(queue);
      m = RabbitQueue.class.getDeclaredField("exchange");
      m.setAccessible(true);
      String ename = (String)m.get(queue);

		Map<String, Object> bindingArgs = new HashMap<String, Object>();
	    bindingArgs.put("serial", SERIALNO);
	    bindingArgs.put("x-match", "any");
		chan.queueBind(qname, ename, "", bindingArgs);

		bindingArgs = new HashMap<String, Object>();
	    bindingArgs.put("serial", SERIALNO+"-1");
	    bindingArgs.put("x-match", "any");
		chan.queueBind(qname, ename, "", bindingArgs);

    }
    catch (Exception ex) {
    	ex.printStackTrace();
    }
} */
}

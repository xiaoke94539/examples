

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aruba.acp.ce.AppConfig;
import com.aruba.acp.ce.debug.MessageHelper;
import com.aruba.acp.ce.websocketx.http.SharedConnectionInfo;
import com.aruba.acp.ce.websocketx.relay.RelayVerticle;
import com.aruba.acp.ce.websocketx.util.RMQTopicUtil;
import com.aruba.acp.device.iap.IapPolicy.PerformanceReq;
import com.aruba.acp.device.iap.IapPolicy.PolicyReq;
import com.aruba.acp.device.iap.IapPolicy.PolicyResp;
import com.aruba.acp.device.iap.IapPolicy.WebpageLoadCommand;
import com.aruba.acp.device.iap.Iap_messages;
import com.aruba.acp.device.mbus.MBus.DeviceMessage;
import com.aruba.acp.rabbitmq.RabbitConfig;
import com.aruba.acp.rabbitmq.RabbitConsumer;
import com.aruba.acp.rabbitmq.RabbitFactory;
import com.aruba.acp.rabbitmq.RabbitHeaders;
import com.aruba.acp.rabbitmq.RabbitProducer;
import com.aruba.acp.rabbitmq.RabbitSubscription;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.typesafe.config.Config;

public class RabbitMQUtil {

	private final static Logger LOG = LoggerFactory.getLogger(RelayVerticle.class);

	private RabbitConsumer deviceConsumer;
	private RabbitSubscription deviceSubscription;

	private RabbitConsumer incomingConsumer;
	private RabbitSubscription incomingSubscription;

	private String SERIALNO="CT0362347";
	private int NUM_DEVICES = 2;
	private int NUM_MESSAGES = 5;

	private String POD_NAME="testingdelete";//new SimpleDateFormat("MMdd_HHmm").format(Calendar.getInstance().getTime());
	private AtomicInteger counter = new AtomicInteger();

	private RabbitFactory rabbitFactory;
	private RabbitProducer producer;
	private SharedConnectionInfo connections;
	private int MSG_SIZE = 1000;


	public RabbitMQUtil(Config c) {

		System.out.println(c);
		System.out.println(System.getenv());
		RabbitConfig rabbitmqConf = new RabbitConfig(c);

		rabbitFactory = new RabbitFactory(rabbitmqConf.getConfig(), this::rmqShutdownHandler, Executors.newFixedThreadPool(2));
		createDeviceConsumer();
		createIncomingConsumer();

		producer = rabbitFactory.createProducer("acp-ws-relay-exchange");

		Thread thread = new Thread() {
			public void run() {
				producer(SERIALNO, "iap.cmd.debug.req");
				producer(SERIALNO, "iap.policy.sareq");
			}
		};
		thread.start();
		try {
			Thread.sleep(1000000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		stop();
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
	public void sendMessage(RabbitMQUtil producer, String topic, final ThreadLocalRandom random) {
		for (int i = 0; i < NUM_MESSAGES; i++) {
			int randomNum = random.nextInt(0, NUM_DEVICES);
			producer.producer(String.valueOf(randomNum), topic);
		}
	}

	public void addBinding(RabbitFactory rabbitFactory) {
		//RabbitConsumer consumer = rabbitFactory.getConsumer(POD_NAME);
		//consumer.addTopicBasedTemplate(RabbitHeaders.SERIAL, SERIALNO);
		String owner1 = UUID.randomUUID().toString();
		RMQTopicUtil.getInstance().addTopicBinding(rabbitFactory, SERIALNO, owner1);
		RMQTopicUtil.getInstance().addTopicBinding(rabbitFactory, SERIALNO, UUID.randomUUID().toString());
		
		/*for (int i = 0; i < NUM_DEVICES; i++) {
			consumer.addTopicBasedTemplate(RabbitHeaders.SERIAL, String.valueOf(i));
		}*/
		
		System.out.println(RMQTopicUtil.getInstance().getCurrentMapAsString());
		RMQTopicUtil.getInstance().removeTopicBinding(rabbitFactory, "1", owner1);
		
		RMQTopicUtil.getInstance().removeTopicBinding(rabbitFactory, SERIALNO, owner1);
		RMQTopicUtil.getInstance().removeTopicBinding(rabbitFactory, SERIALNO, UUID.randomUUID().toString());
		System.out.println(RMQTopicUtil.getInstance().getCurrentMapAsString());
		
		RMQTopicUtil.getInstance().addTopicBinding(rabbitFactory, "1", "testing1");
		RMQTopicUtil.getInstance().addTopicBinding(rabbitFactory, "1", "testing");
		RMQTopicUtil.getInstance().removeTopicBinding(rabbitFactory, "1", "testing");
		System.out.println(RMQTopicUtil.getInstance().getCurrentMapAsString());
		RMQTopicUtil.getInstance().removeTopicBinding(rabbitFactory, "1", "testing1");
		
		System.out.println(RMQTopicUtil.getInstance().getCurrentMapAsString());
		
	}

	public void createIncomingConsumer() {
		incomingConsumer = rabbitFactory.createConsumer("veena", POD_NAME, true);
		addBinding(rabbitFactory);
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


	protected static String decodeProtobuf(String topic, byte[] messageBody) {
		try {
			if (topic.contains("iap")) {
				switch (topic) {
				case "native.iap.state.sync":
					return MessageHelper.toPrettyString(Iap_messages.IapState.parseFrom(messageBody));
				case "native.iap.stat":
					return MessageHelper.toPrettyString(Iap_messages.IapStat.parseFrom(messageBody));
				case "iap.apprf":
					return MessageHelper.toPrettyString(Iap_messages.IapAppRF.parseFrom(messageBody));
				case "iap.trap":
					return MessageHelper.toPrettyString(Iap_messages.IapAppRF.parseFrom(messageBody));
				case "native.iap.policy.saresp":
					return MessageHelper.toPrettyString(PolicyResp.parseFrom(messageBody));
				case "iap.cmd.debug.req":
					return MessageHelper.toPrettyString(Iap_messages.IapDebugCommandListReq.parseFrom(messageBody));
				case "native.iap.cmd.debug.resp":
					return MessageHelper.toPrettyString(Iap_messages.IapDebugCommandListResp.parseFrom(messageBody));
				default:
					return "Wrong topic or Topic not yet supported!";
				}
			}
		} catch (Exception e) {
			System.out.println("Error decoding protobuf message - " + topic);
		}
		return "";
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

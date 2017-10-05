


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import com.aruba.acp.ce.App;
import com.aruba.acp.ce.AppConfig;
import com.aruba.acp.ce.debug.MessageHelper;
import com.aruba.acp.common.utils.config.ConfigReader;
import com.aruba.acp.common.utils.message.MsgUtil.Topic;
import com.aruba.acp.device.iap.Iap_messages;
import com.aruba.acp.device.iap.IapPolicy.PerformanceReq;
import com.aruba.acp.device.iap.IapPolicy.PolicyReq;
import com.aruba.acp.device.iap.IapPolicy.PolicyResp;
import com.aruba.acp.device.iap.IapPolicy.TCPConnectCommand;
import com.aruba.acp.device.iap.IapPolicy.WebpageLoadCommand;
import com.aruba.acp.device.mbus.MBus.DeviceMessage;
import com.aruba.acp.rabbitmq.RabbitConfig;
import com.aruba.acp.rabbitmq.RabbitConsumer;
import com.aruba.acp.rabbitmq.RabbitFactory;
import com.aruba.acp.rabbitmq.RabbitHeaders;
import com.aruba.acp.rabbitmq.RabbitQueue;
import com.aruba.acp.rabbitmq.RabbitSubscription;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.typesafe.config.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

public class Test {


  public static void main(String[] args) {
	  setLogLevel("com.aruba.acp", "TRACE", Level.TRACE);

		RabbitMQProducer prod = new RabbitMQProducer();
	  
  }
  

	
	public static void setLogLevel(String baseLoggerName, String loglevel, Level defaultLevel){
		try {
			final Level newLevel = Level.toLevel(loglevel, defaultLevel);
			final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
			for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
				if (log.getName().startsWith(baseLoggerName)) {
					log.setLevel(newLevel);
				}
			}
			System.out.println("Changed base logger \"" + baseLoggerName + "\" to level " + newLevel.toString());
		} catch (Exception e) {
			System.err.println("Exception changing log level of " + baseLoggerName);
		}
	}
}

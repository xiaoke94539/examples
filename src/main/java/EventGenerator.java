import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.kstream.KTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aruba.acp.common.utils.config.ConfigReader;
import com.aruba.acp.common.utils.message.MacUtils;
import com.aruba.acp.proto.Schema;
import com.aruba.acp.proto.Schema.access_point;
import com.aruba.acp.proto.Schema.acp_event;
import com.aruba.acp.proto.Schema.acp_event.event_operation;
import com.aruba.acp.proto.Schema.acp_event.source_device_type;
import com.aruba.acp.proto.Schema.ip_address;
import com.aruba.acp.proto.Schema.mac_address;
import com.aruba.acp.proto.Schema.radio;
import com.aruba.acp.proto.Schema.station;
import com.aruba.acp.proto.Schema.virtual_access_point;
import com.aruba.acp.proto.Schema.wireless_controller;
import com.google.protobuf.ByteString;
import com.typesafe.config.Config;



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;


public class EventGenerator extends Test {
  private static final Logger LOG = LoggerFactory.getLogger(EventGenerator.class);
  //private static String DEPLOYMENT = "dev.";

  private static String DEPLOYMENT = "";
  private static String CE_TOPIC = "ce.state";
  private static String STATE_STATION = "aggregated.mc.state.station";
  private static String STATE_AP = "aggregated.mc.state.ap";
  
  
  private static String BOOTSTRAP_CONTROLLER_TOPIC = "bootstrap.mc.controller";
  private static String BOOTSTRAP_STATION_TOPIC = "bootstrap.mc.station";

  
  public static int NUM_MESSAGES = 20000;
  public static int BATCH_SIZE = 2;
  public static int NUM_CONSUMER = 1;
  DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
  private AtomicInteger counter = new AtomicInteger();
  KafkaStreams streams = null;
  KafkaConsumer<String, byte[]> consumer = null;
  KafkaProducer<String, byte[]> producer = null;
  
  ByteString AP_MAC_DEFAULT = ByteString.copyFrom(new byte[]{(byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x00});
  
  ByteString apMac = ByteString.copyFrom(new byte[]{(byte) 0x24, (byte) 0xde, (byte) 0xc6, (byte) 0x75, (byte) 0xc7, (byte) 0xa0});
  ByteString radioMac = ByteString.copyFrom(new byte[]{(byte) 0x24, (byte) 0xde, (byte) 0xc6, (byte) 0x75, (byte) 0xc7, (byte) 0x0});
  ByteString radioMac1 = ByteString.copyFrom(new byte[]{(byte) 0x24, (byte) 0xde, (byte) 0xc6, (byte) 0x75, (byte) 0xc7, (byte) 0x1});
  ByteString vapMac = ByteString.copyFrom(new byte[]{(byte) 0x24, (byte) 0xde, (byte) 0xc6, (byte) 0x75, (byte) 0xc7, (byte) 0x8});
  ByteString vapMac2 = ByteString.copyFrom(new byte[]{(byte) 0x24, (byte) 0xde, (byte) 0xc6, (byte) 0x75, (byte) 0xc7, (byte) 0xa});
  
  String WC_CONTROLLER_SERIAL = "172.16.0.1";
  List<String> apSerials = Arrays.asList("40539236818042",
      "163501080062262",
      "none_existing",
      "veenaAP1",
      "238352578933286");
  
  // Controller serial number: 94:b4:0f:cc:29:36
  private static List<String> TOPICS = 
    (Arrays.asList(DEPLOYMENT + STATE_STATION, 
        DEPLOYMENT + CE_TOPIC, 
        DEPLOYMENT + STATE_AP));
  
  
  ConfigReader confReader = null;
  private ExecutorService executor;

  public EventGenerator() {
    super();
    confReader = new ConfigReader(conf);

    System.out.println(conf);
    System.out.println("Kafak config");
    System.out.println(confReader.getListAsMap("acp.kafka.list").get(0));
    //createTopic(TOPIC, confReader);


    producer = new KafkaProducer<String, byte[]>(confReader.getListAsMap("acp.kafka.list").get(0));
    
    //apUpdate();
    //radioUpdate();
    //vapUpdate();
    
    
    //stationUpdate("238352578933286", ByteString.copyFrom(new byte[]{(byte) 0xd8, (byte)0xc7, (byte)0xc8, (byte)0x47, (byte)0x22, (byte)0x63}));

    //requestAPController(WC_CONTROLLER_SERIAL, apSerials, BOOTSTRAP_CONTROLLER_TOPIC);
    requestAPController(WC_CONTROLLER_SERIAL, apSerials, BOOTSTRAP_STATION_TOPIC);
    requestAPController(WC_CONTROLLER_SERIAL);
    

    
//    radioDelete();
//    vapUpdate();
//    vapDelete();

    executor = Executors.newFixedThreadPool(NUM_CONSUMER);

    System.out.println("Finished producing");

    //     for (final KafkaStream stream : streams) {
    for (int i = 0; i < NUM_CONSUMER; i++) {
      final int threadNumber = i;
      executor.submit(new Runnable() {
        public void run() {
          simpleConsumer(threadNumber);
        }
      });

    }
  }

  public void createTopic(String topic, ConfigReader confReader) {
    AdminClient admin = AdminClient.create(confReader.getListAsMap("acp.kafka.stream").get(0));
    int part = 3;
    short replica = 2;
    NewTopic top = new NewTopic(topic, part, replica);
    List<NewTopic> ltop = new ArrayList<NewTopic>();
    ltop.add(top);
    admin.createTopics(ltop);
  }

  public void requestAPController(String controllerSerial) {
    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setWirelessController(getWirelessController(controllerSerial));
    publish(BOOTSTRAP_CONTROLLER_TOPIC, "ALL", evt.build().toByteArray());
  }

  
  public void requestAllController(String wcSerilaNumber, String topic) {
    wireless_controller.Builder wc = getWirelessController(wcSerilaNumber);
    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setWirelessController(wc);
    publish(topic, "ALL", evt.build().toByteArray());
  }
  
  public void requestAPController(String wcSerialNumber, List<String> apSerialNumbers, String topic) {
    wireless_controller.Builder wc = getWirelessController(wcSerialNumber);
    for (String ap : apSerialNumbers) {
      wc.addAccessPoint(getAP(AP_MAC_DEFAULT, "127.0.0.1", ap));
    }
    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setWirelessController(wc);
    publish(topic, "ALL", evt.build().toByteArray());
  }
  
  public void stationUpdate(String apSerialNumber, ByteString vapBssid) {
    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setStation(getStation(apSerialNumber, vapBssid));
    publish(CE_TOPIC, apSerialNumber, evt.build().toByteArray());
  }
  
  public void apUpdate() {
    System.out.println("Start producing");
    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setAccessPoint(getAP(apMac, "127.0.3.4"));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    evt.setOp(event_operation.OP_UPDATE).setAccessPoint(getAP(apMac, "127.0.3.4"));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());

    evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setAccessPoint(getAP(apMac, "127.0.3.4", "veenaAP"));
    publish(CE_TOPIC, "3", evt.build().toByteArray());
    evt.setOp(event_operation.OP_UPDATE).setAccessPoint(getAP(apMac, "127.0.3.4", "veenaAP1"));
    publish(CE_TOPIC, "8", evt.build().toByteArray());
    
    }
  
  public void radioUpdate() {
    System.out.println("Start producing");
    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setRadio(getRadio(apMac, radioMac, 1));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    
    evt.setOp(event_operation.OP_UPDATE).setRadio(getRadio(apMac, radioMac, 2));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    
    evt.setOp(event_operation.OP_UPDATE).setRadio(getRadio(apMac, radioMac1, 2));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    }

  public void radioDelete() {
    System.out.println("Start producing");

    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setRadio(getRadio(apMac, radioMac, 1));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    
    evt.setOp(event_operation.OP_DELETE).setRadio(getRadio(apMac, radioMac, 2));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    }
  
  public void vapUpdate() {
    System.out.println("Start producing");

    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setVirtualAccessPoint(getVAP(apMac, vapMac, radioMac, "1"));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    
    evt.setOp(event_operation.OP_UPDATE).setVirtualAccessPoint(getVAP(apMac, vapMac, radioMac, "2"));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    }

  public void vapDelete() {
    System.out.println("Start producing");

    acp_event.Builder evt = getACPEvent(event_operation.OP_ADD, "127.0.0.1").setVirtualAccessPoint(getVAP(apMac, vapMac, radioMac, "1"));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    
    evt.setOp(event_operation.OP_DELETE).setVirtualAccessPoint(getVAP(apMac, vapMac, radioMac, "2"));
    publish(CE_TOPIC, MacUtils.macAsString(apMac), evt.build().toByteArray());
    }  
  
 
  
  /*acpEvent.hasSourceDevice() && acpEvent.hasSourceIp() //&& acpEvent.hasSerialNumber()
          && acpEvent.getSourceDevice().equals(Schema.acp_event.source_device_type.CONTROLLER));
          */
  

  public static acp_event.Builder getACPEvent(event_operation op, String ip) {
    acp_event.Builder evt = acp_event.newBuilder().setTenantId("1").setOp(op)
        .setSourceDevice(source_device_type.CONTROLLER).setSourceIp(ip)
        .setProcessedTimestamp(System.currentTimeMillis());
    return evt;
  }
  
  public static wireless_controller.Builder getWirelessController(String serialNumber) {
    return wireless_controller.newBuilder().setSerialNumber(serialNumber);
  }
  
  public static access_point.Builder getAP(ByteString mac, String ip ) {
      return getAP(mac, ip, MacUtils.macAsString(mac));
  }
  public static access_point.Builder getAP(ByteString mac, String ip, String serialNum) {
      return access_point.newBuilder().setApEthMac(mac_address.newBuilder().setAddr(mac)).setApName(serialNum).setSerialNumber(serialNum)
    .setApIpAddress(ip_address.newBuilder().setAddr(ByteString.copyFromUtf8(ip))).setApModel("215");
  }
  
  
  public static virtual_access_point.Builder getVAP(ByteString apMac, ByteString vapMac, ByteString radioMac, String name) {
    return virtual_access_point.newBuilder().setApEthMac(mac_address.newBuilder().setAddr(apMac))
        .setBssid(mac_address.newBuilder().setAddr(vapMac)).setApSerialNumber(MacUtils.macAsString(apMac))
        .setSsid(name).setRadioBssid(mac_address.newBuilder().setAddr(radioMac));
  }
  
  
  public static radio.Builder getRadio(ByteString apMac, ByteString radioMac, long name) {
    return radio.newBuilder().setApEthMac(mac_address.newBuilder().setAddr(apMac))
        .setTimestamp(name).setApSerialNumber(MacUtils.macAsString(apMac))
        .setRadioBssid(mac_address.newBuilder().setAddr(radioMac));
  }
  
  public static station.Builder getStation(String apSerialNumber, ByteString vapMac) {
    return station.newBuilder().setApSerialNumber(apSerialNumber)
        .setBssid(mac_address.newBuilder().setAddr(vapMac))
        .setStaEthMac(mac_address.newBuilder().setAddr(vapMac));
  }
  
  public void publish(String topic, String key, byte[] bytes) {
    ProducerRecord data = new ProducerRecord<String, byte[]>(DEPLOYMENT + topic, key, bytes);
    LOG.info("Publishing to: " + topic + " with key: " + key);
    Future<RecordMetadata> recordMetadata = producer.send(data, new Callback() {
      public void onCompletion(RecordMetadata metadata, Exception e) {
        if(e != null)
          e.printStackTrace();
      }
    });
  }

  public void simpleConsumer(int threadNumber) {

    consumer = new KafkaConsumer<String, byte[]>(confReader.getListAsMap("acp.kafka.list").get(0));
    consumer.subscribe(TOPICS);

    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(5000);
      System.out.println("Received: " + records.count() + " by " + threadNumber);
      for (TopicPartition topicPartition : records.partitions()) {
        List<ConsumerRecord<String, byte[]>> topicRecords = records.records(topicPartition);
        for (ConsumerRecord<String, byte[]> record : topicRecords) {
          //System.out.println(String.format("offset = %d, key = %s, value = %s", record.offset(), record.key(), record.value()));
          LOG.info("ConsumerId:{}-Topic:{} => Partition={}, Offset={}, EventTime:[{}] Key={}", threadNumber,
              topicPartition.topic(), record.partition(), record.offset(), record.timestamp(), record.key());
          LOG.info(decodeProtobuf(topicPartition.topic().replace(DEPLOYMENT, ""), record.value()));
              
        }
      }
      //consumer.commitSync();
      counter.addAndGet(records.count());
      System.out.println("Got " + counter.get());
      if (counter.get() >= NUM_MESSAGES) {
        shutdown();
      }
    }
  }

  public void shutdown() {
    if (consumer != null) consumer.close();
    if (producer != null) producer.close();
    if (executor != null) {
      executor.shutdown();
      try {

        if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
          System.out.println(
              "Timed out waiting for consumer threads to shut down, exiting uncleanly");
        }
      } catch (InterruptedException e) {
        System.out.println("Interrupted during shutdown, exiting uncleanly");
      }
    }
  }

  public static void main(String[] args) {

    //RabbitMQProducer prod = new RabbitMQProducer(c);
    //RabbitMQUtil prod = new RabbitMQUtil(c);
    EventGenerator prod = new EventGenerator();

  }
}

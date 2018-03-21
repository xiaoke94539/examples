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
import com.typesafe.config.Config;



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;


public class KProducer extends Test {
  private static final Logger LOG = LoggerFactory.getLogger(KProducer.class);

  public static int NUM_MESSAGES = 20;
  public static int BATCH_SIZE = 2;
  public static int NUM_CONSUMER = 1;
  DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
  private AtomicInteger counter = new AtomicInteger();
  KafkaStreams streams = null;
  KafkaConsumer<String, byte[]> consumer = null;
  KafkaProducer<String, byte[]> producer = null;

  private static String DEV = "dev.";
  private static String TOPIC = "streams-plaintext-input"; //DEV + "ce.state";
  ConfigReader confReader = null;
  private ExecutorService executor;

  public KProducer() {
    super();
    confReader = new ConfigReader(conf);

    System.out.println(conf);
    System.out.println("Kafak config");
    System.out.println(confReader.getListAsMap("acp.kafka.list").get(0));
    //createTopic(TOPIC, confReader);


    producer = new KafkaProducer<String, byte[]>(confReader.getListAsMap("acp.kafka.list").get(0));
    //streamProducer(producer, 1);
    simpleProducer(producer, TOPIC);
    simpleProducer(producer, TOPIC + "1");
    //streamConsumer();

    executor = Executors.newFixedThreadPool(NUM_CONSUMER);

    //producer.close();
    System.out.println("Finished producing");

    //     for (final KafkaStream stream : streams) {
/*    for (int i = 0; i < NUM_CONSUMER; i++) {
      final int threadNumber = i;
      executor.submit(new Runnable() {
        public void run() {
          simpleConsumer(threadNumber);
          //streamConsumer(threadNumber, source);
        }
      });

    } */
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

  public void streamProducer(KafkaProducer<String, String> producer, int partition) {
    System.out.println("Start producing");
    for (int i = 0; i < NUM_MESSAGES; i++) {
      ProducerRecord data = new ProducerRecord<String, String>(TOPIC, partition, "word" + i, "Hello this is record " + dateFormat.format(LocalDateTime.now()));
      System.out.println(i);
      Future<RecordMetadata> recordMetadata = producer.send(data, new Callback() {
        public void onCompletion(RecordMetadata metadata, Exception e) {
          if(e != null)
            e.printStackTrace();
          System.out.println("The offset of the record we just sent is: " + metadata);
        }
      });
    }
  }

  public void simpleProducer(KafkaProducer<String, byte[]> producer, String topic) {
    System.out.println("Start producing");
    for (int i = 0; i < NUM_MESSAGES; i++) {
      ProducerRecord data = new ProducerRecord<String, byte[]>(topic, String.valueOf(i), (topic + " Hello this is record " + i + " " + dateFormat.format(LocalDateTime.now())).getBytes());
      System.out.println(i + ":"  +  topic + " Hello this is record " + i + " " + dateFormat.format(LocalDateTime.now()));
      try {
        Thread.sleep(1000);
      }
      catch (Exception ex) {
        
      }
      Future<RecordMetadata> recordMetadata = producer.send(data, new Callback() {
        public void onCompletion(RecordMetadata metadata, Exception e) {
          if(e != null)
            e.printStackTrace();
          //System.out.println("The offset of the record we just sent is: " + metadata);
        }
      });
    }
  }
  public void streamConsumer(int threadNumber, KStream<String, String> source) {
    source.foreach((key, value) -> System.out.println(String.format("key = %s, value = %s", key, value)));
    /*final Pattern pattern = Pattern.compile("\\W+");

		KTable<String, Long> viewCount = source.flatMapValues(value-> Arrays.asList(pattern.split(value.toLowerCase())))
		 .map((key, value) -> new KeyValue<String, String>(value, value))
		 .filter((key, value) -> (!value.equals("the")))
        .groupByKey()
        .count("CountStore");//.mapValues(value->Long.toString(value));
		viewCount.print(Serdes.String(), Serdes.Long());
     */
    /*KStream counts = source.flatMapValues(value-> Arrays.asList(pattern.split(value.toLowerCase())))
	    		 .map((key, value) -> new KeyValue<Object, Object>(value, value))
	    		 .filter((key, value) -> (!value.equals("the")))
	             .groupByKey()
	             .count("CountStore").mapValues(value->Long.toString(value)).toStream();
	     counts.to("wordcount-output"); */


    /*ConsumerIterator<Object, Object> it = stream.iterator();

	    while (it.hasNext()) {
	      MessageAndMetadata<Object, Object> record = it.next();

	      String topic = record.topic();
	      int partition = record.partition();
	      long offset = record.offset();
	      Object key = record.key();
	      GenericRecord message = (GenericRecord) record.message();
	      System.out.println("Thread " + threadNumber +
	                         " received: " + "Topic " + topic +
	                         " Partition " + partition +
	                         " Offset " + offset +
	                         " Key " + key +
	                         " Message " + message.toString());
	    }
	    System.out.println("Shutting down Thread: " + threadNumber); */
  }
  public void simpleConsumer(int threadNumber) {

    consumer = new KafkaConsumer<String, byte[]>(confReader.getListAsMap("acp.kafka.list").get(0));
    consumer.subscribe(Arrays.asList(TOPIC));

    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(5000);
      System.out.println("Received: " + records.count() + " by " + threadNumber);
      for (TopicPartition topicPartition : records.partitions()) {
        List<ConsumerRecord<String, byte[]>> topicRecords = records.records(topicPartition);
        for (ConsumerRecord<String, byte[]> record : topicRecords) {
          //System.out.println(String.format("offset = %d, key = %s, value = %s", record.offset(), record.key(), record.value()));
          LOG.info("ConsumerId:{}-Topic:{} => Partition={}, Offset={}, EventTime:[{}] Key={}", threadNumber,
              topicPartition.topic(), record.partition(), record.offset(), record.timestamp(), record.key());
          LOG.info(decodeProtobuf(topicPartition.topic(), record.value()));
              
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
    KProducer prod = new KProducer();

  }
}

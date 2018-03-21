


import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.KafkaStreams.State;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.streams.processor.TaskMetadata;
import org.apache.kafka.streams.processor.ThreadMetadata;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aruba.acp.common.utils.config.ConfigReader;


public class KStreamConsumer extends Test {

  private final static Logger LOG = LoggerFactory.getLogger(KStreamConsumer.class);
  private  ExecutorService executor;
  private static String DEPLOYMENT = "dev.";
  //private static String DEPLOYMENT = "";
  private static String CE_TOPIC = "ce.state";

  final Serde<String> stringSerde = Serdes.String();
  final Serde<byte[]> byteSerde = Serdes.ByteArray();
  public static int NUM_CONSUMER = 2;
  private String STORE_NAME = DEPLOYMENT + "veena-store";

  public KStreamConsumer() {
    super();
    ConfigReader confReader = null;
    confReader = new ConfigReader(conf);

    System.out.println(conf);
    System.out.println("Kafak config");
    System.out.println(confReader.getListAsMap("acp.kafka.list").get(0));
    String topic = "streams-plaintext-input"; // DEPLOYMENT + CE_TOPIC

    /*StreamsBuilder builder = new StreamsBuilder();


    StoreBuilder<KeyValueStore<String, byte[]>> countStoreSupplier = Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore(STORE_NAME),
        Serdes.String(),
        Serdes.ByteArray()).withCachingEnabled();


    builder.addStateStore(countStoreSupplier); */

    KafkaStreams streams = createStream(topic, confReader, false);
    KafkaStreams streams1 = createStream(topic + "1", confReader, true);
    
    KafkaStreams streams2 = createStateTopology(topic, confReader, false);
    
    /*final Topology topology = builder.build();
    LOG.info(topology.describe().toString());

    Map<String, Object> props = confReader.getListAsMap("acp.kafka.stream").get(0);
    
    KafkaStreams streams = new KafkaStreams(builder.build(), new StreamsConfig(props)); */

    final CountDownLatch latch = new CountDownLatch(1);

    // attach shutdown handler to catch control-c
    Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
      @Override
      public void run() {
        streams.close();
        streams1.close();
        streams2.close();
        latch.countDown();
      }
    });

    try {
      //streams.start();
      //streams1.start();
      streams2.start();
      
      /*try {
        Thread.sleep(1000);
      }
      catch (Exception e) {}

      */
      latch.await();
    } catch (Throwable e) {
      System.exit(1);
    }
    //System.exit(0);
  }


  public void dumpStore(KafkaStreams streams) {
    ReadOnlyKeyValueStore<String, byte[]> store = streams.store(STORE_NAME, QueryableStoreTypes.<String, byte[]>keyValueStore());
    KeyValueIterator<String, byte[]> range = store.all();
    LOG.debug("Store has: " + store.approximateNumEntries());
    while (range.hasNext()) {
      KeyValue<String, byte[]> next = range.next();
      try {
        LOG.debug("Value for for " + next.key + " is: " + new String(next.value));
      }
      catch (Exception ex) {
        LOG.info("Error: " + ex);
      }
    }
    range.close();
  }

  private KafkaStreams createStateTopology(String topic, ConfigReader confReader, boolean read) {
    //StreamsBuilder builder = new StreamsBuilder();

    Topology builder = new Topology();
    builder.addSource("state", topic);
  
    builder.addProcessor("state_processor", new ProcessorSupplier<String, byte[]>() {
      @Override
      public Processor<String, byte[]> get() {
        return new AbstractProcessor<String, byte[]>() {

          KeyValueStore<String, byte[]> store;
          int processedRecords;

          @SuppressWarnings("unchecked")
          @Override
          public void init(ProcessorContext context) {
            super.init(context);
            store = (KeyValueStore<String, byte[]>) context.getStateStore(STORE_NAME);
            LOG.info("STORE NAME: " + store.name());
          }

          @Override
          public void process(String key, byte[] value) {
            LOG.info("Processing: " + key + ": " + new String(value));
            if (key == null) {
              key = "veena-key";
            }
            if (!read) {
              store.put(key, value);
              LOG.info("Store: " + new String(store.get(key)));
            }
            else {
              if (store.get(key) == null) {
                LOG.error("Key: " + key + ", but the value is null");
              }
              else {
                LOG.info("Reading from store: " + new String(store.get(key)));
              }
            }
            context().forward(String.valueOf(processedRecords++), store.get(key));
            
          }
          @Override
          public void punctuate(long timestamp) {
          }

          @Override
          public void close() {
          }
        };
      }}, "state");
    
    StoreBuilder<KeyValueStore<String, byte[]>> stateSupplier = Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore(STORE_NAME),
        Serdes.String(),
        Serdes.ByteArray()).withCachingEnabled();

    builder.addStateStore(stateSupplier, "state_processor"); 
    
    builder.addSink("state_sink", "state_out", new StringSerializer(), new ByteArraySerializer(),"state_processor");

    LOG.info(builder.describe().toString());

    Map<String, Object> props = confReader.getListAsMap("acp.kafka.stream").get(0);
    if (read) {
      props.put("application.id", "read-appid");
    }
    
    LOG.debug("Configs: " + props);
    KafkaStreams streams = new KafkaStreams(builder, new StreamsConfig(props));
    return streams;
  }
  
  public KafkaStreams createStream(String topic, ConfigReader confReader, boolean read) {
    
    StreamsBuilder builder = new StreamsBuilder();


    StoreBuilder<KeyValueStore<String, byte[]>> countStoreSupplier = Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore(STORE_NAME),
        Serdes.String(),
        Serdes.ByteArray()).withCachingEnabled();


    builder.addStateStore(countStoreSupplier);

    LOG.info("Topic to consume: " + topic);

    KStream<String, byte[]> kstream = builder.stream(topic);

    kstream.process(new ProcessorSupplier<String, byte[]>() {
      @Override
      public Processor<String, byte[]> get() {
        return new AbstractProcessor<String, byte[]>() {
          KeyValueStore<String, byte[]> store;
          int processedRecords;

          @SuppressWarnings("unchecked")
          @Override
          public void init(ProcessorContext context) {
            store = (KeyValueStore<String, byte[]>) context.getStateStore(STORE_NAME);
            LOG.info("STORE NAME: " + store.name());
            
            
          }

          @Override
          public void process(String key, byte[] value) {
            LOG.info("Processing: " + key + ": " + new String(value));
            if (key == null) {
              key = "veena-key";
            }
            if (!read) {
              store.put(key, value);
              LOG.info("Store: " + new String(store.get(key)));
            }
            else {
              if (store.get(key) == null) {
                LOG.error("Key: " + key + ", but the value is null");
              }
              else {
                LOG.info("Reading from store: " + new String(store.get(key)));
              }
            }

            processedRecords++;
            
          }

          @Override
          public void punctuate(long timestamp) {
          }

          @Override
          public void close() {
          }
        };
      }
    }, STORE_NAME);
    
    final Topology topology = builder.build();
    LOG.info(topology.describe().toString());

    Map<String, Object> props = confReader.getListAsMap("acp.kafka.stream").get(0);
    if (read) {
      props.put("application.id", "read-appid");
    }
    
    LOG.debug("Configs: " + props);
    KafkaStreams streams = new KafkaStreams(builder.build(), new StreamsConfig(props));

    streams.setStateListener(new KafkaStreams.StateListener() {

      @Override
      public void onChange(State newState, State oldState) {
        if (newState.isRunning()) {
          LOG.debug("stream state: " + streams.state());
          LOG.debug("Stream local metadaa" + streams.localThreadsMetadata());
          //LOG.debug("Stream metadata" + streams.);
          Collection<ThreadMetadata> metadataForStore = streams.localThreadsMetadata();//streams.allMetadataForStore(StationProcessor.AP_STORE_NAME);
          //LOG.debug("Getting the meta data " + metadataForStore);
          //StreamsMetadata streamMetada= streams.metadataForKey(StationProcessor.AP_STORE_NAME, "veenaAP", Serdes$StringSerde);
          for (ThreadMetadata data : metadataForStore) {
            Set<TaskMetadata> taskMeta = data.activeTasks();
            for (TaskMetadata task: taskMeta) {
              LOG.debug("Metadata: " + task.topicPartitions());
            }
          }
        }
        
      }
      
    });
    return streams;
    
  }

  public void shutdown() {
    if (executor != null) executor.shutdown();
    try {
      if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
        System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
      }
    } catch (InterruptedException e) {
      System.out.println("Interrupted during shutdown, exiting uncleanly");
    }
  }


  public static void main(String[] args) {

    KStreamConsumer example = new KStreamConsumer();
  }
}


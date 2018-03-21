import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aruba.acp.common.utils.config.ConfigReader;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.TopicMetadata;
import kafka.javaapi.TopicMetadataRequest;
import kafka.javaapi.TopicMetadataResponse;
import kafka.javaapi.consumer.SimpleConsumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class KafkaPartition extends Test {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaPartition.class);
  
  ConfigReader confReader = null;
  public KafkaPartition() {
    
    super();
    confReader = new ConfigReader(conf);

    System.out.println(conf);
    System.out.println("Kafak config");
    System.out.println(confReader.getListAsMap("acp.kafka.list").get(0));
    String topic = "ce.state";
    System.out.println("Partition for : " + topic + " is: " + getPartitionsForTopic(topic, "127.0.0.1", 9092));
  }
  
  public static List<Integer> getPartitionsForTopic(String topic, String host, int port) {
    List<Integer> partitions = new ArrayList<>();
    //for (BrokerInfo seed : seedBrokers) {
      SimpleConsumer consumer = null;
      try {
        consumer = new SimpleConsumer(host, port, 20000, 128 * 1024, "partitionLookup");
        List<String> topics = Collections.singletonList(topic);
        TopicMetadataRequest req = new TopicMetadataRequest(topics);
        kafka.javaapi.TopicMetadataResponse resp = consumer.send(req);

        // find our partition's metadata
        List<TopicMetadata> metaData = resp.topicsMetadata();
        for (TopicMetadata item : metaData) {
          for (PartitionMetadata part : item.partitionsMetadata()) {
            partitions.add(part.partitionId());
          }
        }
        //break;  // leave on first successful broker (every broker has this info)
      } catch (Exception e) {
        // try all available brokers, so just report error and go to next one
        LOG.error("Error communicating with broker [" + host + "] to find list of partitions for [" + topic + "]. Reason: " + e);
      } finally {
        if (consumer != null)
          consumer.close();
      }
      return partitions;
    }
    

  public static void main(String[] args) {
    new KafkaPartition();

  }

}

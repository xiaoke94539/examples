import java.io.ByteArrayOutputStream;

public class AcpEventWrapper {
	String topic;
	
	public AcpEventWrapper(String topic) {
		this.topic = topic;
	}
	public String getTopicAsStr() {
		return topic;
	}
	public ByteArrayOutputStream getEvent() {
		return new ByteArrayOutputStream();
	}

	public String toString() {
		return topic;
	}
}

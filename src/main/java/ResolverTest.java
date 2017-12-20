
import com.aruba.acp.common.utils.message.MsgUtil;
//import com.aruba.acp.events.AcpEventWrapper;
import com.aruba.acp.hazelcast.HazelcastLocalClient;
import com.aruba.acp.hazelcast.config.HazelcastEndpointConfig;
import com.aruba.acp.hazelcast.portable.AcpKey;
import com.aruba.acp.hazelcast.portable.AcpPortable;
import com.aruba.acp.proto.Schema;
import com.google.protobuf.ByteString;
import com.typesafe.config.Config;

import org.zeromq.ZMQ;

/**
 * Created by balavigneshr on 4/11/17.
 */
public class ResolverTest {

	public ResolverTest(Config c) {
		HazelcastLocalClient client = new HazelcastLocalClient(new HazelcastEndpointConfig(c));
		try {
			ZMQ.Context context = ZMQ.context(1);
			ZMQ.Socket publisher = context.socket(ZMQ.PUB);
			publisher.bind("tcp://*:7779");

			Schema.acp_event.Builder acp_event = Schema.acp_event.newBuilder();
			Schema.wireless_controller.Builder wc = Schema.wireless_controller.newBuilder();
			Schema.ip_address.Builder ipAddr = Schema.ip_address.newBuilder();
			ipAddr.setAddr(ipToByteString("10.53.9.76"));
			ipAddr.setAf(Schema.ip_address.addr_family.ADDR_FAMILY_INET);
			wc.setControllerIpAddress(ipAddr.build());
			wc.setSerialNumber("124421212");
			wc.setUsername("admin");
			wc.setPassword("admins");
			acp_event.setWirelessController(wc);
			client.getMap("context_wireless_controller").put(new AcpKey("124421212"), new AcpPortable(acp_event.build()));

			Schema.mac_address.Builder macBuilder = Schema.mac_address.newBuilder();
			macBuilder.setAddr(macToByteString("e0:45:4f:00:00:24"));

			Schema.acp_event.Builder eventBldr = Schema.acp_event.newBuilder();
			eventBldr.getStationStatsBuilder().setStaEthMac(macBuilder);
			eventBldr.getStationStatsBuilder().setStaEthMac(macBuilder);
/*
			AcpEventWrapper eventWrapper = new AcpEventWrapper(MsgUtil.Topic.STATION_STATS, "10.53.9.76", "10.53.9.76",
					"124421212", "1000000", eventBldr, null);
			publisher.sendMore(eventWrapper.getTopic().getTopicName());
			publisher.send(eventWrapper.getEvent().toByteArray());
			publisher.close (); */
			context.term ();
		} catch (Exception e) {
			System.out.println("e = " + e);
		} finally {
			client.stop();
		}
	}

	private static ByteString macToByteString(String macAddress) {
		String[] macAddressParts = macAddress.split(":");
		byte[] macAddressBytes = new byte[6];
		for(int i=0; i<6; i++){
			Integer hex = Integer.parseInt(macAddressParts[i], 16);
			macAddressBytes[i] = hex.byteValue();
		}
		return ByteString.copyFrom(macAddressBytes);
	}

	private static ByteString ipToByteString(String ipAddress) {
		String[] ipAddressParts = ipAddress.split("\\.");

		byte[] ipAddressBytes = new byte[4];
		for(int i=0; i<4; i++){
			Integer integer = Integer.parseInt(ipAddressParts[i]);
			ipAddressBytes[i] = integer.byteValue();
		}
		return ByteString.copyFrom(ipAddressBytes);
	}
}


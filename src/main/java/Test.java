


import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.collections.impl.factory.Sets;
import org.slf4j.LoggerFactory;

import com.aruba.acp.ce.AppConfig;
import com.aruba.acp.ce.debug.MessageHelper;
import com.aruba.acp.device.iap.Iap_messages;
import com.aruba.acp.device.iap.IapPolicy.PolicyResp;
import com.aruba.acp.device.iap.IapPolicy.WebpageLoadCommand;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.vertx.core.json.JsonObject;
import com.aruba.acp.proto.Schema.acp_event;
import com.google.protobuf.ByteString;

public abstract class Test {

  protected Config conf;
  public Test() {

    setLogLevel("com.aruba.acp", "TRACE", Level.TRACE);
    setLogLevel("org.apache", "INFO", Level.INFO);
    conf = ConfigFactory.empty();
    String filename = Test.class.getClassLoader().getResource("reference.conf").getPath();
    try {
      conf = ConfigFactory.parseFile(new File(filename), ConfigParseOptions.defaults().setAllowMissing(false).setSyntax(ConfigSyntax.CONF));
    } catch (Exception e1) {
      System.err.println("Exception parsing config from \"" + filename + "\": " + e1.getMessage());
    }
  }


  protected static String decodeProtobuf(String topic, byte[] messageBody) {
    try {
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
        case "iap.policy.sareq":
          return MessageHelper.toPrettyString(WebpageLoadCommand.parseFrom(messageBody));
        case "state":
        case "aggregated.mc.state.ap":
        case "aggregated.mc.state.station":
          return MessageHelper.toPrettyString(acp_event.parseFrom(messageBody));
        
          
        default:
          return "Wrong topic or Topic not yet supported!";
      }
    } catch (Exception e) {
      System.out.println("Error decoding protobuf message - " + topic);
    }
    return "";
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
  
  public static ByteString ipToByteString(String ipAddress) {
    String[] ipAddressParts = ipAddress.split("\\.");

    byte[] ipAddressBytes = new byte[4];
    for(int i=0; i<4; i++){
        Integer integer = Integer.parseInt(ipAddressParts[i]);
        ipAddressBytes[i] = integer.byteValue();
    }
    return ByteString.copyFrom(ipAddressBytes);
}
}

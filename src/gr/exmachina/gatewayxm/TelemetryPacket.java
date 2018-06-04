package gr.exmachina.gatewayxm;

import java.util.HashMap;
import java.util.Map;

public class TelemetryPacket
{
	public String deviceName;
	public long timestamp;
	public Map<String, String> data = new HashMap<String, String>();
}

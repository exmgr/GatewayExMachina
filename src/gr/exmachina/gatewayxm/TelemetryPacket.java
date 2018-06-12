package gr.exmachina.gatewayxm;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Thingsboard telemetry packet
 * Can contain multiple key/vals
 *
 * @author Ex-Machina
 *
 */
public class TelemetryPacket
{
    /** Device name */
    private String _deviceName;
    /** Telemetry timestamp (mS) */
    private long _timestamp;
    /** Field/value data map */
    private Map<String, String> _data = new HashMap<String, String>();

    /**
     * Constructor
     */
    public TelemetryPacket()
    {

    }

    /**
     * Set device name
     */
    public void setDeviceName(String name)
    {
        _deviceName = name;
    }

    /**
     * Get device name
     */
    public String getDeviceName()
    {
        return _deviceName;
    }

    /**
     * Set telemetry timestamp (mS)
     */
    public void setTimestamp(long timestamp)
    {
        _timestamp = timestamp;
    }

    public long getTimestamp()
    {
        return _timestamp;
    }

    /**
     * Add telemetry data entry
     * @param name Field name
     * @param value Field value
     */
    public void addData(String name, String value)
    {
        _data.put(name, value);
    }

    /**
     * Get telemetry map
     */
    public Map<String, String> getData()
    {
        return _data;
    }
}
package gr.exmachina.gatewayxm;

import java.util.LinkedList;

import java.util.List;
import org.json.*;

/**
 * Forwards captured data to a thingsboard gateway device via paho Mqtt
 * 
 * @author Ex-Machina
 *
 */
public class TbForwarder implements Runnable
{

    /** Packet queue */
    List<DataPacket> m_listPackets = new LinkedList<DataPacket>();

    /** Forwarder thread */
    Thread thread;

    /** Manages mqtt connection */
    Mqtt m_mqtt;

    /** Keeps forwarder thread running */
    boolean doForward = false;

    /**
     * Constructor
     */
    public TbForwarder()
    {
        m_mqtt = new Mqtt();
    }

    /**
     * Initialize object and try to connect to mqtt broker
     */
    public boolean start()
    {
        // Thread already running
        if (thread != null && thread.isAlive())
            return false;

        // Attempt mqtt connection
        if (!m_mqtt.connect())
            return false;

        // All good, setup thread and start processing queue
        doForward = true;

        thread = new Thread(this);
        thread.setName(TbForwarder.class.toString());
        thread.start();

        return true;
    }

    public void stop()
    {
        doForward = false;
    }

    /**
     * Push data packet into the queue
     * 
     * @param packet
     */
    public void pushPacket(DataPacket packet)
    {
        synchronized (m_listPackets)
        {
            m_listPackets.add(packet);
            GatewayXM.logger.info("list size " + m_listPackets.size());
        }
    }

    /**
     * Transform packet to a JSON format that TB accepts
     * TB will create a device under the name packet.assetName and record a telemetry entry for the packet.channelName
     * timeseries
     * with the value packet.data at time packet.timestamp
     */
    private String transformPacket(DataPacket packet)
    {      
        JSONObject jsonParent = new JSONObject();
        JSONObject jsonValues = new JSONObject();
        
        jsonParent.put("ts", Long.toString(packet.timestamp));
        
        jsonValues.put(packet.channelName, packet.data);
        jsonParent.put("values", jsonValues);
       
        // { "Asset": [{"ts": 1527005515605, "values": {"Channel": 10}}] }
        
        GatewayXM.logger.info("JSON: " + "[" + jsonParent.toString() + "]");

        return "[" + jsonParent.toString() + "]";
    }

    /**
     * Forwarder thread
     */
    @Override
    public void run()
    {
        while (doForward)
        {
            DataPacket packet;

            // Pop packet and forward
            synchronized (m_listPackets)
            {
                if (m_listPackets.isEmpty())
                    continue;

                packet = m_listPackets.remove(0);
            }

            // Get tb gateway compatible json
            String json = transformPacket(packet);

            m_mqtt.publish(json);
        }

        m_mqtt.disconnect();
    }
}

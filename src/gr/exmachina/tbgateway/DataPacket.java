package gr.exmachina.tbgateway;

/**
 * Represents a single record captured from a modbus channel.
 * DataPackets are queued in TbForwarder for further transformation and forwarding
 * to the thingsboard gateway
 * 
 * @author Ex-Machina
 *
 */
public class DataPacket
{

    public String assetName, channelName, data, type;
    public long timestamp;
}

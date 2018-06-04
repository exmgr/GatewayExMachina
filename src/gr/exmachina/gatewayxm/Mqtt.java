package gr.exmachina.gatewayxm;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Wrapper class for basic mqtt operations
 * 
 * @author Ex-Machina
 *
 */
public class Mqtt
{

    /** Thingsboard gateway mqtt telemetry topic */
    public static final String MQTT_TELEMETRY_TOPIC = "v1/gateway/telemetry";
    /** Message QOS */
    public static final int MQTT_QOS = 2;

    /** Paho mqtt client object */
    MqttClient mqttClient;

    /** MQTT data persistence */
    MemoryPersistence persistence;

    /**
     * Constructor
     */
    public Mqtt()
    {
    }

    /**
     * Connect to the thingsboard mqtt broker and init mqttclient
     * 
     * @return False on fail
     */
    public boolean connect()
    {
        try
        {
            // Get mqtt config from properties
            String broker = String.format("%s:%s", GatewayXM.get_property(GatewayXM.PROP_TB_GW_BROKER_URL).toString(),
                    GatewayXM.get_property(GatewayXM.PROP_TB_GW_BROKER_PORT).toString());
            String clientId = GatewayXM.get_property(GatewayXM.PROP_MQTT_CLIENT_ID).toString();
            String username = GatewayXM.get_property(GatewayXM.PROP_TB_GW_ACCESS_TOKEN).toString();

            GatewayXM.logger.info(String.format("Connecting to MQTT broker: %s, username: %s, clientId: %s", broker,
                    username, clientId));

            persistence = new MemoryPersistence();
            mqttClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            connOpts.setUserName(username);

            mqttClient.connect(connOpts);

            GatewayXM.logger.info("MQTT Connected!");
        }
        catch (MqttException e)
        {
            GatewayXM.logger.info("Could not connect mqtt: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Disconnect from mqtt broker
     */
    public void disconnect()
    {
        if (mqttClient != null && mqttClient.isConnected())
        {
            try
            {
                mqttClient.disconnect();
            }
            catch (MqttException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Publish a message to the mqtt broker
     * 
     * @param content Mqtt payload
     * @return True on success
     */
    public boolean publish(String content)
    {
        if (!mqttClient.isConnected())
            return false;

        MqttMessage message = new MqttMessage(content.getBytes());

        message.setQos(MQTT_QOS);

        try
        {
            GatewayXM.logger.info("Publishing: " + message);
            mqttClient.publish(MQTT_TELEMETRY_TOPIC, message);
        }
        catch (MqttException e)
        {
            GatewayXM.logger.info("Could not publish message: " + e.getMessage());
            return false;
        }

        return true;
    }

    public boolean isConnected()
    {
        return mqttClient.isConnected();
    }
}

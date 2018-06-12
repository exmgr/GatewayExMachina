package gr.exmachina.gatewayxm;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens on local Artemis MQTT broker for telemetry coming from mesh sensor nodes 
 * and publishes to Thingsboard.
 *
 * @author Ex-Machina
 *
 */
public class SensorNodes implements MqttCallback
{
    /** Singleton instance */
    private static SensorNodes _inst = null;

    /** Sensor node telementry topic */
    public static final String SENSOR_TOPIC = "sensor_node_out/+/telemetry/";

    /** Regex to obtain sensor node device name from topic */
    public static final String DEVICE_NAME_REGEX = "sensor_node_out/(.*?)/telemetry/";

    /** Mqtt broker - Connects only to local broker */
    public static final String MQTT_BROKER_URL = "localhost";

    /** MQTT client id */
    final String MQTT_CLIENT_ID = "GatewayXM";

    /** Message QOS */
    public static final int MQTT_QOS = 2;

    /** Paho mqtt client object */
    MqttClient _mqttClient;

    /** Sensor broker MQTT username */
    String _mqttUsername = "";

    /** MQTT data persistence */
    MemoryPersistence _persistence;

    /** MQTT broker port */
    int _mqttBrokerPort;

    /** Thingsboard MQTT object */

    /**
     * Private constructor
     */
    private SensorNodes(){}

    /**
     * Get singleton instance
     */
    public static SensorNodes inst()
    {
        if(_inst == null)
            _inst = new SensorNodes();

        return _inst;
    }

    /**
     * Try to connect to broker with the credentials provided
     * @return True if successful
     */
    public boolean connectMqtt()
    {
        // Connect to broker
        try
        {
            String broker = String.format("tcp://%s:%d", MQTT_BROKER_URL, _mqttBrokerPort);

            GatewayXM.logger.info("Connecting to sensor node broker: " + broker);

            _persistence = new MemoryPersistence();
            _mqttClient = new MqttClient(broker, MQTT_BROKER_URL, _persistence);

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            if(_mqttUsername.length() > 0)
                connOpts.setUserName(_mqttUsername);

            _mqttClient.connect(connOpts);

            GatewayXM.logger.info("Connected to sensor node MQTT!");
        }
        catch (MqttException e)
        {
            GatewayXM.logger.info("Could not connect to sensor node mqtt: " + e.getMessage());
            return false;
        }
        catch (IllegalArgumentException e)
        {
            GatewayXM.logger.info("Invalid MQTT address/port: " + e.getMessage());
            return false;
        }

        // Subscribe to sensor node telemetry topic
        _mqttClient.setCallback(this);

        try
        {
            _mqttClient.subscribe(SENSOR_TOPIC);
        }
        catch (MqttException e)
        {
            System.out.printf(String.format("Could not subscribe to topic '%s' : %s", SENSOR_TOPIC, e.getMessage()));
            return false;
        }

        return true;
    }

    /**
     * Disconnect from mqtt broker
     */
    public void disconnectMqtt()
    {
        if (_mqttClient != null && _mqttClient.isConnected())
        {
            try
            {
                _mqttClient.disconnect();
                GatewayXM.logger.info("Disconnected from sensor node MQTT.");
            }
            catch (MqttException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Set sensor node MQTT username
     * @param username
     */
    public void setMqttUsername(String username)
    {
        _mqttUsername = username;
    }

    /**
     * Set sensor node MQTT port
     */
    public void setMqttPort(int port)
    {
        _mqttBrokerPort = port;
    }


    @Override
    public void connectionLost(Throwable throwable){}

    /**
     * Message arrived on sensor node telemetry topic (callback)
     * Scan the topic for the device name, if found build a telemetry packet and pass it to TB for publishing
     * @param
     * @param mqttMessage
     * @return
     * @throws Exception
     */
    @Override
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception
    {
        // Obtain device name from topic
        Matcher matcher = Pattern.compile(DEVICE_NAME_REGEX).matcher(s);
        String deviceName = "";

        System.out.println(mqttMessage.toString());

        if(matcher.find())
            deviceName = matcher.group(1);

        // Build packet
        if(deviceName.length() > 0)
        {
            TelemetryPacket packet = new TelemetryPacket();

            packet.setTimestamp(System.currentTimeMillis());
            packet.setDeviceName(deviceName);

            // Add all keys from root object
            JSONObject jsonData = new JSONObject(mqttMessage.toString());
            Iterator<String> iteratorKeys = jsonData.keys();

            while(iteratorKeys.hasNext())
            {
                String key = iteratorKeys.next();
                packet.addData(key, jsonData.get(key).toString());
            }

            ThingsboardMqtt.inst().publishTelemetry(packet);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken){}
}

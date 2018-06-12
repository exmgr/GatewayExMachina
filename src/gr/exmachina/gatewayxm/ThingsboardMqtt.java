package gr.exmachina.gatewayxm;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

/**
 * Connects to thingsboard MQTT gateway, manages connection of devices 
 * and publishing of telemetry
 *
 * @author Ex-Machina
 *
 */
public class ThingsboardMqtt
{
    /** Singleton instance */
    private static ThingsboardMqtt _inst = null;

    /** Thingsboard gateway mqtt telemetry topic */
    public static final String TELEMETRY_TOPIC = "v1/gateway/telemetry";

    /** Thingsboard gateway device connect topic */
    public static final String DEVICE_CONNECT_TOPIC = "v1/gateway/connect";

    /** Thingsboard gateway device disconnect topic */
    public static final String DEVICE_DISCONNECT_TOPIC = "v1/gateway/disconnect";

    /** Message QOS */
    public static final int MQTT_QOS = 2;

    /** Paho mqtt client object */
    private MqttClient _mqttClient;

    /** MQTT data persistence */
    private MemoryPersistence _persistence;

    /** Thingsboard broker url - must contain protocol (eg. tcp://) */
    private String _brokerUrl = "";

    /** Gateway device token - used as username */
    private String _gatewayDeviceToken = "";

    /** MQTT client id */
    private String _clientId = "";

    /** MQTT broker port */
    private int _brokerPort;

    /** Names of all connected devices */
    ArrayList<String> _listConnectedDevices = new ArrayList<>();

    /**
     * Private constructor
     */
    public ThingsboardMqtt(){}

    /**
     * Get singleton instance
     */
    public static ThingsboardMqtt inst()
    {
        if(_inst == null)
            _inst = new ThingsboardMqtt();

        return _inst;
    }

    /**
     * Try to connect to broker with the credentials provided
     * @return True if successful
     */
    public boolean connect()
    {
        if(_gatewayDeviceToken.length() < 1)
        {
            GatewayXM.logger.info("Thingsboard gateway device token is required.");
            return false;
        }
        if(_brokerUrl.length() < 1)
        {
            GatewayXM.logger.info("Thingsboard mqtt broker url is required.");
            return false;
        }
        if(_clientId.length() < 1)
        {
            GatewayXM.logger.info("Thingsboard mqtt client id is required.");
            return false;
        }

        try
        {
            String broker = String.format("tcp://%s:%d", _brokerUrl, _brokerPort);

            GatewayXM.logger.info("Connecting to thingsboard MQTT: " + broker);

            _persistence = new MemoryPersistence();
            _mqttClient = new MqttClient(broker, _clientId, _persistence);

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setUserName(_gatewayDeviceToken);

            _mqttClient.connect(connOpts);

            GatewayXM.logger.info("Connected to Thingsboard MQTT!");
        }
        catch (MqttException e)
        {
            GatewayXM.logger.info("Could not connect mqtt: " + e.getMessage());
            return false;
        }
        catch (IllegalArgumentException e)
        {
            GatewayXM.logger.info("Invalid MQTT address/port: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Disconnect from mqtt broker
     */
    public void disconnect()
    {
        if (_mqttClient != null && _mqttClient.isConnected())
        {
            try
            {
                _mqttClient.disconnect();
                GatewayXM.logger.info("Disconnected from thingsboard MQTT.");
            }
            catch (MqttException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Publish a telemetry packet to the broker, after transforming it to JSON for the tb gw telemetry api
     * @param packet Telemetry packet
     * @return
     */
    public boolean publishTelemetry(TelemetryPacket packet)
    {
        if(packet.getDeviceName().length() < 1)
            return false;

        // Connect device if its not connected
        connectDevice(packet.getDeviceName());

        JSONObject jsonRoot = new JSONObject();
        JSONArray jsonDeviceTelemetries = new JSONArray();
        JSONObject jsonSingleTelemetry = new JSONObject();
        JSONObject jsonTelemetryValues = new JSONObject();

        // Add delemetry data
        for(Map.Entry<String, String> data : packet.getData().entrySet())
        {
            jsonTelemetryValues.put(data.getKey(), data.getValue());
        }

        jsonSingleTelemetry.put("ts", String.valueOf(packet.getTimestamp()));
        jsonSingleTelemetry.put("values", jsonTelemetryValues);

        jsonDeviceTelemetries.put(jsonSingleTelemetry);

        // Add all to root element
        jsonRoot.put(packet.getDeviceName(), jsonDeviceTelemetries);

        return publish(jsonRoot.toString(), TELEMETRY_TOPIC);
    }

    /**
     * If device is not connected, publish "connect" message to TB broker, else ignore
     */
    public void connectDevice(String deviceName)
    {
        if(_listConnectedDevices.contains(deviceName))
            return;

        // Send "connect" message
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("device", deviceName);

        System.out.println("Connecting device " + deviceName);

        publish(jsonObject.toString(), DEVICE_CONNECT_TOPIC);

        synchronized (_listConnectedDevices)
        {
            _listConnectedDevices.add(deviceName);
        }
    }

    /**
     * If device connected, send disconnect message, else ignore
     * @param deviceName
     */
    public void disconnectDevice(String deviceName)
    {
        if(_listConnectedDevices.contains(deviceName))
            return;

        // Send "connect" message
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("device", deviceName);

        System.out.println("Disconnecting device " + deviceName);

        publish(jsonObject.toString(), DEVICE_DISCONNECT_TOPIC);

        synchronized (_listConnectedDevices)
        {
            _listConnectedDevices.remove(deviceName);
        }
    }

    /**
     * Send disconnect message for all devices
     */
    public void disconnectAllDevices()
    {
        for(String name : _listConnectedDevices)
            disconnectDevice(name);
    }

    /**
     * Generic function for publishing messages to the thingsboard broker
     * @param content Message content
     * @param topic Topic to publish to
     * @return True when successfull
     */
    private boolean publish(String content, String topic)
    {
        if (_mqttClient.isConnected() == false)
            return false;

        MqttMessage message = new MqttMessage(content.getBytes());

        message.setQos(MQTT_QOS);

        try
        {
            _mqttClient.publish(topic, message);

            GatewayXM.logger.info(String.format("Publishing to %s: %s", topic, message) );
        }
        catch (MqttException e)
        {
            GatewayXM.logger.info("Could not publish: " + e.getMessage());
            return false;
        }

        return true;
    }


    /**
     * Set thingsboard gateway device token - used as mqtt username
     * @param token
     */
    public void setGatewayDeviceToken(String token)
    {
        _gatewayDeviceToken = token;
    }

    /**
     * Mqtt client id
     * @param clientId
     */
    public void setClientId(String clientId)
    {
        _clientId = clientId;
    }

    /**m
     * Thingsboard mqtt broker url
     */
    public void setBrokerUrl(String url)
    {
        _brokerUrl = url;
    }

    /**
     * Mqtt broker port
     * @param port
     */
    public void setBrokerPort(int port)
    {
        _brokerPort = port;
    }
}

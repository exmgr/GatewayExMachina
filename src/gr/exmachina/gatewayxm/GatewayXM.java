package gr.exmachina.gatewayxm;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.driver.DriverService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Ex-Machina */
public class GatewayXM implements ConfigurableComponent
{

    /**
     * Property IDs
     */
    /** TB Gateway access token */
    public static final String PROP_TB_GW_ACCESS_TOKEN = "tb.gw_access_token";
    /** TB Gateway broker URI */
    public static final String PROP_TB_GW_BROKER_URL = "tb.broker_url";
    /** TB Gateway broker port */
    public static final String PROP_TB_GW_BROKER_PORT = "tb.broker_port";
    /** Modbus poll interval in ms */
    public static final String PROP_MQTT_CLIENT_ID = "mqtt.client_id";
    /** Modbus poll interval in ms */
    public static final String PROP_MODBUS_POLL_INTERVAL = "modbus.poll_interval";

    /**
     * Other constants
     */
    /** Modbus driver name, used to identify assets which belong to modbus driver instances */
    public static final String MODBUS_DRIVER_NAME = "org.eclipse.kura.internal.driver.modbus.ModbusDriver";

    /**
     * Objects
     */
    /** Logger object */
    public static Logger logger = LoggerFactory.getLogger(GatewayXM.class);

    /** Kura Asset service, for accessing all asset related functions */
    private static AssetService m_assetService;

    /** Kura Asset service, for accessing all driver related functions */
    private static DriverService m_driverService;

    /** Bundle properties map, updated on activate() and update(); */
    private static Map<String, Object> m_properties;

    /** Polls for data on ModBus */
    private static ModbusPoller m_modbusPoller;
    /** Forwards read modbus data to thingsboard */
    private static TbForwarder m_tbForwarder;

    /**
     * Called by Kura on bundle activate.
     * 
     * @param componentContext component context
     * 
     */
    protected void activate(ComponentContext componentContext)
    {
        logger.info("Gateway activated.");

        m_modbusPoller = new ModbusPoller();
        m_tbForwarder = new TbForwarder();
    }

    /**
     * Called by Kura on bundle activate, when configuration is available
     * 
     * @param componentContext
     *            Component context
     * @param properties
     *            Bundle configuration
     */
    protected void activate(ComponentContext componentContext, Map<String, Object> properties)
    {
        updated(properties);
    }

    /**
     * Called by Kura on bundle deactivate
     * 
     * @param componentContext
     */
    protected void deactivate(ComponentContext componentContext)
    {
        m_modbusPoller.stop();
        m_tbForwarder.stop();

        logger.info("Gateway Deactivated.");
    }

    /**
     * Called by Kura when bundle configuration is updated
     * 
     * @param properties New bundle config
     */
    public void updated(Map<String, Object> properties)
    {
        logger.info("Gateway configuration updated.");

        // Stop modbus and forwarder before updating configuration
        m_modbusPoller.stop();
        m_tbForwarder.stop();

        m_properties = properties;

        if (properties != null && !properties.isEmpty())
        {
            Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
            while (it.hasNext())
            {
                Entry<String, Object> entry = it.next();
                logger.info("New property - " + entry.getKey() + " = " + entry.getValue() + " of type "
                        + entry.getValue().getClass().toString());
            }
        }

        // Restart forwarder and modbus poller
        if (m_tbForwarder.start())
            m_modbusPoller.start();
    }

    /**
     * Get property from bundle's config (set from kura web ui)
     * 
     * @param Property id
     * @return
     */
    public static Object get_property(String key)
    {
        return m_properties.get(key);
    }

    /** Called by Kura to set the DriverService */
    public void setDriverService(DriverService driverService)
    {
        logger.info("Set driver service.");
        m_driverService = driverService;
    }

    /** Called by Kura to unset the DriverService */
    public void unsetDriverService(DriverService driverService)
    {
        logger.info("Unset driver service.");
        m_driverService = null;
    }

    /** Called by Kura to set the AssetService */
    public void setAssetService(AssetService assetService)
    {
        logger.info("Setting the asset service.");
        m_assetService = assetService;
    }

    /** Called by Kura to unset the AssetService */
    public void unsetAssetService(AssetService assetService)
    {
        logger.info("Unsetting the asset service.");
        m_assetService = null;
    }

    /** Getter */
    public static DriverService getDriverService()
    {
        return m_driverService;
    }

    /** Getter */
    public static AssetService getAssetService()
    {
        return m_assetService;
    }

    /** Getter */
    public static ModbusPoller getModbusPoller()
    {
        return m_modbusPoller;
    }

    /** Setter */
    public static TbForwarder getTbForwarder()
    {
        return m_tbForwarder;
    }
}

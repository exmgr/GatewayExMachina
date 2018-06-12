package gr.exmachina.gatewayxm;

import java.util.List;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.asset.Asset;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.driver.Driver;

/**
 * Polls AssetService for assets, posts read requests on their channels and forwards
 * returned data to TbForwarder.
 *
 * @author Ex-Machina
 *
 */
public class ModbusPoller implements Runnable
{
	/** Singleton instance */
	private static ModbusPoller _inst = null;
	
    /** Poller thread */
    Thread _thread;

    /** Keeps poller thread running */
    private boolean _isActive = false;
	
	/**
	 * Private constructor
	 */
	private ModbusPoller() {}
	
	/**
	 * Get singleton instance
	 */
	public static ModbusPoller inst()
	{
		if(_inst == null)
			_inst = new ModbusPoller();
		
		return _inst;
	}
	
    /**
     * Init polling thread and start polling for data on assets
     *
     * @return True on success
     */
    public boolean start()
    {
        // Thread already running
        if (_thread != null && _thread.isAlive())
            return false;

        _thread = new Thread(this);
        _thread.setName(ModbusPoller.class.toString());
        _thread.start();
        _isActive = true;

        return true;
    }
    
    /**
     * Stop polling
     */
    public void stop()
    {
        _isActive = false;
    }

	@Override
	public void run()
	{
		GatewayXM.logger.info("Poller started.");

        while (_isActive)
        {
            GatewayXM.logger.info("Waiting to read modbus...");
            // Wait X seconds before reading modbus again
            try
            {
                int pollInterval = Integer
                        .parseInt(GatewayXM.getConfigProperty(GatewayXM.PROP_MODBUS_POLL_INTERVAL).toString());
                Thread.sleep(pollInterval);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            // Asset service not yet set. Drivers and Services may take a while to appear after Kura starts, abort for
            // now
            if (GatewayXM.getDriverService() == null || GatewayXM.getAssetService() == null)
                continue;

            // Iterate through all assets, ignore those that do not belong to the modbus driver
            for (Asset asset : GatewayXM.getAssetService().listAssets())
            {
                String driverPid = asset.getAssetConfiguration().getDriverPid();

                // Get driver by asset's driver_pid. This is needed ONLY to check if asset is under a modbus driver
                Driver driver = GatewayXM.getDriverService().getDriver(driverPid);

                // Assets that were created for a driver that no longer exists return null
                if (driver == null)
                    continue;

                // Asset must belong to the modbus driver
                if (driver.getClass().getName() != GatewayXM.MODBUS_DRIVER_NAME)
                    continue;

                List<ChannelRecord> channelRecords = null;
                try
                {
                    channelRecords = asset.readAllChannels();
                }
                catch (KuraException e)
                {
                    GatewayXM.logger.info("Error while reading channels: " + e.getMessage());
                }

                if (channelRecords == null)
                    continue;

                for (ChannelRecord rec : channelRecords)
                {
                    // Read failed on channel, ignore
                    if (rec.getChannelStatus().getChannelFlag() != ChannelFlag.SUCCESS)
                    {
                        GatewayXM.logger.info("Could not read value for ModBus channel: " + rec.getChannelName());
                        continue;
                    }

                    GatewayXM.logger
                            .info("Read: " + rec.getValue().getValue() + " from Channel " + rec.getChannelName());

                    // Populate new DataPacket obj and queue
                    TelemetryPacket packet = new TelemetryPacket();
                    packet.setDeviceName(GatewayXM.getAssetService().getAssetPid(asset));
                    packet.setTimestamp(System.currentTimeMillis());
                    packet.addData(rec.getChannelName(), rec.getValue().getValue().toString());

                    ThingsboardMqtt.inst().publishTelemetry(packet);

//	                    DataPacket packet = new DataPacket();
//	                    packet.assetName = GatewayXM.getAssetService().getAssetPid(asset);
//	                    packet.channelName = rec.getChannelName();
//	                    packet.data = rec.getValue().getValue().toString();
//	                    packet.type = rec.getValueType().toString(); // temp
//	                    packet.timestamp = System.currentTimeMillis();

//	                    GatewayXM.getTbForwarder().pushPacket(packet);
                }
            }
        }
	}
	
	
}
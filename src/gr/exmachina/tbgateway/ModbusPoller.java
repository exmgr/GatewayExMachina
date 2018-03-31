package gr.exmachina.tbgateway;

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

    /** Poller thread */
    Thread thread;

    /** Keeps poller thread running */
    private boolean doPoll = false;

    public ModbusPoller()
    {
    }

    /**
     * Init polling thread and start polling for data on assets
     * 
     * @return True on success
     */
    public boolean start()
    {
        // Thread already running
        if (thread != null && thread.isAlive())
            return false;

        thread = new Thread(this);
        thread.setName(ModbusPoller.class.toString());
        thread.start();
        doPoll = true;

        return true;
    }

    /**
     * Stop polling
     */
    public void stop()
    {
        doPoll = false;
    }

    /**
     * 
     */
    @Override
    public void run()
    {
        TbGateway.logger.info("Poller started.");

        while (doPoll)
        {
            TbGateway.logger.info("Waiting to read modbus...");
            // Wait X seconds before reading modbus again
            try
            {
                int pollInterval = Integer
                        .parseInt(TbGateway.get_property(TbGateway.PROP_MODBUS_POLL_INTERVAL).toString());
                Thread.sleep(pollInterval);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            // Asset service not yet set. Drivers and Services may take a while to appear after Kura starts, abort for
            // now
            if (TbGateway.getDriverService() == null || TbGateway.getAssetService() == null)
                continue;

            // Iterate through all assets, ignore those that do not belong to the modbus driver
            for (Asset asset : TbGateway.getAssetService().listAssets())
            {
                String driverPid = asset.getAssetConfiguration().getDriverPid();

                // Get driver by asset's driver_pid. This is needed ONLY to check if asset is under a modbus driver
                Driver driver = TbGateway.getDriverService().getDriver(driverPid);

                // Assets that were created for a driver that no longer exists return null
                if (driver == null)
                    continue;

                // Asset must belong to the modbus driver
                if (driver.getClass().getName() != TbGateway.MODBUS_DRIVER_NAME)
                    continue;

                List<ChannelRecord> channelRecords = null;
                try
                {
                    channelRecords = asset.readAllChannels();
                }
                catch (KuraException e)
                {
                    TbGateway.logger.info("Error while reading channels: " + e.getMessage());
                }

                if (channelRecords == null)
                    continue;

                for (ChannelRecord rec : channelRecords)
                {
                    // Read failed on channel, ignore
                    if (rec.getChannelStatus().getChannelFlag() != ChannelFlag.SUCCESS)
                    {
                        TbGateway.logger.info("Could not read value for ModBus channel: " + rec.getChannelName());
                        continue;
                    }

                    TbGateway.logger
                            .info("Read: " + rec.getValue().getValue() + " from Channel " + rec.getChannelName());

                    // Populate new DataPacket obj and queue
                    DataPacket packet = new DataPacket();
                    packet.assetName = TbGateway.getAssetService().getAssetPid(asset);
                    packet.channelName = rec.getChannelName();
                    packet.data = rec.getValue().getValue().toString();
                    packet.type = rec.getValueType().toString(); // temp
                    packet.timestamp = System.currentTimeMillis();

                    TbGateway.getTbForwarder().pushPacket(packet);

                }
            }
        }
    }
}

package com.mood.cheatacx;

import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import org.cowboycoders.ant.Channel;
import org.cowboycoders.ant.Node;
import org.cowboycoders.ant.events.MessageCondition;
import org.cowboycoders.ant.events.MessageConditionFactory;
import org.cowboycoders.ant.interfaces.AntTransceiver;
import org.cowboycoders.ant.messages.ChannelType;
import org.cowboycoders.ant.messages.SlaveChannelType;
import org.cowboycoders.ant.messages.commands.ChannelRequestMessage;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;
import org.cowboycoders.ant.messages.responses.ChannelIdResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /*
     * See ANT+ data sheet for explanation
     */
    private static final int ANT_SPORT_SPEED_PERIOD = 8086;

    /*
     * See ANT+ data sheet for explanation
     */
    private static final int ANT_SPORT_FREQ = 57; // 0x39

    /*
     * This should match the device you are connecting with. Some devices are
     * put into pairing mode (which sets this bit).
     *
     * Note: Many ANT+ sport devices do not set this bit (eg. HRM strap).
     *
     * See ANT+ docs.
     */
    private static final boolean HRM_PAIRING_FLAG = false;

    /*
     * Should match device transmission id (0-255). Special rules apply for
     * shared channels. See ANT+ protocol.
     *
     * 0: wildcard, matches any value (slave only)
     */
    private static final int HRM_TRANSMISSION_TYPE = 0;

	/*
     * device type for ANT+ heart rate monitor
	 */

    private static final int ANT_SPORT_SandC_TYPE = 121; // 0x78

    /*
     * You should make a note of the device id and use it in preference to the
     * wild card to pair to a specific device.
     *
     * 0: wild card, matches all device ids any other number: match specific
     * device id
     */
    private static final int HRM_DEVICE_ID = 0;

    private static final Level LOG_LEVEL = Level.SEVERE;

    private static void setupLogging() {
        // set logging level
        AntTransceiver.LOGGER.setLevel(LOG_LEVEL);
        ConsoleHandler handler = new ConsoleHandler();
        // PUBLISH this level
        handler.setLevel(LOG_LEVEL);
        AntTransceiver.LOGGER.addHandler(handler);
    }

    private static void printChannelConfig(Channel channel) {
        // build request
        ChannelRequestMessage msg = new ChannelRequestMessage(channel.getNumber(), ChannelRequestMessage.Request.CHANNEL_ID);

        // response should be an instance of ChannelIdResponse
        MessageCondition condition = MessageConditionFactory.newInstanceOfCondition(ChannelIdResponse.class);

        try {
            // send request (blocks until reply received or timeout expired)
            ChannelIdResponse response = (ChannelIdResponse) channel.sendAndWaitForMessage(msg, condition, 5L, TimeUnit.SECONDS, null);

            log.info("\n");
            log.info("Device configuration: ");
            log.info("deviceID: " + response.getDeviceNumber());
            log.info("deviceType: " + response.getDeviceType());
            log.info("transmissionType: " + response.getTransmissionType());
            log.info("pairing flag set: " + response.isPairingFlagSet());
            log.info("\n");

        } catch (Exception e) {
            // not critical, so just print error
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // optional: enable console logging with Level = LOG_LEVEL
        setupLogging();

		/*
         * Choose driver: AndroidAntTransceiver or AntTransceiver
		 *
		 * AntTransceiver(int deviceNumber) deviceNumber : 0 ... number of usb
		 * sticks plugged in 0: first usb ant-stick
		 */
        AntTransceiver antchip = new AntTransceiver(0);
        antchip.setPolicy(usbInterface -> true);

        // initialises node with chosen driver
        Node node = new Node(antchip);

		/* must be called before any configuration takes place */
        node.start();

		/* sends reset request : resets channels to default state */
        node.reset();

        // specs say wait 500ms after reset before sending any more host
        // commands
        Thread.sleep(500);

        Channel channel = node.getFreeChannel();

        // Arbitrary name : useful for identifying channel
        channel.setName("C:CheaTacx");

        // choose slave or master type. Constructors exist to set
        // two-way/one-way and shared/non-shared variants.
        ChannelType channelType = new SlaveChannelType();

        // use ant network key "N:ANT+"
        channel.assign(NetworkKeys.ANT_SPORT, channelType);

        // registers an instance of our callback with the channel
        channel.registerRxListener(new HeartRateListener(), BroadcastDataMessage.class);

        /******* start device specific configuration ******/

        channel.setId(HRM_DEVICE_ID, ANT_SPORT_SandC_TYPE, HRM_TRANSMISSION_TYPE, HRM_PAIRING_FLAG);

        channel.setFrequency(ANT_SPORT_FREQ);

        channel.setPeriod(ANT_SPORT_SPEED_PERIOD);

        /******* end device specific configuration ******/

        // timeout before we give up looking for device
        channel.setSearchTimeout(Channel.SEARCH_TIMEOUT_NEVER);

        // start listening
        channel.open();

        Runnable stopper = () -> {
            // stop listening
            channel.close();

            // optional : demo requesting of channel configuration. If device
            // connected
            // this will reflect actual device id, transmission type etc. This info
            // will allow
            // you to only connect to this device in the future.
            printChannelConfig(channel);

            // resets channel configuration
            channel.unassign();

            // return the channel to the pool of available channels
            node.freeChannel(channel);

            // cleans up : gives up control of usb device etc.
            node.stop();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(stopper));

        Thread waiter = new Thread(() -> {
            try {
                Object handle = new Object();
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (handle) {
                    handle.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();
        waiter.join();
    }
}

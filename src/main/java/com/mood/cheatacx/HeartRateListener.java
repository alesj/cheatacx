package com.mood.cheatacx;

import org.cowboycoders.ant.events.BroadcastListener;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heart rate ANT+ processor.
 */
class HeartRateListener implements BroadcastListener<BroadcastDataMessage> {
    private static final Logger log = LoggerFactory.getLogger(HeartRateListener.class);

    @Override
    /*
     * getData() returns the 8 byte payload. The current heart rate
     * is contained in the last byte.
     * 
     * Note: remember the lack of unsigned bytes in java, so unsigned values
     * should be converted to ints for any arithmetic / display - getUnsignedData()
     * is a utility method to do this.
     */    
    public void receiveMessage(BroadcastDataMessage message) {
        log.info("Heart rate: " + message.getUnsignedData()[7]);
    }
}

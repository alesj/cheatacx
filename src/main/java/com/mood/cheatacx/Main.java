package com.mood.cheatacx;

import java.io.OutputStream;

import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Main {
    private static final Object lock = new Object();
    private RemoteDevice device;

    public static void main(String[] args) {
        try {
            Main main = new Main();
            main.doDiscovery();
            if (main.device != null) {
                main.discoverServices();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDiscovery() {
        try {
            // 1
            LocalDevice localDevice = LocalDevice.getLocalDevice();

            // 2
            DiscoveryAgent agent = localDevice.getDiscoveryAgent();

            // 3
            agent.startInquiry(DiscoveryAgent.GIAC, new MyDiscoveryListener());

            System.out.println("Device Inquiry Started. ");
            try {
                synchronized (lock) {
                    lock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Device Inquiry Completed. ");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void discoverServices() throws Exception {
        UUID[] uuidSet = new UUID[1];
        uuidSet[0] = new UUID(0x1105); //OBEX Object Push service

        int[] attrIDs = new int[]{
            0x0100 // Service name
        };

        LocalDevice localDevice = LocalDevice.getLocalDevice();
        DiscoveryAgent agent = localDevice.getDiscoveryAgent();
        agent.searchServices(null, uuidSet, device, new MyDiscoveryListener());

        System.out.println("Service Search Started. ");
        try {
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public class MyDiscoveryListener implements DiscoveryListener {

        @Override
        public void deviceDiscovered(RemoteDevice btDevice, DeviceClass arg1) {
            Main.this.device = btDevice;
            String name;
            try {
                name = btDevice.getFriendlyName(false);
            } catch (Exception e) {
                name = btDevice.getBluetoothAddress();
            }

            System.out.println("device found: " + name);

        }

        @Override
        public void inquiryCompleted(int arg0) {
            synchronized (lock) {
                lock.notify();
            }
        }

        @Override
        public void serviceSearchCompleted(int arg0, int arg1) {
            synchronized (lock) {
                lock.notify();
            }
        }

        @Override
        public void servicesDiscovered(int arg0, ServiceRecord[] services) {
            for (int i = 0; i < services.length; i++) {
                String url = services[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                if (url == null) {
                    continue;
                }

                DataElement serviceName = services[i].getAttributeValue(0x0100);
                if (serviceName != null) {
                    System.out.println("service " + serviceName.getValue() + " found " + url);
                } else {
                    System.out.println("service found " + url);
                }

                if (serviceName.getValue().equals("OBEX Object Push")) {
                    //sendMessageToDevice(url);
                }
            }

        }
    }

    private static void sendMessageToDevice(String serverURL) {
        try {
            System.out.println("Connecting to " + serverURL);

            ClientSession clientSession = (ClientSession) Connector.open(serverURL);
            HeaderSet hsConnectReply = clientSession.connect(null);
            if (hsConnectReply.getResponseCode() != ResponseCodes.OBEX_HTTP_OK) {
                System.out.println("Failed to connect");
                return;
            }

            HeaderSet hsOperation = clientSession.createHeaderSet();
            hsOperation.setHeader(HeaderSet.NAME, "Hello.txt");
            hsOperation.setHeader(HeaderSet.TYPE, "text");

            //Create PUT Operation
            Operation putOperation = clientSession.put(hsOperation);

            // Sending the message
            byte data[] = "Hello World !!!".getBytes("iso-8859-1");
            OutputStream os = putOperation.openOutputStream();
            os.write(data);
            os.close();

            putOperation.close();
            clientSession.disconnect(null);
            clientSession.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

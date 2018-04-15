package rocks.androidthings.arduwrap;

import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Copyright (C) 2017 mplacona.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class Dht22Driver implements BaseSensor, AutoCloseable {
    private static final String TAG = "Dht22Driver";
    private static final int CHUNK_SIZE = 10;
    private ByteBuffer mMessageBuffer = ByteBuffer.allocate(CHUNK_SIZE);
    private final Arduino arduino;

    private UartDevice mDevice;
    private boolean receiving;

    public Dht22Driver(Arduino arduino){
        this.arduino = arduino;
    }

    @Override
    public void startup() {
        PeripheralManager mPeripheralManagerService = PeripheralManager.getInstance();

        try {
            mDevice = mPeripheralManagerService.openUartDevice(arduino.getUartDeviceName());
            mDevice.setDataSize(arduino.getDataBits());
            mDevice.setParity(UartDevice.PARITY_NONE);
            mDevice.setStopBits(arduino.getStopBits());
            mDevice.setBaudrate(arduino.getBaudRate());
        } catch (IOException e){
            try {
                close();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            throw new IllegalStateException("Sensor can't start", e);
        }
    }

    public String getTemperature() {
        String mode = "T";
        String response = "";
        byte[] buffer = new byte[CHUNK_SIZE];

        try {
            response = fillBuffer(buffer, mode);
        } catch (IOException e) {

            e.printStackTrace();
        }
        return response;
    }


    public String getHumidity() {
        String mode = "H";
        String response = "";
        byte[] buffer = new byte[10];

        try {
            response = fillBuffer(buffer, mode);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    private String fillBuffer(byte[] buffer, String mode) throws IOException {
        mDevice.write(mode.getBytes(), mode.length());
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mDevice.read(buffer, buffer.length);

        // clear out buffer
        processBuffer(buffer, buffer.length);

        // return new buffer trimmed
        return new String(mMessageBuffer.array(), "UTF-8").replaceAll("\0", "");
    }

    private void processBuffer(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            if (0x24 == buffer[i]) {
                receiving = true;
            } else if (0x23 == buffer[i]) {
                receiving = false;
                mMessageBuffer.clear();
            } else {
                //Insert all other characters into the buffer
                if(receiving && buffer[i] != 0x00)
                    mMessageBuffer.put(buffer[i]);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if(mDevice != null){
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    @Override
    public void shutdown() {
        if (mDevice != null) {
            try {
                mDevice.close();
                mDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close UART device", e);
            }
        }
    }
}

package com.ultraaudio.recorder.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Broadcast receiver for USB audio device attach/detach events.
 */
public class UsbAudioReceiver extends BroadcastReceiver {
    private static final String TAG = "UsbAudioReceiver";
    
    private static List<UsbDeviceListener> listeners = new ArrayList<>();
    
    public interface UsbDeviceListener {
        void onUsbDeviceAttached(UsbDevice device);
        void onUsbDeviceDetached(UsbDevice device);
    }
    
    public static void addListener(UsbDeviceListener listener) {
        listeners.add(listener);
    }
    
    public static void removeListener(UsbDeviceListener listener) {
        listeners.remove(listener);
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device == null) return;
        
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                Log.i(TAG, "USB device attached: " + device.getDeviceName() + 
                    " VID:" + device.getVendorId() + " PID:" + device.getProductId());
                for (UsbDeviceListener listener : listeners) {
                    listener.onUsbDeviceAttached(device);
                }
                break;
                
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                Log.i(TAG, "USB device detached: " + device.getDeviceName());
                for (UsbDeviceListener listener : listeners) {
                    listener.onUsbDeviceDetached(device);
                }
                break;
        }
    }
}

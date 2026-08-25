package com.rodo.posoptica;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Iterator;

/**
 * Habla directo con una impresora térmica ESC/POS conectada por USB
 * usando el UsbManager nativo de Android (host mode), sin depender de
 * WebUSB del navegador ni de conexión a internet.
 */
@CapacitorPlugin(name = "EscposUsb")
public class EscposUsbPlugin extends Plugin {

    private static final String ACTION_USB_PERMISSION = "com.rodo.posoptica.USB_PERMISSION";

    private PluginCall pendingPermissionCall;
    private UsbDeviceConnection connection;
    private UsbInterface claimedInterface;
    private UsbEndpoint outEndpoint;
    private UsbDevice currentDevice;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            synchronized (this) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                PluginCall call = pendingPermissionCall;
                pendingPermissionCall = null;
                JSObject result = new JSObject();
                boolean connected = false;
                if (granted && device != null) {
                    connected = openDevice(device);
                }
                result.put("granted", granted);
                result.put("connected", connected);
                if (call != null) call.resolve(result);
            }
        }
    };

    private UsbManager usbManager() {
        return (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (receiverRegistered) {
            try { getContext().unregisterReceiver(permissionReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        closeConnectionQuietly();
    }

    @PluginMethod
    public void listDevices(PluginCall call) {
        JSObject result = new JSObject();
        JSArray arr = new JSArray();
        Iterator<UsbDevice> it = usbManager().getDeviceList().values().iterator();
        while (it.hasNext()) {
            UsbDevice device = it.next();
            JSObject d = new JSObject();
            d.put("deviceId", device.getDeviceId());
            d.put("deviceName", device.getDeviceName());
            d.put("vendorId", device.getVendorId());
            d.put("productId", device.getProductId());
            d.put("hasPermission", usbManager().hasPermission(device));
            arr.put(d);
        }
        result.put("devices", arr);
        call.resolve(result);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        Integer deviceId = call.getInt("deviceId");
        if (deviceId == null) {
            call.reject("Falta deviceId");
            return;
        }
        UsbDevice device = findDevice(deviceId);
        if (device == null) {
            call.reject("Dispositivo no encontrado. Vuelve a listar los dispositivos.");
            return;
        }
        if (usbManager().hasPermission(device)) {
            boolean connected = openDevice(device);
            JSObject result = new JSObject();
            result.put("granted", true);
            result.put("connected", connected);
            call.resolve(result);
            return;
        }
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(permissionReceiver, filter);
            }
            receiverRegistered = true;
        }
        pendingPermissionCall = call;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(getContext(), 0, new Intent(ACTION_USB_PERMISSION), flags);
        usbManager().requestPermission(device, pi);
    }

    private UsbDevice findDevice(int deviceId) {
        Iterator<UsbDevice> it = usbManager().getDeviceList().values().iterator();
        while (it.hasNext()) {
            UsbDevice device = it.next();
            if (device.getDeviceId() == deviceId) return device;
        }
        return null;
    }

    /** Busca una interfaz con endpoint bulk OUT y abre la conexión. */
    private boolean openDevice(UsbDevice device) {
        closeConnectionQuietly();
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint ep = intf.getEndpoint(e);
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    UsbDeviceConnection conn = usbManager().openDevice(device);
                    if (conn == null) return false;
                    if (!conn.claimInterface(intf, true)) {
                        conn.close();
                        return false;
                    }
                    connection = conn;
                    claimedInterface = intf;
                    outEndpoint = ep;
                    currentDevice = device;
                    return true;
                }
            }
        }
        return false;
    }

    @PluginMethod
    public void print(PluginCall call) {
        String base64 = call.getString("dataBase64");
        if (base64 == null) {
            call.reject("Falta dataBase64");
            return;
        }
        if (connection == null || outEndpoint == null) {
            call.reject("La impresora no está conectada.");
            return;
        }
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        int sent = connection.bulkTransfer(outEndpoint, bytes, bytes.length, 8000);
        JSObject result = new JSObject();
        result.put("success", sent >= 0);
        result.put("bytesSent", sent);
        if (sent < 0) {
            call.reject("No se pudieron enviar los datos a la impresora.");
        } else {
            call.resolve(result);
        }
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject result = new JSObject();
        result.put("connected", connection != null);
        if (currentDevice != null) {
            result.put("deviceName", currentDevice.getDeviceName());
            result.put("vendorId", currentDevice.getVendorId());
            result.put("productId", currentDevice.getProductId());
        }
        call.resolve(result);
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        closeConnectionQuietly();
        call.resolve();
    }

    private void closeConnectionQuietly() {
        try {
            if (claimedInterface != null && connection != null) {
                connection.releaseInterface(claimedInterface);
            }
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
        connection = null;
        claimedInterface = null;
        outEndpoint = null;
        currentDevice = null;
    }
}

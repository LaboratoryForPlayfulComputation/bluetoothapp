package com.bluetooth.mwoolley.microbitbledemo.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.bluetooth.mwoolley.microbitbledemo.Constants;
import com.bluetooth.mwoolley.microbitbledemo.MicroBit;
import com.bluetooth.mwoolley.microbitbledemo.R;
import com.bluetooth.mwoolley.microbitbledemo.Utility;
import com.bluetooth.mwoolley.microbitbledemo.bluetooth.BleAdapterService;
import com.bluetooth.mwoolley.microbitbledemo.bluetooth.ConnectionStatusListener;

import java.io.UnsupportedEncodingException;

public class UartKVActivity extends AppCompatActivity implements ConnectionStatusListener {

    private BleAdapterService bluetooth_le_adapter;

    private boolean exiting=false;
    private boolean indications_on=false;
    private int guess_count=0;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(Constants.TAG, "onServiceConnected");
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(mMessageHandler);

            if (bluetooth_le_adapter.setIndicationsState(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID), true)) {
                showMsg(Utility.htmlColorGreen("UART TX indications ON"));
            } else {
                showMsg(Utility.htmlColorRed("Failed to set UART TX indications ON"));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    @Override
    public void connectionStatusChanged(boolean connected) {
        if (connected) {
            showMsg(Utility.htmlColorGreen("Connected"));
        } else {
            showMsg(Utility.htmlColorRed("Disconnected"));
        }
    }

    @Override
    public void serviceDiscoveryStatusChanged(boolean new_state) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uart_kv);
        getSupportActionBar().setTitle(R.string.screen_title_UART_KV);
        // read intent data
        final Intent intent = getIntent();
        MicroBit.getInstance().setConnection_status_listener(this);

        // connect to the Bluetooth smart service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        Log.d(Constants.TAG, "onDestroy");
        super.onDestroy();
        if (indications_on) {
            exiting = true;
            bluetooth_le_adapter.setIndicationsState(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID), false);
        }
        try {
            // may already have unbound. No API to check state so....
            unbindService(mServiceConnection);
        } catch (Exception e) {
        }
    }
    public void onBackPressed() {
        Log.d(Constants.TAG, "onBackPressed");
        if (MicroBit.getInstance().isMicrobit_connected() && indications_on) {
            exiting = true;
            bluetooth_le_adapter.setIndicationsState(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID), false);
        }
        exiting=true;
        if (!MicroBit.getInstance().isMicrobit_connected()) {
            try {
                // may already have unbound. No API to check state so....
                unbindService(mServiceConnection);
            } catch (Exception e) {
            }
        }
        finish();
        exiting=true;
    }

    // kari didn't copy onCreateOptionsMenu or onOptionItemsSelected from avm, since not changing options

    private void showMsg(final String msg) {
        Log.d(Constants.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) UartKVActivity.this.findViewById(R.id.message)).setText(Html.fromHtml(msg));
            }
        });
    }

    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            String descriptor_uuid = "";
            byte[] b = null;
            TextView value_text = null;

            switch (msg.what) {
                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    Log.d(Constants.TAG, "Handler received characteristic written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    Log.d(Constants.TAG, "characteristic " + characteristic_uuid + " of service " + service_uuid + " written OK");
                    showMsg(Utility.htmlColorGreen("Ready"));
                    break;
                case BleAdapterService.GATT_DESCRIPTOR_WRITTEN:
                    Log.d(Constants.TAG, "Handler received descriptor written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    descriptor_uuid = bundle.getString(BleAdapterService.PARCEL_DESCRIPTOR_UUID);
                    Log.d(Constants.TAG, "descriptor " + descriptor_uuid + " of characteristic " + characteristic_uuid + " of service " + service_uuid + " written OK");
                    if (!exiting) {
                        showMsg(Utility.htmlColorGreen("UART TX indications ON"));
                        indications_on=true;
                    } else {
                        showMsg(Utility.htmlColorGreen("UART TX indications OFF"));
                        indications_on=false;
                        finish();
                    }
                    break;

                case BleAdapterService.NOTIFICATION_OR_INDICATION_RECEIVED:
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                    Log.d(Constants.TAG, "Value=" + Utility.byteArrayAsHexString(b));
                    if (characteristic_uuid.equalsIgnoreCase((Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID)))) {
                        String ascii="";
                        Log.d(Constants.TAG, "UART TX received");
                        try {
                            ascii = new String(b,"US-ASCII");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            showMsg(Utility.htmlColorGreen("Could not convert TX data to ASCII"));
                            return;
                        }
                        Log.d(Constants.TAG, "micro:bit sent: " + ascii);

                        showReceivedMessage(ascii);

                    }
                    break;
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(Utility.htmlColorRed(text));
            }
        }
    };
    private void showReceivedMessage(final String mesg) {

        runOnUiThread(new Runnable() {
            @Override

            public void run() {
             //   ((TextView) UartKVActivity.this.findViewById(R.id.kv_string_recvd)).setText(mesg);
                // split mesg assuming all is goo for now
                String clean = mesg.substring(1,mesg.length()-1);
                String[] kvArr = clean.split("^");
                if (kvArr.length == 2) {
                    ((TextView) UartKVActivity.this.findViewById(R.id.kv_key_recvd)).setText(kvArr[0]);
                    ((TextView) UartKVActivity.this.findViewById(R.id.kv_value_recvd)).setText(kvArr[1]);
                } else {
                    ((TextView) UartKVActivity.this.findViewById(R.id.kv_key_recvd)).setText("ERROR!"+kvArr.length+kvArr[0]);
                    ((TextView) UartKVActivity.this.findViewById(R.id.kv_value_recvd)).setText(mesg+"-"+clean+"-");
                }
            }
        });
    }
    public void onSendString(View view) {
        Log.d(Constants.TAG, "onSendString");
      //  EditText text = (EditText) UartKVActivity.this.findViewById(R.id.kv_string_text);
     /*   Log.d(Constants.TAG, "onSendString: " + text.getText().toString());
        try {
            String question = text.getText().toString() + ":";  // delimiter used by UART service to know end of message on uBit
            byte[] ascii_bytes = question.getBytes("US-ASCII");
            Log.d(Constants.TAG, "ASCII bytes: 0x" + Utility.byteArrayAsHexString(ascii_bytes));
            bluetooth_le_adapter.writeCharacteristic(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_RX_CHARACTERISTIC_UUID), ascii_bytes);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            showMsg("Unable to convert text to ASCII bytes");
        }
        */
    }
    public void onSendKeyValue(View view) {
        Log.d(Constants.TAG, "onSendKeyValue");
        EditText key = (EditText) UartKVActivity.this.findViewById(R.id.kv_send_key_text);
        EditText value = (EditText) UartKVActivity.this.findViewById(R.id.kv_send_value_text);
        Log.d(Constants.TAG, "onSendString: " + key.getText().toString()+":"+value.getText().toString());
        try {  // can't use delimiter of ':' because part of JSON, so need something else -
            // I set in pxt.io "bluetooth uart read" block and main.cpp onConnected(MicroBitEvent)
            // is this going to screw up other apps?
            String simpleJson = key.getText().toString()+"^"+value.getText().toString()+"|";  // delimiter used by UART service to know end of message on uBit
            byte[] ascii_bytes = simpleJson.getBytes("US-ASCII");
            Log.d(Constants.TAG, "ASCII bytes: 0x" + Utility.byteArrayAsHexString(ascii_bytes));
            bluetooth_le_adapter.writeCharacteristic(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_RX_CHARACTERISTIC_UUID), ascii_bytes);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            showMsg("Unable to convert text to ASCII bytes");
        }
    }
}

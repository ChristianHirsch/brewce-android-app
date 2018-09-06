package net.chrivieh.brewce;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import layout.AutomaticControlFragment;

public class MqttGatewayService extends Service {

    public static final String TAG = MqttGatewayService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder();

    MqttAndroidClient mqttAndroidClient;

    public final String MQTT_SERVER_URI = "MQTT_SERVER_URI";
    public final String MQTT_CLIENT_ID  = "MQTT_CLIENT_ID";
    public final String MQTT_CLIENT_ACCESS_TOKEN = "MQTT_CLIENT_ACCESS_TOKEN";

    final String gatewayConnectTopic =
            "v1/gateway/connect";
    final String gatewayDisconnectTopic =
            "v1/gateway/disconnect";
    final String gatewayRequestAttributeTopic =
            "v1/gateway/attributes/request";
    final String gatewayTelemetryTopic =
            "v1/gateway/telemetry";
    final String gatewayAttributesTopic =
            "v1/gateway/attributes";

    final String[] mqttTopics = {
            "v1/gateway/attributes",
            "v1/gateway/attributes/response"
    };
    final int[] mqttTopicQos = {
            0, 0
    };

    private String mDeviceAddress = null;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if(!preferences.contains(MQTT_SERVER_URI))
            openMqttServerUriSettingsDialog();
        if(!preferences.contains(MQTT_CLIENT_ID))
            openMqttClientIdSettingsDialog();
        if(!preferences.contains(MQTT_CLIENT_ACCESS_TOKEN))
            openMqttClientAccessTokenSettingsDialog();

        final String serverUri = preferences.getString(MQTT_SERVER_URI, "tcp://chirsch.dest-unreachable.net:1883");
        final String clientId  = preferences.getString(MQTT_CLIENT_ID, "") + System.currentTimeMillis();
        final String accessToken = preferences.getString(MQTT_CLIENT_ACCESS_TOKEN, "");

        registerReceiver(mBroadcastReceiver, makeIntentFilter());

        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.i(TAG, "MqttCallbackExtend.connectComplete(" + reconnect + ", ...)");
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "MqttCallbackExtend.connectionLost(...)");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Log.i(TAG, "MqttCallbackExtend.messageArrived(...): "
                        + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.i(TAG, "MqttCallbackExtend.deliveryComplete(...)");
            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(accessToken);

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    //subscribe to topic
                    Log.i(TAG, "Succesfully connected: IMqttActionListener.onSuccess(...)");

                    subscribeToTopics();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG,"IMqttActionListener.onFailure(...)");
                    exception.printStackTrace();
                }
            });
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    private void subscribeToTopics() {
        try {
            mqttAndroidClient.subscribe( mqttTopics, mqttTopicQos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG,"IMqttActionListener.onSuccess(): Subscribed to topic.");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG,"IMqttActionListener.onFailure(): Not subscribed to topic.");
                }
            });

        } catch (MqttException ex) {
            Log.e(TAG, "Exception in subscribeToTopics()");
            ex.printStackTrace();
        }
    }

    private void publishConnectMessage(final String device) {
        try {
            JSONObject data = new JSONObject();
            try {
                data.put("device", device);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(data.toString().getBytes());
            mqttAndroidClient.publish( gatewayConnectTopic, mqttMessage);
        } catch (MqttException ex) {
            Log.e(TAG, "Exception in publishMessage()");
            ex.printStackTrace();
        }
    }

    private void publishDisconnectMessage(final String device) {
        try {
            JSONObject data = new JSONObject();
            try {
                data.put("device", device);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(data.toString().getBytes());
            mqttAndroidClient.publish( gatewayDisconnectTopic, mqttMessage);
        } catch (MqttException ex) {
            Log.e(TAG, "Exception in publishMessage()");
            ex.printStackTrace();
        }
    }

    private void publishTemperatureAsTelemetryMessage(final String device, float temperature) {
        try {
            JSONObject data = new JSONObject();
            try {
                data.put(device, new JSONArray().put(
                        new JSONObject().put("ts", System.currentTimeMillis())
                                .put("values", new JSONObject()
                                        .put("temperature", temperature))
                ));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(data.toString().getBytes());
            mqttAndroidClient.publish( gatewayTelemetryTopic, mqttMessage);
        } catch (/*Mqtt*/Exception ex) {
            Log.e(TAG, "Exception in publishTelemetryMessage()");
            ex.printStackTrace();
        }
    }

    private void publishTemperatureSetpointTelemetryMessage(final String device, float temperature) {
        try {
            JSONObject data = new JSONObject();
            try {
                data.put(device, new JSONArray().put(
                        new JSONObject().put("ts", System.currentTimeMillis())
                                .put("values", new JSONObject()
                                        .put("temperature_setpoint", temperature))
                ));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(data.toString().getBytes());
            mqttAndroidClient.publish( gatewayTelemetryTopic, mqttMessage);
        } catch (/*Mqtt*/Exception ex) {
            Log.e(TAG, "Exception in publishTelemetryMessage()");
            ex.printStackTrace();
        }
    }

    private void publishPowerTelemetryMessage(final String device, int power) {
        try {
            JSONObject data = new JSONObject();
            try {
                data.put(device, new JSONArray().put(
                        new JSONObject().put("ts", System.currentTimeMillis())
                                .put("values", new JSONObject()
                                        .put("power", power))
                ));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(data.toString().getBytes());
            mqttAndroidClient.publish( gatewayTelemetryTopic, mqttMessage);
        } catch (/*Mqtt*/Exception ex) {
            Log.e(TAG, "Exception in publishTelemetryMessage()");
            ex.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mDeviceAddress != null)
            publishDisconnectMessage(mDeviceAddress);
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public MqttGatewayService getService() {
            return MqttGatewayService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //publishDisconnectMessage(mBluetoothGatt.getConnectedDevices().get(0).getAddress());
        return super.onUnbind(intent);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action)
            {
                case BluetoothLeService.ACTION_GATT_CONNECTED:
                    mDeviceAddress =
                            intent.getStringExtra(BluetoothLeService.EXTRA_DATA_DEVICE_ADDRESS);
                    publishConnectMessage(mDeviceAddress);
                    break;

                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    float temp = intent.getFloatExtra(BluetoothLeService.EXTRA_DATA_FLOAT, 0.0f);
                    publishTemperatureAsTelemetryMessage(
                            intent.getStringExtra(BluetoothLeService.EXTRA_DATA_DEVICE_ADDRESS),
                            temp);
                    break;

                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                    mDeviceAddress =
                            intent.getStringExtra(BluetoothLeService.EXTRA_DATA_DEVICE_ADDRESS);
                    publishDisconnectMessage(mDeviceAddress);
                    break;

                case AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED:
                    float setpointTemperature =
                            intent.getFloatExtra(AutomaticControlFragment.EXTRA_DATA, 0.0f);
                    publishTemperatureSetpointTelemetryMessage(mDeviceAddress, setpointTemperature);
                    break;
                case TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED:
                    setpointTemperature =
                            intent.getFloatExtra(TemperatureProfileControlService.EXTRA_DATA, 0.0f);
                    publishTemperatureSetpointTelemetryMessage(mDeviceAddress, setpointTemperature);
                    break;

                case BluetoothLeService.ACTION_DATA_WRITE:
                    int controlEffort = intent.getIntExtra(BluetoothLeService.EXTRA_DATA, 0);
                    int power = Math.round(((3500/250) * controlEffort) / 100);
                    publishPowerTelemetryMessage(mDeviceAddress, power * 100);
                    break;
            }
        }
    };

    private IntentFilter makeIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);

        intentFilter.addAction(AutomaticControlFragment.ACTION_TARGET_TEMPERATURE_CHANGED);
        intentFilter.addAction(TemperatureProfileControlService.ACTION_TARGET_TEMPERATURE_CHANGED);

        return intentFilter;
    }

    private void openMqttServerUriSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("MQTT Server URI");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(MQTT_SERVER_URI, input.getText().toString()); // value to store
                editor.apply();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void openMqttClientIdSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("MQTT Client ID");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(MQTT_CLIENT_ID, input.getText().toString()); // value to store
                editor.apply();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void openMqttClientAccessTokenSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("MQTT Client Access Token");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(MQTT_CLIENT_ACCESS_TOKEN, input.getText().toString()); // value to store
                editor.apply();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
}
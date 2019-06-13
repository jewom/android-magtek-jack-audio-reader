package com.magtek.mobile.android.mtscrademo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.magtek.mobile.android.mtlib.MTDeviceFeatures;
import com.magtek.mobile.android.mtlib.MTSCRA;
import com.magtek.mobile.android.mtlib.MTConnectionType;
import com.magtek.mobile.android.mtlib.MTSCRAEvent;
import com.magtek.mobile.android.mtlib.MTEMVEvent;
import com.magtek.mobile.android.mtlib.MTConnectionState;
import com.magtek.mobile.android.mtlib.MTCardDataState;
import com.magtek.mobile.android.mtlib.IMTCardData;

import java.util.regex.Pattern;

public class MagTekDemo extends Activity {

    private final static String TAG = "MagTekJewom";
    private TextView mMessageTextView;
    private TextView mMessageTextView2;
    private TextView mConnectionStateField;
    private EditText mDataFields;
    private AudioManager m_audioManager;
    private int m_audioVolume;
    private MTSCRA m_scra;
    private MTConnectionType m_connectionType;
    private MTConnectionState m_connectionState = MTConnectionState.Disconnected;
    private final HeadSetBroadCastReceiver m_headsetReceiver = new HeadSetBroadCastReceiver();
    private final NoisyAudioStreamReceiver m_noisyAudioStreamReceiver = new NoisyAudioStreamReceiver();
    private Handler m_scraHandler = new Handler(new SCRAHandlerCallback());

    private class SCRAHandlerCallback implements Callback  {
        public boolean handleMessage(Message msg)
        {
            try
            {
                Log.i(TAG, "*** Callback " + msg.what);
                switch (msg.what)
                {
                    case MTSCRAEvent.OnDeviceConnectionStateChanged:
                        OnDeviceStateChanged((MTConnectionState) msg.obj);
                        break;
                    case MTSCRAEvent.OnCardDataStateChanged:
                        OnCardDataStateChanged((MTCardDataState) msg.obj);
                        break;
                    case MTSCRAEvent.OnDataReceived:
                        OnCardDataReceived((IMTCardData) msg.obj);
                        break;
                    case MTEMVEvent.OnDeviceExtendedResponse:
                        OnDeviceExtendedResponse((String) msg.obj);
                        break;
                }
            }
            catch (Exception ignored){ }
            return true;
        }
    }

    protected void OnDeviceStateChanged(MTConnectionState deviceState) {
        setState(deviceState);
        updateDisplay();
        invalidateOptionsMenu();

        switch (deviceState)
        {
            case Disconnected:
                if (m_connectionType == MTConnectionType.Audio)
                {
                    restoreVolume();
                }
                break;
            case Connected:
                displayDeviceFeatures();
                setVolumeToMax();
                clearMessage();
                clearMessage2();
                break;
            case Error:
                sendToDisplay("[Device State Error]");
                break;
            case Connecting:
                break;
            case Disconnecting:
                break;
        }
    }

    protected void OnCardDataStateChanged(MTCardDataState cardDataState) {
        switch (cardDataState)
        {
            case DataNotReady:
                sendToDisplay("[Card Data Not Ready]");
                break;
            case DataReady:
                sendToDisplay("[Card Data Ready]");
                break;
            case DataError:
                sendToDisplay("[Card Data Error]");
                break;
        }
    }

    protected void OnCardDataReceived(IMTCardData cardData) {
        sendToDisplay("[Raw Data]");
        //long a = cardData.getCardDataCRC();
        //String pan = cardData.getCardPAN();
        sendToDisplay(m_scra.getResponseData());

        sendToDisplay("[Card Data]");
        sendToDisplay(getCardInfo());

        //sendToDisplay("[TLV Payload]");
        //sendToDisplay(cardData.getTLVPayload());
    }

    protected void OnDeviceExtendedResponse(String data) {
        sendToDisplay("[Device Extended Response]");
        sendToDisplay(data);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        m_connectionType = MTConnectionType.Audio;
        mMessageTextView = findViewById(R.id.messageTextView);
        mMessageTextView2 = findViewById(R.id.messageTextView2);
        mConnectionStateField = findViewById(R.id.connection_state);
        mDataFields = findViewById(R.id.data_values);

        try
        {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String strAppVersion =  pInfo.versionName;

            getActionBar().setTitle(getResources().getText(R.string.app_name) + " " + strAppVersion);
        }
        catch (Exception ex)
        {
            getActionBar().setTitle(R.string.app_name);
        }

        getActionBar().setDisplayHomeAsUpEnabled(true);

        m_audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        m_scra = new MTSCRA(this, m_scraHandler);
    }

    private void setState(MTConnectionState deviceState)
    {
        m_connectionState = deviceState;
        updateDisplay();
        invalidateOptionsMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.registerReceiver(m_headsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        this.registerReceiver(m_noisyAudioStreamReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        Log.i(TAG, "*** App onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "*** App onPause");
        if (m_scra.isDeviceConnected()) {
            if (m_connectionType == MTConnectionType.Audio)
                m_scra.closeDevice();
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "*** App onDestroy");
        unregisterReceiver(m_headsetReceiver);
        unregisterReceiver(m_noisyAudioStreamReceiver);
        if (m_scra.isDeviceConnected())
            m_scra.closeDevice();
        super.onDestroy();
    }

    private void displayDeviceFeatures() {
        if (m_scra != null) {
            MTDeviceFeatures features = m_scra.getDeviceFeatures();
            if (features != null) {
                StringBuilder infoSB = new StringBuilder();
                infoSB.append("[Device Features]\n");
                infoSB.append("Supported Types: " + (features.MSR ? "(MSR) ":"") + (features.Contact ? "(Contact) ":"") + (features.Contactless ? "(Contactless) ":"") + "\n");
                infoSB.append("MSR Power Saver: " + (features.MSRPowerSaver ? "Yes":"No") + "\n");
                infoSB.append("Battery Backed Clock: " + (features.BatteryBackedClock ? "Yes":"No"));

                sendToDisplay(infoSB.toString());
            }
        }
    }


    public long closeDevice() {
        Log.i(TAG, "SCRADevice closeDevice");
        long result = -1;
        if (m_scra != null) {
            m_scra.closeDevice();
            result = 0;
        }
        return result;
    }

    public long openDevice() {
        Log.i(TAG, "SCRADevice openDevice");
        long result = -1;
        if (m_scra != null) {
            m_scra.setConnectionType(m_connectionType);
            m_scra.setAddress("");
            boolean enableRetry = false;
            m_scra.setConnectionRetry(enableRetry);
            m_scra.openDevice();
            result = 0;
        }
        return result;
    }

    private void setVolume(int volume) {
        m_audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void saveVolume() {
        m_audioVolume = m_audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private void restoreVolume() {
        setVolume(m_audioVolume);
    }

    private void setVolumeToMax() {
        saveVolume();
        int volume = m_audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        setVolume(volume);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mConnectionStateField.setText(resourceId);
            }
        });
    }

    private void updateDisplay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (m_connectionState == MTConnectionState.Connected)
                    updateConnectionState(R.string.connected);
                else if (m_connectionState == MTConnectionState.Connecting)
                    updateConnectionState(R.string.connecting);
                else if (m_connectionState == MTConnectionState.Disconnecting)
                    updateConnectionState(R.string.disconnecting);
                else if (m_connectionState == MTConnectionState.Disconnected)
                    updateConnectionState(R.string.disconnected);
            }
        });
    }

    private void clearMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMessageTextView.setText("");
            }
        });
    }

    private void clearMessage2() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMessageTextView2.setText("");
            }
        });
    }


    public void sendToDisplay(final String data) {
        if (data != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDataFields.append(data + "\n");
                }
            });
        }
    }


    public String getCardInfo() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(String.format("Card.Name=%s \n", m_scra.getCardName()));
        stringBuilder.append(String.format("Card.Exp.Date=%s \n", m_scra.getCardExpDate()));
        stringBuilder.append(String.format("Card.IIN=%s \n", m_scra.getCardIIN()));
        stringBuilder.append(String.format("Card.Last4=%s \n", m_scra.getCardLast4()));
        stringBuilder.append(String.format("Card.PAN=%s \n", m_scra.getCardPAN()));


        stringBuilder.append(String.format("jewomm=%s \n", m_scra.getCardDataCRC()));
        stringBuilder.append(String.format("jewomm=%s \n", m_scra.getCardExpDate()));
        stringBuilder.append(String.format("jewomm=%s \n", m_scra.getCardIIN()));
        stringBuilder.append(String.format("jewomm=%s \n", m_scra.getCardServiceCode()));

        stringBuilder.append(String.format("util =   %s \n", m_scra.getKSN()));

        try {
            // Setup
            String bdkString = "b2395cd7d466f6e1eb82602e8e69b750";
            String ksnString = m_scra.getKSN();
            String track2String = m_scra.getTrack2();

            byte[] bdk = Dukpt.toByteArray(bdkString);
            byte[] ksn = Dukpt.toByteArray(ksnString);
            byte[] data = Dukpt.toByteArray(track2String);

            // Action
            byte[] key = Dukpt.computeKey(bdk, ksn);
            data = Dukpt.decryptTripleDes(key, data);
            String dataOutput = new String(data, "UTF-8");
            Log.d("jewomDebug", "" + key.length);
            dataOutput = dataOutput.replace(";", "");
            dataOutput = dataOutput.replace("?", "");
            dataOutput = dataOutput.replace(">", "");
            dataOutput = dataOutput.replace("<", "");

            stringBuilder.append(String.format("CARD DECRYPTED =   %s \n", dataOutput));

            String [] cardInfo = dataOutput.split("=");
            String card = cardInfo[0];
            String expiryFull = cardInfo[1];
            String expriryYear = expiryFull.substring(0, 2);
            String expiryMonth = expiryFull.substring(2, 4);

            stringBuilder.append(String.format("CARD =   %s \n", card));
            stringBuilder.append(String.format("EXPIRY MONTH =   %s \n", expiryMonth));
            stringBuilder.append(String.format("EXPIRY YEAR =   %s \n", expriryYear));

        } catch (Exception e) {
            Log.d("jewomDebug", "" + e.getMessage());
            e.printStackTrace();
        }

        return stringBuilder.toString();
    }

    public class NoisyAudioStreamReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (m_connectionType == MTConnectionType.Audio) {
                    if(m_scra.isDeviceConnected())
                        closeDevice();
                }
            }
        }
    }

    public class HeadSetBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();

                if( (action.compareTo(Intent.ACTION_HEADSET_PLUG))  == 0)   //if the action match a headset one
                {
                    int headSetState = intent.getIntExtra("state", 0);      //get the headset state property
                    int hasMicrophone = intent.getIntExtra("microphone", 0);//get the headset microphone property

                    if ((headSetState != 1) || (hasMicrophone != 1))        //headset was unplugged & has no microphone
                    {
                        Log.d("debugJewom", "hollla");
                        if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                            if (m_connectionType == MTConnectionType.Audio) {
                                if(m_scra.isDeviceConnected())
                                    closeDevice();
                            }
                        }
                    }
                    else {
                        Log.d("debugJewom", "plugin card reader !");
                        if (openDevice() != 0)
                            sendToDisplay("[Failed to connect to the device]");
                    }
                }
            }
            catch(Exception ignored) { }
        }
    }


}

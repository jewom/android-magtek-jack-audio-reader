package com.magtek.mobile.android.mtscrademo;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.magtek.mobile.android.mtlib.MTDeviceFeatures;
import com.magtek.mobile.android.mtlib.MTSCRA;
import com.magtek.mobile.android.mtlib.MTConnectionType;
import com.magtek.mobile.android.mtlib.MTSCRAEvent;
import com.magtek.mobile.android.mtlib.MTEMVEvent;
import com.magtek.mobile.android.mtlib.MTConnectionState;
import com.magtek.mobile.android.mtlib.MTCardDataState;
import com.magtek.mobile.android.mtlib.MTDeviceConstants;
import com.magtek.mobile.android.mtlib.IMTCardData;

public class MagTekDemo extends Activity {
    private final static String TAG = "MagTekJewom";

    private Menu mMainMenu;
    private TextView mMessageTextView;
    private TextView mMessageTextView2;
    private TextView mConnectionStateField;
    private EditText mDataFields;
    private AudioManager m_audioManager;
    private int m_audioVolume;
    private boolean m_startTransactionActionPending;
    private boolean m_turnOffLEDPending;
    private int m_emvMessageFormat = 0;
    private boolean m_emvMessageFormatRequestPending = false;
    private MTSCRA m_scra;
    private MTConnectionType m_connectionType;
    private Object m_syncEvent = null;
    private String m_syncData = "";
    private MTConnectionState m_connectionState = MTConnectionState.Disconnected;
    private final HeadSetBroadCastReceiver m_headsetReceiver = new HeadSetBroadCastReceiver();
    private final NoisyAudioStreamReceiver m_noisyAudioStreamReceiver = new NoisyAudioStreamReceiver();
    private String[] mTypes = new String[] {"Swipe", "Chip", "Contactless"};
    private boolean[] mTypeChecked = new boolean[] {false, true, false};
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
                    case MTSCRAEvent.OnDeviceResponse:
                        OnDeviceResponse((String) msg.obj);
                        break;
                    case MTEMVEvent.OnTransactionStatus:
                        OnTransactionStatus((byte[]) msg.obj);
                        break;
                    case MTEMVEvent.OnDisplayMessageRequest:
                        OnDisplayMessageRequest((byte[]) msg.obj);
                        break;
                    case MTEMVEvent.OnUserSelectionRequest:
                        OnUserSelectionRequest((byte[]) msg.obj);
                        break;
                    case MTEMVEvent.OnTransactionResult:
                        OnTransactionResult((byte[]) msg.obj);
                        break;

                    case MTEMVEvent.OnEMVCommandResult:
                        OnEMVCommandResult((byte[]) msg.obj);
                        break;

                    case MTEMVEvent.OnDeviceExtendedResponse:
                        OnDeviceExtendedResponse((String) msg.obj);
                        break;
                }
            }
            catch (Exception ex)
            {

            }

            return true;
        }
    }

    protected void OnDeviceStateChanged(MTConnectionState deviceState)
    {
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
                if (m_connectionType == MTConnectionType.Audio)
                {
                    setVolumeToMax();
                }
                else if ((m_connectionType == MTConnectionType.USB) && (m_scra.isDeviceEMV() == false))
                {
                    sendGetSecurityLevelCommand();	// Wake up swipe output channel for BulleT and Audio readers
                }

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
        sendToDisplay(m_scra.getResponseData());

        sendToDisplay("[Card Data]");
        sendToDisplay(getCardInfo());

        sendToDisplay("[TLV Payload]");
        sendToDisplay(cardData.getTLVPayload());
    }

    protected void notifySyncData(String data) {
        if (m_syncEvent != null)
        {
            synchronized(m_syncEvent)
            {
                m_syncData = data;
                m_syncEvent.notifyAll();
            }
        }
    }

    protected void OnDeviceResponse(String data) {
        sendToDisplay("[Device Response]");
        sendToDisplay(data);
        notifySyncData(data);

        if (m_emvMessageFormatRequestPending) {
            m_emvMessageFormatRequestPending = false;

            byte[] emvMessageFormatResponseByteArray = TLVParser.getByteArrayFromHexString(data);

            if (emvMessageFormatResponseByteArray.length == 3)
            {
                if ((emvMessageFormatResponseByteArray[0] == 0) && (emvMessageFormatResponseByteArray[1] == 1))
                {
                    m_emvMessageFormat = emvMessageFormatResponseByteArray[2];
                }
            }
        }
        else if (m_startTransactionActionPending)
        {
            m_startTransactionActionPending = false;

            startTransaction();
        }
    }

    protected void OnTransactionStatus(byte[] data)
    {
        sendToDisplay("[Transaction Status]");

        sendToDisplay(TLVParser.getHexString(data));
    }

    protected void OnDisplayMessageRequest(byte[] data)
    {
        sendToDisplay("[Display Message Request]");

        String message = TLVParser.getTextString(data, 0);
        sendToDisplay(message);
        displayMessage(message);
    }

    protected void OnEMVCommandResult(byte[] data) {
        sendToDisplay("[EMV Command Result]");
        sendToDisplay(TLVParser.getHexString(data));
        if (m_turnOffLEDPending) {
            m_turnOffLEDPending = false;
            setLED(false);
        }
    }

    protected void OnDeviceExtendedResponse(String data) {
        sendToDisplay("[Device Extended Response]");
        sendToDisplay(data);
    }


    protected void OnUserSelectionRequest(byte[] data) {
        sendToDisplay("[User Selection Request]");
        sendToDisplay(TLVParser.getHexString(data));
    }

    protected void showTransactionTypes()
    {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("Transaction Types");
        dialogBuilder.setNegativeButton(R.string.value_cancel,
                new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.dismiss();
                    }
                });

        dialogBuilder.setPositiveButton("Start",
                new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.dismiss();
                        startTransactionWithLED();
                    }
                });


        dialogBuilder.setMultiChoiceItems(mTypes, mTypeChecked, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which, boolean isChecked) {
                try {
                    mTypeChecked[which] = isChecked;
                }
                catch (Exception ex) {
                }
            }
        });

    }


    protected void OnTransactionResult(byte[] data) {
        sendToDisplay("[Transaction Result]");

        if (data != null)
        {
            if (data.length > 0)
            {
                boolean signatureRequired = (data[0] != 0);

                int lenBatchData = data.length - 3;
                if (lenBatchData > 0)
                {
                    byte[] batchData = new byte[lenBatchData];

                    System.arraycopy(data, 3, batchData, 0, lenBatchData);

                    sendToDisplay("(Parsed Batch Data)");

                    List<HashMap<String, String>> parsedTLVList = TLVParser.parseEMVData(batchData, false, "");

                    displayParsedTLV(parsedTLVList);

                    String cidString = TLVParser.getTagValue(parsedTLVList, "9F27");
                    byte[] cidValue = TLVParser.getByteArrayFromHexString(cidString);

                    boolean approved = false;

                    if (cidValue != null)
                    {
                        if (cidValue.length > 0)
                        {
                            if ((cidValue[0] & (byte) 0x40) != 0)
                            {
                                approved = true;
                            }
                        }
                    }

                    if (approved) {
                        if (signatureRequired)
                            displayMessage2("( Signature Required )");
                        else
                            displayMessage2("( No Signature Required )");
                    }
                }
            }
        }

        setLED(false);
    }

    private void displayParsedTLV(List<HashMap<String, String>> parsedTLVList)
    {
        if (parsedTLVList != null)
        {

            for (HashMap<String, String> map : parsedTLVList) {
                String tagString = map.get("tag");
                String valueString = map.get("value");
                sendToDisplay("  " + tagString + "=" + valueString);
            }
        }
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
    protected void onResume()
    {
        super.onResume();

        this.registerReceiver(m_headsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        this.registerReceiver(m_noisyAudioStreamReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        Log.i(TAG, "*** App onResume");
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        Log.i(TAG, "*** App onPause");

        if (m_scra.isDeviceConnected())
        {
            if (m_connectionType == MTConnectionType.Audio)
            {
                m_scra.closeDevice();
            }
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        Log.i(TAG, "*** App onStop");
    }

    @Override
    protected void onDestroy()
    {
        Log.i(TAG, "*** App onDestroy");

        unregisterReceiver(m_headsetReceiver);
        unregisterReceiver(m_noisyAudioStreamReceiver);

        if (m_scra.isDeviceConnected()) {
            m_scra.closeDevice();
        }

        super.onDestroy();
    }


    private void sendSetDateTimeCommand()
    {
        Calendar now = Calendar.getInstance();

        int month = now.get(Calendar.MONTH) + 1;
        int day = now.get(Calendar.DAY_OF_MONTH);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int second = now.get(Calendar.SECOND);
        int year = now.get(Calendar.YEAR) - 2008;

        String dateTimeString = String.format("%1$02x%2$02x%3$02x%4$02x%5$02x00%6$02x", month, day, hour, minute, second, year);
        String command = "49220000030C001C0000000000000000000000000000000000" + dateTimeString + "00000000";
        sendCommand(command);
    }

    private class getDeviceInfoTask extends AsyncTask<String, Void, String>
    {
        @Override
        protected String doInBackground(String... params)
        {
            String response = sendCommandSync("000100");
            sendToDisplay("[Firmware ID]");
            sendToDisplay(response);

            String response2 = sendCommandSync("000103");
            sendToDisplay("[Device SN]");
            sendToDisplay(response2);

            String response3 = sendCommandSync("1500");
            sendToDisplay("[Security Level]");
            sendToDisplay(response3);

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    private class getBatteryLevelTask extends AsyncTask<String, Void, String>
    {
        @Override
        protected String doInBackground(String... params)
        {
            long level = m_scra.getBatteryLevel();
            sendToDisplay("Battery Level=" + level);

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    private void displayDeviceFeatures()
    {
        if (m_scra != null)
        {
            MTDeviceFeatures features = m_scra.getDeviceFeatures();

            if (features != null)
            {
                StringBuilder infoSB = new StringBuilder();
                infoSB.append("[Device Features]\n");
                infoSB.append("Supported Types: " + (features.MSR ? "(MSR) ":"") + (features.Contact ? "(Contact) ":"") + (features.Contactless ? "(Contactless) ":"") + "\n");
                infoSB.append("MSR Power Saver: " + (features.MSRPowerSaver ? "Yes":"No") + "\n");
                infoSB.append("Battery Backed Clock: " + (features.BatteryBackedClock ? "Yes":"No"));

                sendToDisplay(infoSB.toString());
            }
        }
    }

    private void getDeviceInfo()
    {
        new getDeviceInfoTask().execute();
    }

    private void getBatteryLevel()
    {
        new getBatteryLevelTask().execute();
    }

    private void getKSN()
    {
        if (m_scra != null)
        {
            sendToDisplay("[Get KSN]");
            String ksn = m_scra.getKSN();
            sendToDisplay("KSN=" +  ksn);
        }
    }

    private void clearBuffers()
    {
        if (m_scra != null)
        {
            sendToDisplay("[Clear Buffers]");
            m_scra.clearBuffers();
        }
    }

    private void setMSRPower(boolean state)
    {
        String command = "5801" + (state ? "01":"00");

        sendCommand(command);
    }

    private void requestEMVMessageFormat()
    {
        Handler delayHandler = new Handler();
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                m_emvMessageFormatRequestPending = true;

                int status = sendCommand("000168");

                if (status != MTSCRA.SEND_COMMAND_SUCCESS)
                {
                    m_emvMessageFormatRequestPending = false;
                }
            }
        }, 1000);
    }

    private void sendGetSecurityLevelCommand()
    {
        Handler delayHandler = new Handler();
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int status = sendCommand("1500");

            }
        }, 1000);
    }


    public String sendCommandSync(String command)
    {
        String response = "";

        m_syncData = "";
        m_syncEvent = new Object();

        synchronized(m_syncEvent)
        {
            sendCommand(command);

            try
            {
                m_syncEvent.wait(3000);
                response = new String(m_syncData);
            }
            catch (InterruptedException ex)
            {
                // response timed out
            }
        }

        m_syncEvent = null;

        return response;
    }

    public int sendCommand(String command)
    {
        int result = MTSCRA.SEND_COMMAND_ERROR;


        if (m_scra != null)
        {
            sendToDisplay("[Sending Command]");
            sendToDisplay(command);

            result = m_scra.sendCommandToDevice(command);
        }

        return result;
    }

    public void startTransactionWithLED()
    {
        m_startTransactionActionPending = true;
        setLED(true);
    }

    public void startTransaction()
    {
        byte type = 0;

        if (mTypeChecked[0])
        {
            type |= (byte) 0x01;
        }

        if (mTypeChecked[1])
        {
            type |= (byte) 0x02;
        }

        if (mTypeChecked[2])
        {
            type |= (byte) 0x04;
        }

    }


    public void setLED(boolean on) {
        if (m_scra != null) {
            if (on)
                m_scra.sendCommandToDevice(MTDeviceConstants.SCRA_DEVICE_COMMAND_STRING_SET_LED_ON);
            else
                m_scra.sendCommandToDevice(MTDeviceConstants.SCRA_DEVICE_COMMAND_STRING_SET_LED_OFF);
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

    private void displayMessage(final String message) {
        if (message != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMessageTextView.setText(message);
                }
            });
        }
    }

    private void displayMessage2(final String message) {
        if (message != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMessageTextView2.setText(message);
                }
            });
        }
    }

    public String formatStringIfNotEmpty(String format, String data) {
        String result = "";
        if (!data.isEmpty())
            result = String.format(format, data);
        return result;
    }

    public String formatStringIfNotValueZero(String format, int data) {
        String result = "";
        if (data != 0) {
            result = String.format(format, data);
        }
        return result;
    }

    public String getCardInfo() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(String.format("Tracks.Masked=%s \n", m_scra.getMaskedTracks()));

        stringBuilder.append(String.format("Track1.Encrypted=%s \n", m_scra.getTrack1()));
        stringBuilder.append(String.format("Track2.Encrypted=%s \n", m_scra.getTrack2()));
        stringBuilder.append(String.format("Track3.Encrypted=%s \n", m_scra.getTrack3()));

        stringBuilder.append(String.format("Track1.Masked=%s \n", m_scra.getTrack1Masked()));
        stringBuilder.append(String.format("Track2.Masked=%s \n", m_scra.getTrack2Masked()));
        stringBuilder.append(String.format("Track3.Masked=%s \n", m_scra.getTrack3Masked()));

        stringBuilder.append(String.format("MagnePrint.Encrypted=%s \n", m_scra.getMagnePrint()));
        stringBuilder.append(String.format("MagnePrint.Status=%s \n", m_scra.getMagnePrintStatus()));
        stringBuilder.append(String.format("Device.Serial=%s \n", m_scra.getDeviceSerial()));
        stringBuilder.append(String.format("Session.ID=%s \n", m_scra.getSessionID()));
        stringBuilder.append(String.format("KSN=%s \n", m_scra.getKSN()));
        stringBuilder.append(formatStringIfNotEmpty("Cap.MagnePrint=%s \n", m_scra.getCapMagnePrint()));
        stringBuilder.append(formatStringIfNotEmpty("Cap.MagnePrintEncryption=%s \n", m_scra.getCapMagnePrintEncryption()));
        stringBuilder.append(formatStringIfNotEmpty("Cap.MagneSafe20Encryption=%s \n", m_scra.getCapMagneSafe20Encryption()));
        stringBuilder.append(formatStringIfNotEmpty("Cap.MagStripeEncryption=%s \n", m_scra.getCapMagStripeEncryption()));
        stringBuilder.append(formatStringIfNotEmpty("Cap.MSR=%s \n", m_scra.getCapMSR()));
        stringBuilder.append(formatStringIfNotEmpty("Cap.Tracks=%s \n", m_scra.getCapTracks()));

        stringBuilder.append(String.format("Card.Data.CRC=%d \n", m_scra.getCardDataCRC()));
        stringBuilder.append(String.format("Card.Exp.Date=%s \n", m_scra.getCardExpDate()));
        stringBuilder.append(String.format("Card.IIN=%s \n", m_scra.getCardIIN()));
        stringBuilder.append(String.format("Card.Last4=%s \n", m_scra.getCardLast4()));
        stringBuilder.append(String.format("Card.Name=%s \n", m_scra.getCardName()));
        stringBuilder.append(String.format("Card.PAN=%s \n", m_scra.getCardPAN()));
        stringBuilder.append(String.format("Card.PAN.Length=%d \n", m_scra.getCardPANLength()));
        stringBuilder.append(String.format("Card.Service.Code=%s \n", m_scra.getCardServiceCode()));
        stringBuilder.append(String.format("Card.Status=%s \n", m_scra.getCardStatus()));
        stringBuilder.append(formatStringIfNotEmpty("HashCode=%s \n", m_scra.getHashCode()));
        stringBuilder.append(formatStringIfNotValueZero("Data.Field.Count=%s \n", m_scra.getDataFieldCount()));
        stringBuilder.append(String.format("Encryption.Status=%s \n", m_scra.getEncryptionStatus()));
        stringBuilder.append(formatStringIfNotEmpty("MagTek.Device.Serial=%s \n", m_scra.getMagTekDeviceSerial()));
        stringBuilder.append(formatStringIfNotEmpty("Response.Type=%s \n", m_scra.getResponseType()));
        stringBuilder.append(formatStringIfNotEmpty("TLV.Version=%s \n", m_scra.getTLVVersion()));
        stringBuilder.append(String.format("Track.Decode.Status=%s \n", m_scra.getTrackDecodeStatus()));

        String tkStatus = m_scra.getTrackDecodeStatus();

        if (tkStatus.length() >= 6) {
            String tk1Status = tkStatus.substring(0, 2);
            String tk2Status = tkStatus.substring(2, 4);
            String tk3Status = tkStatus.substring(4, 6);
            stringBuilder.append(String.format("Track1.Status=%s \n", tk1Status));
            stringBuilder.append(String.format("Track2.Status=%s \n", tk2Status));
            stringBuilder.append(String.format("Track3.Status=%s \n", tk3Status));
        }
        stringBuilder.append(String.format("SDK.Version=%s \n", m_scra.getSDKVersion()));
        stringBuilder.append(String.format("Battery.Level=%d \n", m_scra.getBatteryLevel()));
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
            catch(Exception ex) {

            }
        }
    }



}

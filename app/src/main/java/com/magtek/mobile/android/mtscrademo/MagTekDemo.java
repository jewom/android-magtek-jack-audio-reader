package com.magtek.mobile.android.mtscrademo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

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
import com.magtek.mobile.android.mtlib.MTEMVDeviceConstants;
import com.magtek.mobile.android.mtlib.MTSCRA;
import com.magtek.mobile.android.mtlib.MTConnectionType;
import com.magtek.mobile.android.mtlib.MTSCRAEvent;
import com.magtek.mobile.android.mtlib.MTEMVEvent;
import com.magtek.mobile.android.mtlib.MTConnectionState;
import com.magtek.mobile.android.mtlib.MTCardDataState;
import com.magtek.mobile.android.mtlib.MTDeviceConstants;
import com.magtek.mobile.android.mtlib.IMTCardData;
import com.magtek.mobile.android.mtlib.MTServiceState;
import com.magtek.mobile.android.mtlib.config.MTSCRAConfig;
import com.magtek.mobile.android.mtlib.config.ProcessMessageResponse;
import com.magtek.mobile.android.mtlib.config.SCRAConfigurationDeviceInfo;

public class MagTekDemo extends Activity
{
    private final static String TAG = MagTekDemo.class.getSimpleName();

    public static final String AUDIO_CONFIG_FILE = "MTSCRAAudioConfig.cfg";

    public static final String EXTRAS_CONNECTION_TYPE_VALUE_AUDIO = "Audio";
    public static final String EXTRAS_CONNECTION_TYPE_VALUE_BLE = "BLE";
    public static final String EXTRAS_CONNECTION_TYPE_VALUE_BLE_EMV = "BLEEMV";
    public static final String EXTRAS_CONNECTION_TYPE_VALUE_BLE_EMVT = "BLEEMVT";
    public static final String EXTRAS_CONNECTION_TYPE_VALUE_BLUETOOTH = "Bluetooth";
    public static final String EXTRAS_CONNECTION_TYPE_VALUE_USB = "USB";
    public static final String EXTRAS_CONNECTION_TYPE_VALUE_SERIAL = "Serial";

    public static final String EXTRAS_CONNECTION_TYPE = "CONNECTION_TYPE";
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_AUDIO_CONFIG_TYPE = "AUDIO_CONFIG_TYPE";

    public static final String CONFIGWS_URL = "https://deviceconfig.magensa.net/service.asmx";//Production URL
    private static final String CONFIGWS_USERNAME = "magtek";
    private static final String CONFIGWS_PASSWORD = "p@ssword";
    private static final int CONFIGWS_READERTYPE = 0;
    private static final int CONFIGWS_TIMEOUT = 10000;

    private static String SCRA_CONFIG_VERSION = "102.02";

    private Menu mMainMenu;

    private TextView mMessageTextView;
    private TextView mMessageTextView2;

    private TextView mAddressField;
    private TextView mConnectionStateField;

    private EditText mDataFields;

    private AlertDialog mSelectionDialog;
    private Handler mSelectionDialogController;

    private AudioManager m_audioManager;

    private int m_audioVolume;

    private boolean m_startTransactionActionPending;

    private boolean m_turnOffLEDPending;

    private int m_emvMessageFormat = 0;

    private boolean m_emvMessageFormatRequestPending = false;

    private MTSCRA m_scra;

    private MTConnectionType m_connectionType;
    private String m_deviceName;
    private String m_deviceAddress;
    private String m_audioConfigType;

    private Object m_syncEvent = null;
    private String m_syncData = "";

    private MTConnectionState m_connectionState = MTConnectionState.Disconnected;

    private final HeadSetBroadCastReceiver m_headsetReceiver = new HeadSetBroadCastReceiver();

    private final NoisyAudioStreamReceiver m_noisyAudioStreamReceiver = new NoisyAudioStreamReceiver();

    private AlertDialog mTransactionDialog;
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
                    case MTEMVEvent.OnARQCReceived:
                        OnARQCReceived((byte[]) msg.obj);
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

    protected void OnCardDataStateChanged(MTCardDataState cardDataState)
    {
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

    protected void OnCardDataReceived(IMTCardData cardData)
    {
        //clearDisplay();

        sendToDisplay("[Raw Data]");
        sendToDisplay(m_scra.getResponseData());

        sendToDisplay("[Card Data]");
        sendToDisplay(getCardInfo());

        sendToDisplay("[TLV Payload]");
        sendToDisplay(cardData.getTLVPayload());
    }

    protected void notifySyncData(String data)
    {
        if (m_syncEvent != null)
        {
            synchronized(m_syncEvent)
            {
                m_syncData = data;
                m_syncEvent.notifyAll();
            }
        }
    }

    protected void OnDeviceResponse(String data)
    {
        sendToDisplay("[Device Response]");

        sendToDisplay(data);

        notifySyncData(data);

        if (m_emvMessageFormatRequestPending)
        {
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

    protected void OnEMVCommandResult(byte[] data)
    {
        sendToDisplay("[EMV Command Result]");

        sendToDisplay(TLVParser.getHexString(data));

        if (m_turnOffLEDPending)
        {
            m_turnOffLEDPending = false;

            setLED(false);
        }
    }

    protected void OnDeviceExtendedResponse(String data)
    {
        sendToDisplay("[Device Extended Response]");

        sendToDisplay(data);
    }

    protected ArrayList<String> getSelectionList(byte[] data, int offset)
    {
        ArrayList<String> selectionList = new ArrayList<String>();

        if (data != null)
        {
            int dataLen = data.length;

            if (dataLen >= offset)
            {
                int start = offset;

                for (int i = offset; i < dataLen; i++)
                {
                    if (data[i] == 0x00)
                    {
                        int len = i - start;

                        if (len >= 0)
                        {
                            selectionList.add(new String(data, start, len));
                        }

                        start = i + 1;
                    }
                }
            }
        }

        return selectionList;
    }

    protected void OnUserSelectionRequest(byte[] data)
    {
        sendToDisplay("[User Selection Request]");

        sendToDisplay(TLVParser.getHexString(data));

        processSelectionRequest(data);
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
                try
                {
                    mTypeChecked[which] = isChecked;
                }
                catch (Exception ex)
                {

                }
            }
        });

        mTransactionDialog = dialogBuilder.show();
    }

    protected void processSelectionRequest(byte[] data)
    {
        if (data != null)
        {
            int dataLen = data.length;

            if (dataLen > 2)
            {
                byte selectionType = data[0];
                long timeout = ((long) (data[1] & 0xFF) * 1000);

                ArrayList<String> selectionList = getSelectionList(data, 2);

                String selectionTitle = selectionList.get(0);

                selectionList.remove(0);

                int nSelections = selectionList.size();

                if (nSelections > 0)
                {
                    if (selectionType == MTEMVDeviceConstants.SELECTION_TYPE_LANGUAGE)
                    {
                        for (int i = 0; i < nSelections; i++)
                        {
                            byte[] code = selectionList.get(i).getBytes();
                            EMVLanguage language = EMVLanguage.GetLanguage(code);

                            if (language != null)
                            {
                                selectionList.set(i, language.getName());
                            }
                        }
                    }

                    String[] selectionArray = selectionList.toArray(new String[selectionList.size()]);

                    mSelectionDialogController = new Handler();

                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                    dialogBuilder.setTitle(selectionTitle);

                    dialogBuilder.setNegativeButton(R.string.value_cancel,
                            new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int id)
                                {
                                    mSelectionDialogController.removeCallbacksAndMessages(null);
                                    mSelectionDialogController = null;
                                    dialog.dismiss();
                                    setUserSelectionResult(MTEMVDeviceConstants.SELECTION_STATUS_CANCELLED, (byte) 0);
                                }
                            });

                    dialogBuilder.setItems(selectionArray,
                            new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    mSelectionDialogController.removeCallbacksAndMessages(null);
                                    mSelectionDialogController = null;
                                    setUserSelectionResult(MTEMVDeviceConstants.SELECTION_STATUS_COMPLETED, (byte) (which));
                                }
                            });

                    mSelectionDialog = dialogBuilder.show();

                    mSelectionDialogController.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mSelectionDialog.dismiss();
                            setUserSelectionResult(MTEMVDeviceConstants.SELECTION_STATUS_TIMED_OUT, (byte) 0);
                        }
                    }, timeout);
                }
            }
        }
    }

    protected boolean isQuickChipEnabled()
    {
        boolean enabled = false;

        if (mMainMenu != null)
        {
            enabled = mMainMenu.findItem(R.id.menu_emv_quickchip).isChecked();
        }

        return enabled;
    }

    protected boolean isTransactionApproved(byte[] data)
    {
        boolean approved = false;

        if (mMainMenu != null)
        {
            approved = mMainMenu.findItem(R.id.menu_emv_approved).isChecked();
        }

        return approved;
    }

    protected void OnARQCReceived(byte[] data)
    {
        sendToDisplay("[ARQC Received]");

        sendToDisplay(TLVParser.getHexString(data));

        if (isQuickChipEnabled())
        {
            Log.i(TAG, "** Not sending ARQC response for Quick Chip");
            return;
        }

        List<HashMap<String, String>> parsedTLVList = TLVParser.parseEMVData(data, true, "");

        if (parsedTLVList != null)
        {
            String macKSNString = TLVParser.getTagValue(parsedTLVList, "DFDF54");
            byte[] macKSN = TLVParser.getByteArrayFromHexString(macKSNString);

            String macEncryptionTypeString = TLVParser.getTagValue(parsedTLVList, "DFDF55");
            byte[] macEncryptionType = TLVParser.getByteArrayFromHexString(macEncryptionTypeString);

            String deviceSNString = TLVParser.getTagValue(parsedTLVList, "DFDF25");
            byte[] deviceSN = TLVParser.getByteArrayFromHexString(deviceSNString);

            sendToDisplay("SN Bytes=" + deviceSNString);
            sendToDisplay("SN String=" + TLVParser.getTextString(deviceSN, 2));

            boolean approved = isTransactionApproved(data);

            byte[] response = null;

            if (m_emvMessageFormat == 0)
            {
                response = buildAcquirerResponseFormat0(deviceSN, approved);
            }
            else if (m_emvMessageFormat == 1)
            {
                response = buildAcquirerResponseFormat1(macKSN, macEncryptionType, deviceSN, approved);
            }

            setAcquirerResponse(response);
        }
    }

    protected byte[] buildAcquirerResponseFormat0(byte[] deviceSN, boolean approved)
    {
        byte[] response = null;

        int lenSN = 0;

        if (deviceSN != null)
            lenSN = deviceSN.length;

        byte[] snTag = new byte[] { (byte) 0xDF, (byte) 0xDF, 0x25, (byte) lenSN };
        byte[] container = new byte[] { (byte) 0xFA, 0x06, 0x70, 0x04 };
        byte[] approvedARC = new byte[] { (byte) 0x8A, 0x02, 0x30, 0x30 };
        byte[] declinedARC = new byte[] { (byte) 0x8A, 0x02, 0x30, 0x35 };

        int len = 4 + snTag.length + lenSN + container.length + approvedARC.length;

        response = new byte[len];

        int i = 0;
        len -= 2;
        response[i++] = (byte)((len >> 8) & 0xFF);
        response[i++] = (byte)(len & 0xFF);
        len -= 2;
        response[i++] = (byte) 0xF9;
        response[i++] = (byte) len;
        System.arraycopy(snTag, 0, response, i, snTag.length);
        i += snTag.length;
        System.arraycopy(deviceSN, 0, response, i, deviceSN.length);
        i += deviceSN.length;
        System.arraycopy(container, 0, response, i, container.length);
        i += container.length;
        if (approved)
        {
            System.arraycopy(approvedARC, 0, response, i, approvedARC.length);
        }
        else
        {
            System.arraycopy(declinedARC, 0, response, i, declinedARC.length);
        }

        return response;
    }

    protected byte[] buildAcquirerResponseFormat1(byte[] macKSN, byte[] macEncryptionType, byte[] deviceSN, boolean approved)
    {
        byte[] response = null;

        int lenMACKSN = 0;
        int lenMACEncryptionType = 0;
        int lenSN = 0;

        if (macKSN != null)
        {
            lenMACKSN = macKSN.length;
        }

        if (macEncryptionType != null)
        {
            lenMACEncryptionType = macEncryptionType.length;
        }

        if (deviceSN != null)
        {
            lenSN = deviceSN.length;
        }

        byte[] macKSNTag = new byte[] { (byte)0xDF, (byte)0xDF, 0x54, (byte)lenMACKSN };
        byte[] macEncryptionTypeTag = new byte[] { (byte)0xDF, (byte)0xDF, 0x55, (byte)lenMACEncryptionType };
        byte[] snTag = new byte[] { (byte)0xDF, (byte)0xDF, 0x25, (byte)lenSN };
        byte[] container = new byte[] { (byte)0xFA, 0x06, 0x70, 0x04 };
        byte[] approvedARC = new byte[] { (byte)0x8A, 0x02, 0x30, 0x30 };
        byte[] declinedARC = new byte[] { (byte)0x8A, 0x02, 0x30, 0x35 };

        int lenTLV = 4 + macKSNTag.length + lenMACKSN + macEncryptionTypeTag.length + lenMACEncryptionType + snTag.length + lenSN + container.length + approvedARC.length;

        int lenPadding = 0;

        if ((lenTLV % 8) > 0)
        {
            lenPadding = (8 - lenTLV % 8);
        }

        int lenData = lenTLV + lenPadding + 4;

        response = new byte[lenData];

        int i = 0;
        response[i++] = (byte)(((lenData - 2) >> 8) & 0xFF);
        response[i++] = (byte)((lenData - 2) & 0xFF);
        response[i++] = (byte)0xF9;
        response[i++] = (byte)(lenTLV - 4);
        System.arraycopy(macKSNTag, 0, response, i, macKSNTag.length);
        i += macKSNTag.length;
        System.arraycopy(macKSN, 0, response, i, macKSN.length);
        i += macKSN.length;
        System.arraycopy(macEncryptionTypeTag, 0, response, i, macEncryptionTypeTag.length);
        i += macEncryptionTypeTag.length;
        System.arraycopy(macEncryptionType, 0, response, i, macEncryptionType.length);
        i += macEncryptionType.length;
        System.arraycopy(snTag, 0, response, i, snTag.length);
        i += snTag.length;
        System.arraycopy(deviceSN, 0, response, i, deviceSN.length);
        i += deviceSN.length;
        System.arraycopy(container, 0, response, i, container.length);
        i += container.length;

        if (approved)
        {
            System.arraycopy(approvedARC, 0, response, i, approvedARC.length);
        }
        else
        {
            System.arraycopy(declinedARC, 0, response, i, declinedARC.length);
        }

        return response;
    }

    protected void OnTransactionResult(byte[] data)
    {
        sendToDisplay("[Transaction Result]");

        //sendToDisplay(TLVParser.getHexString(data));

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

                    if (approved)
                    {
                        if (signatureRequired)
                        {
                            displayMessage2("( Signature Required )");
                        }
                        else
                        {
                            displayMessage2("( No Signature Required )");
                        }
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
            ListIterator<HashMap<String, String>> it = parsedTLVList.listIterator();

            while (it.hasNext())
            {
                HashMap<String, String> map = it.next();

                String tagString = map.get("tag");
                //String lenString = map.get("len");
                String valueString = map.get("value");

                sendToDisplay("  "+ tagString + "=" + valueString);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Intent intent = getIntent();

        String connectionType = intent.getStringExtra(EXTRAS_CONNECTION_TYPE);

        m_connectionType = MTConnectionType.Audio;

        if (connectionType != null)
        {
            if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_AUDIO))
            {
                m_connectionType = MTConnectionType.Audio;
            }
            else if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_BLE))
            {
                m_connectionType = MTConnectionType.BLE;
            }
            else if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_BLE_EMV))
            {
                m_connectionType = MTConnectionType.BLEEMV;
            }
            else if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_BLE_EMVT))
            {
                m_connectionType = MTConnectionType.BLEEMVT;
            }
            else if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_BLUETOOTH))
            {
                m_connectionType = MTConnectionType.Bluetooth;
            }
            else if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_USB))
            {
                m_connectionType = MTConnectionType.USB;
            }
            else if (connectionType.equalsIgnoreCase(EXTRAS_CONNECTION_TYPE_VALUE_SERIAL))
            {
                m_connectionType = MTConnectionType.Serial;
            }
        }

        m_deviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        m_deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        m_audioConfigType = intent.getStringExtra(EXTRAS_AUDIO_CONFIG_TYPE);

        // Sets up UI references.
        mMessageTextView = ((TextView) findViewById(R.id.messageTextView));
        mMessageTextView2 = ((TextView) findViewById(R.id.messageTextView2));
        mAddressField = ((TextView) findViewById(R.id.device_address));
        mConnectionStateField = (TextView) findViewById(R.id.connection_state);
        mDataFields = (EditText) findViewById(R.id.data_values);

        mAddressField.setText(m_deviceAddress + " [ " + connectionType + " ]");

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

        getActionBar().setSubtitle(m_deviceName);
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

        //if (m_scra.isDeviceConnected())
        {
            m_scra.closeDevice();
        }

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main, menu);

        mMainMenu = menu;

        if (m_connectionState == MTConnectionState.Connected)
        {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            //menu.findItem(R.id.menu_clear_display).setVisible(true);
            menu.findItem(R.id.menu_options).setVisible(false);
/*
            if (m_connectionType == MTConnectionType.Bluetooth)
                menu.findItem(R.id.menu_send).setVisible(false);
            else
                menu.findItem(R.id.menu_send).setVisible(true);
*/
            menu.findItem(R.id.menu_commands).setVisible(true);

            if (m_scra.isDeviceEMV())
            {
                sendToDisplay("This device supports EMV.");
                menu.findItem(R.id.menu_emv).setVisible(true);

                requestEMVMessageFormat();
            }
            else
            {
                menu.findItem(R.id.menu_emv).setVisible(false);
            }
            sendToDisplay("Power Management Value: " + m_scra.getPowerManagementValue());
        }
        else if (m_connectionState == MTConnectionState.Connecting)
        {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
            //menu.findItem(R.id.menu_clear_display).setVisible(false);
            //menu.findItem(R.id.menu_send).setVisible(false);
            menu.findItem(R.id.menu_commands).setVisible(false);
            menu.findItem(R.id.menu_emv).setVisible(false);
            menu.findItem(R.id.menu_options).setVisible(false);
        }
        else if (m_connectionState == MTConnectionState.Disconnecting)
        {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
            //menu.findItem(R.id.menu_clear_display).setVisible(false);
            //menu.findItem(R.id.menu_send).setVisible(false);
            menu.findItem(R.id.menu_commands).setVisible(false);
            menu.findItem(R.id.menu_emv).setVisible(false);
            menu.findItem(R.id.menu_options).setVisible(false);
        }
        else if (m_connectionState == MTConnectionState.Disconnected)
        {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
            //menu.findItem(R.id.menu_clear_display).setVisible(false);
            //menu.findItem(R.id.menu_send).setVisible(false);
            menu.findItem(R.id.menu_commands).setVisible(false);
            menu.findItem(R.id.menu_emv).setVisible(false);

            if ((m_connectionType == MTConnectionType.BLEEMV) || (m_connectionType == MTConnectionType.BLE))
            {
                menu.findItem(R.id.menu_options).setVisible(true);
            }
            else
            {
                menu.findItem(R.id.menu_options).setVisible(false);
            }
        }

        return true;
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

        //String command = new String("491E0000030C00180000000000000000000000000000000000") + dateTimeString;
        String command = new String("49220000030C001C0000000000000000000000000000000000") + dateTimeString + "00000000";

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

    private void sendCustomCommand()
    {
        LayoutInflater panFactory = LayoutInflater.from(this);

        final View panTextEntryView = panFactory.inflate(R.layout.custom_command, null);

        Builder dialog = new AlertDialog.Builder(this);

//	    dialog.setIconAttribute(android.R.attr.alertDialogIcon);
        dialog.setTitle(R.string.menu_send);
        dialog.setView(panTextEntryView);

        dialog.setPositiveButton(R.string.menu_send, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                EditText textEntry = (EditText) panTextEntryView.findViewById(R.id.custom_command_edittext);
                String command = textEntry.getText().toString();
                sendCommand(command);
            }
        });

        dialog.setNegativeButton(R.string.value_cancel, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
            }
        });

        dialog.show();
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

    public void sendGetSwipeOutput()
    {
        String command = "4800";

        sendCommand(command);
    }

    public void sendSetSwipeToBLE()
    {
        String command = "480101";

        sendCommand(command);
    }

    public void sendSetSwipeToUSB()
    {
        String command = "480100";

        sendCommand(command);
    }

    public void sendResetDevice()
    {
        String command = "0200";

        sendCommand(command);
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

        startTransactionWithOptions(type);
    }

    public void startTransactionWithOptions(byte cardType)
    {
        if (m_scra != null)
        {
//            byte timeLimit = 0x3C;
            byte timeLimit = 0x09;
            //byte cardType = 0x02;  // Chip Only
            //byte cardType = 0x03;  // MSR + Chip
            byte option = (isQuickChipEnabled() ? (byte) 0x80:00);
            byte[] amount = new byte[] {0x00, 0x00, 0x00, 0x00, 0x15, 0x00};
            byte transactionType = 0x00; // Purchase
            byte[] cashBack = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            byte[] currencyCode = new byte[] { 0x08, 0x40};
            byte reportingOption = 0x02;  // All Status Changes

            clearMessage();
            clearMessage2();

            int result = m_scra.startTransaction(timeLimit, cardType, option, amount, transactionType, cashBack, currencyCode, reportingOption);

            sendToDisplay("[Start Transaction] (Result=" + result + ")");
        }
    }

    public void setUserSelectionResult(byte status, byte selection)
    {
        if (m_scra != null)
        {
            sendToDisplay("[Sending Selection Result] Status=" + status + " Selection=" + selection);

            m_scra.setUserSelectionResult(status, selection);
        }
    }

    public void setAcquirerResponse(byte[] response)
    {
        if ((m_scra != null) && (response != null))
        {
            sendToDisplay("[Sending Acquirer Response]\n" + TLVParser.getHexString(response));

            m_scra.setAcquirerResponse(response);
        }
    }

    public void cancelTransaction()
    {
        if (m_scra != null)
        {
            m_turnOffLEDPending = true;

            int result = m_scra.cancelTransaction();

            sendToDisplay("[Cancel Transaction] (Result=" + result + ")");
        }
    }

    public void setLED(boolean on)
    {
        if (m_scra != null)
        {
            if (on)
            {
                m_scra.sendCommandToDevice(MTDeviceConstants.SCRA_DEVICE_COMMAND_STRING_SET_LED_ON);
            }
            else
            {
                m_scra.sendCommandToDevice(MTDeviceConstants.SCRA_DEVICE_COMMAND_STRING_SET_LED_OFF);
            }
        }
    }

    public void getTerminalConfiguration()
    {
        if (m_scra != null)
        {
            String commandString 	= "0306";
            String sizeString 		= "0003";
            String dataString 		= "010F00";

            sendToDisplay("[Get Terminal Configuration]");

            m_scra.sendExtendedCommand(commandString + sizeString + dataString);
        }
    }

    public void setTerminalConfiguration()
    {
        if (m_scra != null)
        {
            String commandString 	= "0305";
            String sizeString 		= "001D";
            String dataString 		= "0001010042333039343633303932323135414100FA00000000B75CD164";

            sendToDisplay("[Set Terminal Configuration]");

            m_scra.sendExtendedCommand(commandString + sizeString + dataString);
        }
    }

    public void commitConfiguration()
    {
        if (m_scra != null)
        {
            String commandString 	= "030E";
            String sizeString 		= "0001";
            String dataString 		= "00";

            sendToDisplay("[Commit Configuration]");

            m_scra.sendExtendedCommand(commandString + sizeString + dataString);
        }
    }


    private String getManualAudioConfig()
    {
        String config = "";

        try
        {
            String model = android.os.Build.MODEL.toUpperCase();

            if(model.contains("DROID RAZR") || model.toUpperCase().contains("XT910"))
            {
                config = "INPUT_SAMPLE_RATE_IN_HZ=48000,";
            }
            else if ((model.equals("DROID PRO"))||
                    (model.equals("MB508"))||
                    (model.equals("DROIDX"))||
                    (model.equals("DROID2"))||
                    (model.equals("MB525")))
            {
                config = "INPUT_SAMPLE_RATE_IN_HZ=32000,";
            }
            else if ((model.equals("GT-I9300"))||//S3 GSM Unlocked
                    (model.equals("SPH-L710"))||//S3 Sprint
                    (model.equals("SGH-T999"))||//S3 T-Mobile
                    (model.equals("SCH-I535"))||//S3 Verizon
                    (model.equals("SCH-R530"))||//S3 US Cellular
                    (model.equals("SAMSUNG-SGH-I747"))||// S3 AT&T
                    (model.equals("M532"))||//Fujitsu
                    (model.equals("GT-N7100"))||//Notes 2
                    (model.equals("GT-N7105"))||//Notes 2
                    (model.equals("SAMSUNG-SGH-I317"))||// Notes 2
                    (model.equals("SCH-I605"))||// Notes 2
                    (model.equals("SCH-R950"))||// Notes 2
                    (model.equals("SGH-T889"))||// Notes 2
                    (model.equals("SPH-L900"))||// Notes 2
                    (model.equals("GT-P3113")))//Galaxy Tab 2, 7.0

            {
                config = "INPUT_AUDIO_SOURCE=VRECOG,";
            }
            else if ((model.equals("XT907")))
            {
                config = "INPUT_WAVE_FORM=0,";
            }
            else
            {
                config = "INPUT_AUDIO_SOURCE=VRECOG,";
                //config += "PAN_MOD10_CHECKDIGIT=FALSE";
            }

        }
        catch (Exception ex)
        {

        }

        return config;
    }

    private void startAudioConfigFromFile()
    {
        try
        {
            new LoadAudioConfigFromFileTask().execute("");
        }
        catch (Exception ex)
        {
            Log.i(TAG, "*** Exception");
        }
    }

    private void startAudioConfigFromServer()
    {
        try
        {
            new LoadAudioConfigFromServerTask().execute("");
        }
        catch (Exception ex)
        {
            Log.i(TAG, "*** Exception");
        }
    }

    private void onAudioConfigReceived(String xmlConfig)
    {
        try
        {
            if (m_scra !=null)
            {
                String config = "";

                try
                {
                    if ((xmlConfig != null) && !xmlConfig.isEmpty())
                    {
                        String model = android.os.Build.MODEL.toUpperCase();

                        Log.i(TAG, "*** Model=" + model);

                        MTSCRAConfig scraConfig = new MTSCRAConfig(SCRA_CONFIG_VERSION);

                        ProcessMessageResponse configurationResponse =scraConfig.getConfigurationResponse(xmlConfig);

                        Log.i(TAG, "*** ProcessMessageResponse Count =" + configurationResponse.getPropertyCount());

                        config = scraConfig.getConfigurationParams(model, configurationResponse);
                    }

                    Log.i(TAG, "*** Config=" + config);

                    m_scra.setDeviceConfiguration(config);
                }
                catch (Exception ex)
                {
                    Log.i(TAG, "*** Exception " + ex.getMessage());
                }

                m_scra.openDevice();
            }
        }
        catch (Exception ex)
        {
            Log.i(TAG, "*** Exception");
        }
    }

    private class LoadAudioConfigFromFileTask extends AsyncTask<String, Void, String>
    {
        protected String doInBackground(String... params)
        {
            String xmlConfig = "";

            try
            {
                xmlConfig = getAudioConfigFromFile();
            }
            catch (Exception ex)
            {
                Log.i(TAG, "*** Exception");
            }

            Log.i(TAG, "*** XML Config=" + xmlConfig);

            return xmlConfig;
        }

        @Override
        protected void onPostExecute(String result)
        {
            onAudioConfigReceived(result);
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }

    private class LoadAudioConfigFromServerTask extends AsyncTask<String, Void, String>
    {
        protected String doInBackground(String... params)
        {
            String xmlConfig = "";

            try
            {
                String model = android.os.Build.MODEL.toUpperCase();

                Log.i(TAG, "*** Model=" + model);

                MTSCRAConfig scraConfig = new MTSCRAConfig(SCRA_CONFIG_VERSION);

                SCRAConfigurationDeviceInfo deviceInfo = new SCRAConfigurationDeviceInfo();
                deviceInfo.setProperty(SCRAConfigurationDeviceInfo.PROP_PLATFORM, "Android");
                deviceInfo.setProperty(SCRAConfigurationDeviceInfo.PROP_MODEL, model);

                xmlConfig = scraConfig.getConfigurationXML(CONFIGWS_USERNAME, CONFIGWS_PASSWORD, CONFIGWS_READERTYPE, deviceInfo, CONFIGWS_URL, CONFIGWS_TIMEOUT);


                saveAudioConfigToFile(xmlConfig);
            }
            catch (Exception ex)
            {
                Log.i(TAG, "*** Exception");
            }

            Log.i(TAG, "*** XML Config=" + xmlConfig);

            return xmlConfig;
        }

        @Override
        protected void onPostExecute(String result)
        {
            onAudioConfigReceived(result);
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }

    public long closeDevice()
    {
        Log.i(TAG, "SCRADevice closeDevice");

        long result = -1;

        if (m_scra != null)
        {
            m_scra.closeDevice();

            result = 0;
        }

        return result;
    }

    public long openDevice()
    {
        Log.i(TAG, "SCRADevice openDevice");

        long result = -1;
/*
        if (m_connectionType == MTConnectionType.BLEEMV)
        {
            if(m_connectionState!=MTConnectionState.Disconnected)
            {
                Log.i(TAG, "SCRADevice openDevice:Device Not Disconnected");
                return 0;
            }
        }
*/
        if (m_scra != null)
        {
            m_scra.setConnectionType(m_connectionType);
            m_scra.setAddress(m_deviceAddress);

            boolean enableRetry = false;

            if (mMainMenu != null)
            {
                enableRetry = mMainMenu.findItem(R.id.menu_connection_retry).isChecked();
            }

            m_scra.setConnectionRetry(enableRetry);

            if (m_connectionType == MTConnectionType.Audio)
            {
                if (m_audioConfigType.equalsIgnoreCase("1"))
                {
                    // Manual Configuration
                    Log.i(TAG, "*** Manual Audio Config");
                    m_scra.setDeviceConfiguration(getManualAudioConfig());
                }
                else if (m_audioConfigType.equalsIgnoreCase("2"))
                {
                    // Configuration File
                    Log.i(TAG, "*** Audio Config From File");
                    startAudioConfigFromFile();
                    return 0;
                }
                else if (m_audioConfigType.equalsIgnoreCase("3"))
                {
                    // Configuration From Server
                    Log.i(TAG, "*** Audio Config From Server");
                    startAudioConfigFromServer();
                    return 0;
                }
            }

            m_scra.openDevice();

            result = 0;
        }

        return result;
    }

    public boolean isDeviceOpened() {
        Log.i(TAG, "SCRADevice isDeviceOpened");

        return (m_connectionState == MTConnectionState.Connected);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case android.R.id.home:
                //if (m_connectionState != MTConnectionState.Connecting)
            {
                onBackPressed();
            }
            return true;
            case R.id.menu_connect:
                if (openDevice() != 0)
                {
                    sendToDisplay("[Failed to connect to the device]");
                }
                return true;
            case R.id.menu_disconnect:
                closeDevice();
                return true;
            case R.id.menu_clear_display:
                clearDisplay();
                return true;
            case R.id.menu_send:
                sendCustomCommand();
                return true;
            case R.id.menu_start_transaction:
                //startTransactionWithLED();
                showTransactionTypes();
                return true;
            case R.id.menu_cancel_transaction:
                cancelTransaction();
                return true;
            case R.id.menu_emv_set_time:
                sendSetDateTimeCommand();
                return true;
            case R.id.menu_get_device_info:
                getDeviceInfo();
                return true;
            case R.id.menu_get_battery_level:
                getBatteryLevel();
                return true;
            case R.id.menu_get_ksn:
                getKSN();
                return true;
            case R.id.menu_clear_buffers:
                clearBuffers();
                return true;
            case R.id.menu_set_msr_on:
                setMSRPower(true);
                return true;
            case R.id.menu_set_msr_off:
                setMSRPower(false);
                return true;
            case R.id.menu_emv_approved:
                item.setChecked(!item.isChecked());
                return true;
            case R.id.menu_emv_quickchip:
                boolean enableQuickChip = !item.isChecked();
                item.setChecked(enableQuickChip);
                if (mMainMenu != null)
                {
                    mMainMenu.findItem(R.id.menu_emv_approved).setEnabled(!enableQuickChip);
                }
                return true;
            case R.id.menu_connection_retry:
                boolean enableRetry =  !item.isChecked();
                item.setChecked(enableRetry);
                return true;
            case R.id.menu_emv_get_terminal_config:
                getTerminalConfiguration();
                return true;
            case R.id.menu_emv_set_terminal_config:
                setTerminalConfiguration();
                return true;
            case R.id.menu_emv_commit_config:
                commitConfiguration();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setVolume(int volume)
    {
        m_audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void saveVolume()
    {
        m_audioVolume = m_audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    private void restoreVolume()
    {
        setVolume(m_audioVolume);
    }

    private void setVolumeToMax()
    {
        saveVolume();

        int volume = m_audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        setVolume(volume);
    }

    private void updateConnectionState(final int resourceId)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mConnectionStateField.setText(resourceId);
            }
        });
    }

    private void updateDisplay()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (m_connectionState == MTConnectionState.Connected)
                {
                    updateConnectionState(R.string.connected);
                }
                else if (m_connectionState == MTConnectionState.Connecting)
                {
                    updateConnectionState(R.string.connecting);
                }
                else if (m_connectionState == MTConnectionState.Disconnecting)
                {
                    updateConnectionState(R.string.disconnecting);
                }
                else if (m_connectionState == MTConnectionState.Disconnected)
                {
                    updateConnectionState(R.string.disconnected);
                }
            }
        });
    }

    private void clearMessage()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mMessageTextView.setText("");
            }
        });
    }

    private void clearMessage2()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mMessageTextView2.setText("");
            }
        });
    }

    private void clearDisplay()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mMessageTextView.setText("");
                mMessageTextView2.setText("");
                mDataFields.setText("");
            }
        });
    }

    public void sendToDisplay(final String data)
    {
        if (data != null)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mDataFields.append(data + "\n");
                }
            });
        }
    }

    private void displayMessage(final String message)
    {
        if (message != null)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mMessageTextView.setText(message);
                }
            });
        }
    }

    private void displayMessage2(final String message)
    {
        if (message != null)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mMessageTextView2.setText(message);
                }
            });
        }
    }

    public String formatStringIfNotEmpty(String format, String data)
    {
        String result = "";

        if (!data.isEmpty())
        {
            result = String.format(format, data);
        }

        return result;
    }

    public String formatStringIfNotValueZero(String format, int data)
    {
        String result = "";

        if (data != 0)
        {
            result = String.format(format, data);
        }

        return result;
    }

    public String getCardInfo()
    {
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

        //stringBuilder.append(formatStringIfNotEmpty("Device.Name=%s \n", m_scra.getDeviceName()));
        //stringBuilder.append(String.format("Swipe.Count=%d \n", m_scra.getSwipeCount()));

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

        //stringBuilder.append(formatStringIfNotEmpty("Firmware=%s \n", m_scra.getFirmware()));

        stringBuilder.append(formatStringIfNotEmpty("MagTek.Device.Serial=%s \n", m_scra.getMagTekDeviceSerial()));

        stringBuilder.append(formatStringIfNotEmpty("Response.Type=%s \n", m_scra.getResponseType()));
        stringBuilder.append(formatStringIfNotEmpty("TLV.Version=%s \n", m_scra.getTLVVersion()));

        stringBuilder.append(String.format("Track.Decode.Status=%s \n", m_scra.getTrackDecodeStatus()));

        String tkStatus = m_scra.getTrackDecodeStatus();

        String tk1Status = "01";
        String tk2Status = "01";
        String tk3Status = "01";

        if (tkStatus.length() >= 6)
        {
            tk1Status = tkStatus.substring(0, 2);
            tk2Status = tkStatus.substring(2, 4);
            tk3Status = tkStatus.substring(4, 6);

            stringBuilder.append(String.format("Track1.Status=%s \n", tk1Status));
            stringBuilder.append(String.format("Track2.Status=%s \n", tk2Status));
            stringBuilder.append(String.format("Track3.Status=%s \n", tk3Status));
        }

        stringBuilder.append(String.format("SDK.Version=%s \n", m_scra.getSDKVersion()));

        stringBuilder.append(String.format("Battery.Level=%d \n", m_scra.getBatteryLevel()));

        return stringBuilder.toString();
    }

    public class NoisyAudioStreamReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            /* If the device is unplugged, this will immediately detect that action,
             * and close the device.
             */
            if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
            {
                if (m_connectionType == MTConnectionType.Audio)
                {
                    if(m_scra.isDeviceConnected())
                    {
                        closeDevice();
                    }
                }
            }
        }
    }

    public class HeadSetBroadCastReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent) {

            try
            {
                String action = intent.getAction();

                if( (action.compareTo(Intent.ACTION_HEADSET_PLUG))  == 0)   //if the action match a headset one
                {
                    int headSetState = intent.getIntExtra("state", 0);      //get the headset state property
                    int hasMicrophone = intent.getIntExtra("microphone", 0);//get the headset microphone property

                    if( (headSetState == 1) && (hasMicrophone == 1))        //headset was unplugged & has no microphone
                    {
                    }
                    else
                    {
                        if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                        {
                            if (m_connectionType == MTConnectionType.Audio)
                            {
                                if(m_scra.isDeviceConnected())
                                {
                                    closeDevice();
                                }
                            }
                        }
                    }

                }

            }
            catch(Exception ex)
            {

            }
        }
    }

    public String getAudioConfigFromFile()
    {
        String config = "";

        try
        {
            config = ReadSettings(getApplicationContext(), AUDIO_CONFIG_FILE);

            if (config==null)
            {
                config = "";
            }
        }
        catch (Exception ex)
        {
        }

        return config;
    }

    public void saveAudioConfigToFile(String xmlConfig)
    {
        try
        {
            WriteSettings(getApplicationContext(), xmlConfig, AUDIO_CONFIG_FILE);
        }
        catch (Exception ex)
        {

        }
    }

    public static String ReadSettings(Context context, String file) throws IOException
    {
        FileInputStream fis = null;
        InputStreamReader isr = null;
        String data = null;
        fis = context.openFileInput(file);
        isr = new InputStreamReader(fis);
        char[] inputBuffer = new char[fis.available()];
        isr.read(inputBuffer);
        data = new String(inputBuffer);
        isr.close();
        fis.close();
        return data;
    }

    public static void WriteSettings(Context context, String data, String file) throws IOException
    {
        FileOutputStream fos= null;
        OutputStreamWriter osw = null;
        fos= context.openFileOutput(file,Context.MODE_PRIVATE);
        osw = new OutputStreamWriter(fos);
        osw.write(data);
        osw.close();
        fos.close();
    }
}

package com.magtek.mobile.android.mtscrademo;


import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;

public class DeviceScanActivity extends ListActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectAudioDevice();
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
    }




    private void selectAudioDevice() {

        final Intent intent = new Intent(this, MagTekDemo.class);
        intent.putExtra(MagTekDemo.EXTRAS_CONNECTION_TYPE, MagTekDemo.EXTRAS_CONNECTION_TYPE_VALUE_AUDIO);
        intent.putExtra(MagTekDemo.EXTRAS_DEVICE_NAME, "Audio");
        intent.putExtra(MagTekDemo.EXTRAS_DEVICE_ADDRESS, "");
        intent.putExtra(MagTekDemo.EXTRAS_AUDIO_CONFIG_TYPE, "0");
        startActivity(intent);
    }

}
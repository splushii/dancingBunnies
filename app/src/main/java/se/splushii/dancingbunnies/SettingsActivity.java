package se.splushii.dancingbunnies;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setSupportActionBar(findViewById(R.id.main_toolbar));
        setContentView(R.layout.settings_activity_layout);
    }

}

package ml.bmlzootown.hydravion.browse;

import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.detail.VideoDetailsFragment;

/*
 * Main Activity class that loads {@link MainFragment}.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_Browse);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportFragmentManager().beginTransaction().add(R.id.main_browse_fragment, new MainFragment()).commit();
    }
}

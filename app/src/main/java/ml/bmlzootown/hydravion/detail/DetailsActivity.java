package ml.bmlzootown.hydravion.detail;

import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import ml.bmlzootown.hydravion.R;

/*
 * Details activity class that loads LeanbackDetailsFragment class
 */
public class DetailsActivity extends AppCompatActivity {

    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String Video = "Video";
    public static final String Resume = "Resume";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_Video);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        getSupportFragmentManager().beginTransaction().add(R.id.details_fragment, new VideoDetailsFragment()).commit();
    }
}

package ml.bmlzootown.hydravion.detail

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ml.bmlzootown.hydravion.R

/*
 * Details activity class that loads LeanbackDetailsFragment class
 */
class DetailsActivity : AppCompatActivity() {
    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Video)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        supportFragmentManager.beginTransaction().add(R.id.details_fragment, VideoDetailsFragment()).commit()
    }

    override fun onBackPressed() {
        setResult(RESULT_OK, Intent().putExtra("REFRESH", true))
        super.onBackPressed()
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val Video = "Video"
        const val Resume = "Resume"
    }
}
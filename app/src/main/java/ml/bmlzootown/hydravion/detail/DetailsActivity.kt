package ml.bmlzootown.hydravion.detail

import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.ThemeManager
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.models.VideoTypeUtil

/*
 * Details activity class that loads LeanbackDetailsFragment class
 */
class DetailsActivity : AppCompatActivity() {
    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val video = intent.getSerializableExtra(Video) as? Video
            if (video == null || !VideoTypeUtil.isTextPost(video)) {
                postponeEnterTransition()
            }
        }
        setContentView(R.layout.activity_details)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_fragment, VideoDetailsFragment())
                .commit()
        }
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
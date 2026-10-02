package ml.bmlzootown.hydravion.browse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.ThemeManager
import ml.bmlzootown.hydravion.browse.MainFragment

/*
 * Main Activity class that loads {@link MainFragment}.
 */
class MainActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainFragment())
                .commit()
        }
    }
}
package com.saucedplussytv.androidtv.browse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.saucedplussytv.androidtv.R
import com.saucedplussytv.androidtv.browse.MainFragment

/*
 * Main Activity class that loads {@link MainFragment}.
 */
class MainActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Browse)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportFragmentManager.beginTransaction().add(R.id.main_browse_fragment, MainFragment()).commit()
    }
}
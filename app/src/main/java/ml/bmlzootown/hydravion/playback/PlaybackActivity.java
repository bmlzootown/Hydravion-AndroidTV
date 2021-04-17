package ml.bmlzootown.hydravion.playback;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.MainFragment;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.models.Video;

public class PlaybackActivity extends FragmentActivity {

    private PlayerView playerView;
    private SimpleExoPlayer player;

    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;

    private String url = "";
    private Video video;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        /*if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new PlaybackVideoFragment())
                    .commit();
        }*/
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final Video video = (Video) getIntent().getSerializableExtra(DetailsActivity.Video);
        this.video = video;
        //url = video.getVidUrl().replaceAll("Edge01-na.floatplane.com", "edge03-na.floatplane.com");
        Log.d("SERVER", MainFragment.cdn);
        Log.d("SERVER", "test");
        url = video.getVidUrl().replaceAll("Edge01-na.floatplane.com", MainFragment.cdn);

        playerView = findViewById(R.id.exoplayer);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    private void initializePlayer() {
        player = new SimpleExoPlayer.Builder(this).build();
        player.setPlayWhenReady(playWhenReady);
        player.seekTo(currentWindow, playbackPosition);
        playerView.setPlayer(player);
        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory();
        String cookies = "__cfduid=" + MainFragment.cfduid + ";sails.sid=" + MainFragment.sailssid + ";";
        dataSourceFactory.getDefaultRequestProperties().set("Cookie", cookies);
        HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url));
        player.setMediaSource(hlsMediaSource);
        //MediaItem mediaItem = MediaItem.fromUri("https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4");
        //player.setMediaItem(mediaItem);

        player.prepare();

        player.addListener(new Player.EventListener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                if (video != null) {
                    if (video.getTitle() == null) {
                        releasePlayer();
                        Toast.makeText(PlaybackActivity.this, "Livestream not found!", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                Log.d("STATE", state + "");
                if (state == Player.STATE_ENDED) {
                    releasePlayer();
                }
            }
        });
    }

    private void releasePlayer() {
        if (player != null) {
            playWhenReady = player.getPlayWhenReady();
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            //player.removeListener((Player.EventListener) this);
            player.stop();
            player.release();
            player = null;
            this.finish();
        }
    }

    @SuppressLint("InlinedApi")
    private void hideSystemUi() {
        playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}

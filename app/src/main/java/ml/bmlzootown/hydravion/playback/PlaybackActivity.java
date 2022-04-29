package ml.bmlzootown.hydravion.playback;

import static ml.bmlzootown.hydravion.browse.MainFragment.bsf;
import static ml.bmlzootown.hydravion.browse.MainFragment.subscriptions;
import static ml.bmlzootown.hydravion.browse.MainFragment.videos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.util.HashMap;
import java.util.List;

import kotlin.Unit;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.browse.GridItemPresenter;
import ml.bmlzootown.hydravion.browse.MainFragment;
import ml.bmlzootown.hydravion.card.CardPresenter;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.subscription.Subscription;

public class PlaybackActivity extends FragmentActivity {

    private HydravionClient client;
    private SharedPreferences defaultPrefs;

    private PlayerView playerView;
    private ImageView like;
    private ImageView dislike;
    private ImageView menu;
    private ImageView speed;
    private LinearLayout exo_playback_menu;
    private LinearLayout exo_settings_menu;
    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaController;

    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;

    private String url = "";
    private Video video;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        client = HydravionClient.Companion.getInstance(this, getPreferences(Context.MODE_PRIVATE));
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(this);
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
        url = video.getVidUrl().replaceAll("Edge01-na.floatplane.com", MainFragment.cdn);

        playerView = findViewById(R.id.exoplayer);
        ((TextView) findViewById(R.id.exo_title)).setText(video.getTitle());
        like = findViewById(R.id.exo_like);
        dislike = findViewById(R.id.exo_dislike);
        menu = findViewById(R.id.exo_menu);
        exo_playback_menu = findViewById(R.id.exo_playback_menu);
        exo_settings_menu = findViewById(R.id.exo_settings_menu);
        speed = findViewById(R.id.exo_speed);
        setupLikeAndDislike();
        setupMenu();

        playerView.setControllerVisibilityListener(visibility -> {
            if (visibility != View.VISIBLE) {
                exo_playback_menu.setVisibility(View.VISIBLE);
                exo_settings_menu.setVisibility(View.GONE);
            }
        });

        // setup media session
        mediaSession = new MediaSessionCompat(this, getPackageName());
        mediaController = new MediaSessionConnector(mediaSession);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (Util.SDK_INT > 23) {
            initializePlayer();
            mediaController.setPlayer(player);
            mediaSession.setActive(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
            mediaController.setPlayer(player);
            mediaSession.setActive(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (Util.SDK_INT <= 23) {
            mediaController.setPlayer(null);
            mediaSession.setActive(false);
            saveVideoPosition();
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (Util.SDK_INT > 23) {
            mediaController.setPlayer(null);
            mediaSession.setActive(false);
            quickRefreshRows();
            saveVideoPosition();
            releasePlayer();
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Hide the
        if (playerView.isControllerVisible()) {
            //playerView.hideController();
            if (exo_playback_menu.getVisibility() == View.VISIBLE) {
                playerView.hideController();
            } else {
                exo_settings_menu.setVisibility(View.GONE);
                exo_playback_menu.setVisibility(View.VISIBLE);
            }
        } else {
            super.onBackPressed();
        }
    }

    private void quickRefreshRows() {
        List<Subscription> subs = subscriptions;
        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter();

        int i;
        for (i = 0; i < subs.size(); i++) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);

            Subscription sub = subscriptions.get(i);
            List<Video> vids = videos.get(sub.getCreator());

            if (vids != null) {
                vids.forEach(listRowAdapter::add);
            }

            HeaderItem header = new HeaderItem(i, sub.getPlan().getTitle());
            rowsAdapter.add(new ListRow(header, listRowAdapter));
        }

        HeaderItem gridHeader = new HeaderItem(i, getString(R.string.settings));

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.refresh));
        gridRowAdapter.add(getResources().getString(R.string.live_stream));
        //gridRowAdapter.add(getResources().getString(R.string.select_server));
        gridRowAdapter.add(getResources().getString(R.string.app_info));
        gridRowAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        bsf.setAdapter(rowsAdapter);
    }

    private void setupLikeAndDislike() {
        client.getPost(video.getId(), post -> {
            if (!post.getUserInteractions().isEmpty()) {
                if (post.isLiked()) {
                    like.setImageResource(R.drawable.ic_like);
                } else if (post.isDisliked()) {
                    dislike.setImageResource(R.drawable.ic_dislike);
                }
            }

            return Unit.INSTANCE;
        });

        like.setOnClickListener(v -> client.toggleLikePost(video.getId(), liked -> {
            if (liked) {
                like.setImageResource(R.drawable.ic_like);
            } else {
                like.setImageResource(R.drawable.ic_like_unselected);
            }

            dislike.setImageResource(R.drawable.ic_dislike_unselected);
            return Unit.INSTANCE;
        }));
        dislike.setOnClickListener(v -> client.toggleDislikePost(video.getId(), disliked -> {
            if (disliked) {
                dislike.setImageResource(R.drawable.ic_dislike);
            } else {
                dislike.setImageResource(R.drawable.ic_dislike_unselected);
            }

            like.setImageResource(R.drawable.ic_like_unselected);
            return Unit.INSTANCE;
        }));
    }

    private void setupMenu() {
        // Show settings menu
        menu.setOnClickListener(v -> {
            exo_playback_menu.setVisibility(View.GONE);
            exo_settings_menu.setVisibility(View.VISIBLE);
        });

        speed.setOnClickListener(v -> showSpeedDialog());
    }

    private void showSpeedDialog() {
        PopupMenu speedMenu = new PopupMenu(this, speed);
        String[] playerSpeedArrayLabels = {"0.5x", "1.0x", "1.25x", "1.5x", "2.0x"};

        for (int i = 0; i < playerSpeedArrayLabels.length; i++) {
            speedMenu.getMenu().add(i, i, i, playerSpeedArrayLabels[i]);
        }

        speedMenu.setOnMenuItemClickListener(item -> {
            String itemTitle = item.getTitle().toString();
            float playbackSpeed = Float.parseFloat(itemTitle.substring(0, itemTitle.length() - 1));

            String msg = "Playback Speed: " + itemTitle;
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            player.setPlaybackSpeed(playbackSpeed);
            return false;
        });

        speedMenu.show();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setPlayWhenReady(playWhenReady);
        player.seekTo(currentWindow, playbackPosition);
        playerView.setPlayer(player);
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        HashMap<String, String> cookieMap = new HashMap<>();
        cookieMap.put("Cookie", "sails.sid=" + MainFragment.sailssid + ";");
        dataSourceFactory.setDefaultRequestProperties(cookieMap);
        int flags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
        DefaultHlsExtractorFactory extractorFactory = new DefaultHlsExtractorFactory(flags, true);
        //url = "https://cdn-vod-drm2.floatplane.com/Videos/CyKnsF4ZuT/2160.mp4/chunk.m3u8?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyZXNzb3VyY2VQYXRoIjoiL1ZpZGVvcy9DeUtuc0Y0WnVULzIxNjAubXA0L2NodW5rLm0zdTgiLCJ1c2VySWQiOiI2MGU5MGUxYjgwMGM0NTE2YzQzYzU0ZTQiLCJpYXQiOjE2MjU5MzQ2MDQsImV4cCI6MTYyNTk1NjIwNH0.BUazqG_Pgd9ribOQ2jyQoLg9QiX77bC6ToGurfFo_pQ";
        MediaItem mi = MediaItem.fromUri(url);
        HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory).setExtractorFactory(extractorFactory).createMediaSource(mi);
        player.setMediaSource(hlsMediaSource);

        player.prepare();

        if (getIntent().getBooleanExtra(DetailsActivity.Resume, false)) {
            player.seekTo(defaultPrefs.getLong(video.getGuid(), 0));
        }

        player.addListener(new Player.Listener() {

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (video != null) {
                    releasePlayer();
                    Toast.makeText(PlaybackActivity.this, "Video could not be played!", Toast.LENGTH_LONG).show();
                }
                Log.e("EXOPLAYER", error.getLocalizedMessage());
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                Log.d("STATE", state + "");
                if (state == Player.STATE_ENDED) {
                    saveVideoPosition();
                    releasePlayer();
                }
            }
        });
    }

    private void saveVideoPosition() {
        if (player != null) {
            defaultPrefs.edit().putLong(video.getGuid(), player.getCurrentPosition()).apply();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playWhenReady = player.getPlayWhenReady();
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentMediaItemIndex();
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

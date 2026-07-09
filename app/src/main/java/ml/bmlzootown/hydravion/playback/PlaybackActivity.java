package ml.bmlzootown.hydravion.playback;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
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

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.common.util.Util;
import android.content.SharedPreferences;

import java.util.HashMap;

import kotlin.Unit;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.ThemeManager;
import ml.bmlzootown.hydravion.authenticate.AuthManager;
import ml.bmlzootown.hydravion.browse.MainFragment;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.models.Video;

public class PlaybackActivity extends FragmentActivity {

    private HydravionClient client;

    private PlayerView playerView;
    private ImageView like;
    private ImageView dislike;
    private ImageView menu;
    private ImageView speed;
    private LinearLayout exo_playback_menu;
    private LinearLayout exo_settings_menu;
    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private MediaSession mediaSession3;

    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;
    private boolean resumed = false;
    private boolean playerInitialized = false;
    private boolean initializationInProgress = false;
    private boolean isControllerVisible = false;

    private String url = "";
    private Video video;

    @SuppressLint("MissingInflatedId")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        client = HydravionClient.getInstance(this);
        // SurfaceView + hardware decoder often stalls on emulators after ~1s of playback.
        // TextureView is more compatible; real devices still use SurfaceView from XML.
        if (isEmulator()) {
            MainFragment.dLog("PLAYBACK", "Emulator detected — inflating TextureView player");
            setContentView(R.layout.activity_player_emulator);
        } else {
            setContentView(R.layout.activity_player);
        }

        final Video video = (Video) getIntent().getSerializableExtra(DetailsActivity.Video);
        this.video = video;
        url = video.getVidUrl();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                isControllerVisible = (visibility == View.VISIBLE);
                if (visibility != View.VISIBLE) {
                    exo_playback_menu.setVisibility(View.VISIBLE);
                    exo_settings_menu.setVisibility(View.GONE);
                }
            }
        });

        // setup media session (legacy MediaSessionCompat for compatibility)
        mediaSession = new MediaSessionCompat(this, getPackageName());
    }

    @Override
    public void onStart() {
        super.onStart();

        if (Util.SDK_INT > 23) {
            // Only initialize if not already initialized or in progress
            if (!playerInitialized && !initializationInProgress && player == null) {
                initializePlayer();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Util.SDK_INT <= 23 || player == null) {
            // Only initialize if not already initialized or in progress
            if (!playerInitialized && !initializationInProgress) {
                initializePlayer();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (Util.SDK_INT <= 23) {
            // Cancel any in-progress initialization
            if (initializationInProgress) {
                initializationInProgress = false;
            }
            
            if (playerInitialized && mediaSession3 != null) {
                mediaSession3.release();
                mediaSession3 = null;
                playerInitialized = false;
            }
            mediaSession.setActive(false);
            saveVideoPosition();
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (Util.SDK_INT > 23) {
            // Cancel any in-progress initialization
            if (initializationInProgress) {
                initializationInProgress = false;
            }
            
            if (playerInitialized && mediaSession3 != null) {
                mediaSession3.release();
                mediaSession3 = null;
                playerInitialized = false;
            }
            mediaSession.setActive(false);
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
        // Hide the menu
        if (isControllerVisible) {
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
        // Mark initialization as in progress
        initializationInProgress = true;
        
        AuthManager authManager = AuthManager.getInstance(this);
        authManager.withValidAccessToken(accessToken -> {
            // Check if initialization was cancelled or activity is no longer valid
            if (!initializationInProgress || isFinishing() || isDestroyed()) {
                initializationInProgress = false;
                return Unit.INSTANCE;
            }
            
            player = new ExoPlayer.Builder(this).build();
            
            // Set player on PlayerView BEFORE setting media source to ensure surface is ready
            // PlayerView uses SurfaceView (configured in XML) for better hardware decoder performance
            // SurfaceView provides lower power consumption, smoother playback, and better HDR/DRM support
            playerView.setPlayer(player);
            
            player.setPlayWhenReady(playWhenReady);
            player.seekTo(currentWindow, playbackPosition);

            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            String version = ml.bmlzootown.hydravion.BuildConfig.VERSION_NAME;
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("User-Agent", "Hydravion (AndroidTV " + version + ")");
            dataSourceFactory.setDefaultRequestProperties(headers);

            MediaItem mi = MediaItem.fromUri(url);
            
            // Get output format preference to determine which MediaSource to use
            android.content.SharedPreferences prefs = getSharedPreferences(ml.bmlzootown.hydravion.Constants.PREF_FILE_NAME, Context.MODE_PRIVATE);
            String outputFormat = prefs.getString(ml.bmlzootown.hydravion.Constants.PREF_OUTPUT_FORMAT, ml.bmlzootown.hydravion.Constants.OUTPUT_FORMAT_DEFAULT);
            
            MainFragment.dLog("MEDIA", "Format preference: " + outputFormat + ", URL: " + url);
            
            // Prioritize user preference over URL detection
            // The API might return HLS URLs even when flat is requested if flat isn't available
            boolean isHls = outputFormat.startsWith("hls.");
            boolean isDash = outputFormat.startsWith("dash.");
            boolean isFlat = outputFormat.equals(ml.bmlzootown.hydravion.Constants.OUTPUT_FORMAT_FLAT);
            
            // If preference doesn't match, fall back to URL detection
            if (!isHls && !isDash && !isFlat) {
                MainFragment.dLog("MEDIA", "Format preference not recognized, detecting from URL");
                isHls = url.contains(".m3u8");
                isDash = url.contains(".mpd");
                isFlat = url.endsWith(".mp4") && !isHls && !isDash;
            }
            
            MediaSource mediaSource;
            
            if (isHls) {
                // HLS format (hls.mpegts or hls.fmp4)
                MainFragment.dLog("MEDIA", "Using HLS MediaSource for format: " + outputFormat);
                int flags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
                DefaultHlsExtractorFactory extractorFactory = new DefaultHlsExtractorFactory(flags, true);
                HlsMediaSource.Factory hlsFactory = new HlsMediaSource.Factory(dataSourceFactory)
                        .setExtractorFactory(extractorFactory);
                mediaSource = hlsFactory.createMediaSource(mi);
            } else if (isDash) {
                // DASH format (dash.mpegts or dash.m4s)
                MainFragment.dLog("MEDIA", "Using DASH MediaSource for format: " + outputFormat);
                DashMediaSource.Factory dashFactory = new DashMediaSource.Factory(dataSourceFactory);
                mediaSource = dashFactory.createMediaSource(mi);
            } else if (isFlat) {
                // Flat MP4 format
                MainFragment.dLog("MEDIA", "Using Progressive MediaSource for flat MP4 format");
                ProgressiveMediaSource.Factory progressiveFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
                mediaSource = progressiveFactory.createMediaSource(mi);
            } else {
                // Fallback: default to HLS if we can't determine
                MainFragment.dLog("MEDIA", "Unknown format, defaulting to HLS MediaSource");
                int flags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
                DefaultHlsExtractorFactory extractorFactory = new DefaultHlsExtractorFactory(flags, true);
                HlsMediaSource.Factory hlsFactory = new HlsMediaSource.Factory(dataSourceFactory)
                        .setExtractorFactory(extractorFactory);
                mediaSource = hlsFactory.createMediaSource(mi);
            }
            
            player.setMediaSource(mediaSource);

            player.prepare();

            // Set up player listener
            player.addListener(new Player.Listener() {

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    if (video != null) {
                        releasePlayer();
                        Toast.makeText(PlaybackActivity.this, "Video could not be played!", Toast.LENGTH_LONG).show();
                    }
                    MainFragment.dError("EXOPLAYER", error.getLocalizedMessage());
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    MainFragment.dLog("STATE", state + "");
                    switch (state) {
                        case Player.STATE_BUFFERING:
                            MainFragment.dLog("PLAYBACK", "Buffering…");
                            break;
                        case Player.STATE_READY:
                            if (getIntent().getBooleanExtra(DetailsActivity.Resume, false) && !resumed) {
                                player.seekTo(video.getVideoInfo().getProgress() * 1000);
                                resumed = true;
                            }
                            break;
                        case Player.STATE_ENDED:
                            saveVideoPosition();
                            releasePlayer();
                            break;
                        default:
                            break;
                    }
                }
            });

            // Only set up media session if activity is still in a valid state
            if (!isFinishing() && !isDestroyed()) {
                // Set up Media3 MediaSession
                if (mediaSession3 == null) {
                    mediaSession3 = new MediaSession.Builder(this, player).build();
                }
                // Also keep legacy MediaSessionCompat active for compatibility
                mediaSession.setActive(true);
                playerInitialized = true;
            } else {
                // Activity is no longer valid, release the player
                if (player != null) {
                    player.release();
                    player = null;
                }
            }
            
            initializationInProgress = false;
            return Unit.INSTANCE;
        }, () -> {
            initializationInProgress = false;
            // Only treat as session expiry when credentials were actually cleared.
            // Transient network failures after TV wake must not force a re-login.
            if (!authManager.hasRefreshToken()) {
                Toast.makeText(this, "Session expired. Please relink your account.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "Could not refresh session. Check your network and try again.", Toast.LENGTH_LONG).show();
                // Leave the activity open so the user can retry (e.g. press play / resume again).
            }
            return Unit.INSTANCE;
        });
    }

    private void saveVideoPosition() {
        // Livestreams have no videoId; don't post progress for them
        if (player != null && video != null && video.getVideoId() != null) {
            client.setVideoProgress(video.getVideoId(), (int) (player.getCurrentPosition() / 1000));
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playWhenReady = player.getPlayWhenReady();
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentMediaItemIndex();
            player.stop();
            player.release();
            player = null;
            playerInitialized = false;
            initializationInProgress = false;
        }
        
        if (mediaSession3 != null) {
            mediaSession3.release();
            mediaSession3 = null;
        }
        
        this.finish();
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.contains("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("goldfish");
    }
}

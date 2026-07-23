package ml.bmlzootown.hydravion.playback;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
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

import java.util.HashMap;
import java.util.List;
import java.util.List;

import kotlin.Unit;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.ThemeManager;
import ml.bmlzootown.hydravion.authenticate.AuthManager;
import ml.bmlzootown.hydravion.browse.MainFragment;
import ml.bmlzootown.hydravion.chat.ChatEmote;
import ml.bmlzootown.hydravion.chat.LiveChatAdapter;
import ml.bmlzootown.hydravion.chat.LiveChatClient;
import ml.bmlzootown.hydravion.chat.RadioChatter;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.poll.LivePollPanelController;

public class PlaybackActivity extends FragmentActivity {

    private HydravionClient client;

    private PlayerView playerView;
    private ImageView like;
    private ImageView dislike;
    private ImageView chatToggle;
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
    private boolean isLivestream = false;

    private View liveChatPanel;
    private View playerSidePanel;
    private TextView liveChatStatus;
    private LiveChatAdapter liveChatAdapter;
    private LiveChatClient liveChatClient;
    private LivePollPanelController livePollPanel;
    private boolean liveChatVisible = false;

    private AnalyticsListener playbackAnalytics;

    @SuppressLint("MissingInflatedId")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        client = HydravionClient.getInstance(this);

        final Video video = (Video) getIntent().getSerializableExtra(DetailsActivity.Video);
        this.video = video;
        url = video.getVidUrl();
        isLivestream = video.getType() != null && video.getType().equalsIgnoreCase("live");

        // TextureView avoids known Google TV Streamer / MediaTek freezes with SurfaceView
        // (video stalls while audio continues). Emulators also need TextureView —
        // SurfaceView + HW decode often stalls after ~1s.
        if (isEmulator() || isLivestream) {
            MainFragment.dLog("PLAYBACK", "Using TextureView player (emulator=" + isEmulator()
                    + ", live=" + isLivestream + ")");
            setContentView(R.layout.activity_player_emulator);
        } else {
            setContentView(R.layout.activity_player);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playerView = findViewById(R.id.exoplayer);
        ((TextView) findViewById(R.id.exo_title)).setText(video.getTitle());
        like = findViewById(R.id.exo_like);
        dislike = findViewById(R.id.exo_dislike);
        chatToggle = findViewById(R.id.exo_chat);
        setupLikeAndDislike();
        setupLiveChat();
        setupLivePoll();

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                isControllerVisible = (visibility == View.VISIBLE);
            }
        });

        // setup media session (legacy MediaSessionCompat for compatibility)
        mediaSession = new MediaSessionCompat(this, getPackageName());
    }

    private void setupLiveChat() {
        playerSidePanel = findViewById(R.id.player_side_panel);
        liveChatPanel = findViewById(R.id.live_chat_panel);
        if (chatToggle != null) {
            chatToggle.setVisibility(View.GONE);
        }
        if (liveChatPanel == null) {
            return;
        }

        if (!isLivestream || video.getLiveStreamId() == null || video.getLiveStreamId().isEmpty()) {
            liveChatPanel.setVisibility(View.GONE);
            updateSidePanelVisibility();
            return;
        }

        liveChatStatus = findViewById(R.id.live_chat_status);
        RecyclerView chatList = findViewById(R.id.live_chat_list);
        liveChatAdapter = new LiveChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatList.setLayoutManager(layoutManager);
        chatList.setAdapter(liveChatAdapter);
        chatList.setItemAnimator(null);

        // Default hidden — many remotes (e.g. Google TV Streamer) lack Captions/Info keys.
        // Defer socket connect until the user opens chat so it doesn't contend with playback startup.
        liveChatVisible = false;
        liveChatPanel.setVisibility(View.GONE);
        updateSidePanelVisibility();
        if (chatToggle != null) {
            chatToggle.setVisibility(View.VISIBLE);
            chatToggle.setOnClickListener(v -> toggleLiveChat());
            updateChatToggleState();
        }
    }

    private void setupLivePoll() {
        if (!isLivestream || playerSidePanel == null) {
            return;
        }
        String creatorId = video.getCreator() != null ? video.getCreator().getId() : null;
        if (creatorId == null || creatorId.isEmpty()) {
            MainFragment.dLog("LIVEPOLL", "No creator id — polls unavailable");
            return;
        }
        livePollPanel = new LivePollPanelController(playerSidePanel, this::updateSidePanelVisibility);
        // Connect once playback UI is up; polls use www socket + OAuth token join.
        livePollPanel.connect(creatorId);
    }

    private void updateSidePanelVisibility() {
        if (playerSidePanel == null) {
            return;
        }
        boolean pollVisible = livePollPanel != null && livePollPanel.isVisible();
        boolean show = liveChatVisible || pollVisible;
        playerSidePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (liveChatPanel != null) {
            liveChatPanel.setVisibility(liveChatVisible ? View.VISIBLE : View.GONE);
            // When chat is hidden but a poll is showing, give the poll the remaining height.
            ViewGroup.LayoutParams lp = liveChatPanel.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams linearLp = (LinearLayout.LayoutParams) lp;
                if (liveChatVisible) {
                    linearLp.height = 0;
                    linearLp.weight = 1f;
                } else {
                    linearLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    linearLp.weight = 0f;
                }
                liveChatPanel.setLayoutParams(linearLp);
            }
        }
    }

    private void ensureLiveChatConnected() {
        if (liveChatClient != null || video.getLiveStreamId() == null || video.getLiveStreamId().isEmpty()) {
            return;
        }
        liveChatClient = LiveChatClient.getInstance(this);
        liveChatClient.connect(video.getLiveStreamId(), new LiveChatClient.Listener() {
            @Override
            public void onConnected() {
                if (liveChatStatus != null) {
                    liveChatStatus.setText(R.string.live_chat_connecting);
                }
            }

            @Override
            public void onJoined(@NonNull List<ChatEmote> emotes) {
                if (liveChatStatus != null) {
                    liveChatStatus.setText(R.string.live_chat_connected);
                }
                if (liveChatAdapter != null) {
                    liveChatAdapter.setEmotes(emotes);
                }
                MainFragment.dLog("LIVECHAT", "Joined with " + emotes.size() + " emotes");
            }

            @Override
            public void onMessage(@NonNull RadioChatter message) {
                if (liveChatAdapter == null) {
                    return;
                }
                liveChatAdapter.addMessage(message);
                RecyclerView list = findViewById(R.id.live_chat_list);
                if (list != null && liveChatAdapter.getItemCount() > 0) {
                    list.scrollToPosition(liveChatAdapter.getItemCount() - 1);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                if (liveChatStatus != null) {
                    liveChatStatus.setText(R.string.live_chat_disconnected);
                }
                MainFragment.dError("LIVECHAT", message);
                Toast.makeText(PlaybackActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected() {
                if (liveChatStatus != null) {
                    liveChatStatus.setText(R.string.live_chat_disconnected);
                }
            }
        });
    }

    private void toggleLiveChat() {
        if (liveChatPanel == null || !isLivestream) {
            return;
        }
        if (!liveChatVisible
                && !ml.bmlzootown.hydravion.chat.SailsSessionHelper.INSTANCE.hasChatCookie(this)) {
            Toast.makeText(this, R.string.chat_cookie_required, Toast.LENGTH_LONG).show();
            return;
        }
        liveChatVisible = !liveChatVisible;
        if (liveChatVisible) {
            ensureLiveChatConnected();
        }
        updateSidePanelVisibility();
        updateChatToggleState();
    }

    private void updateChatToggleState() {
        if (chatToggle == null) {
            return;
        }
        chatToggle.setSelected(liveChatVisible);
        chatToggle.setAlpha(liveChatVisible ? 1f : 0.55f);
        chatToggle.setContentDescription(getString(
                liveChatVisible ? R.string.live_chat_hide : R.string.live_chat_show));
    }

    private void releaseLiveChat() {
        if (liveChatClient != null) {
            liveChatClient.disconnect();
            liveChatClient = null;
        }
        if (liveChatAdapter != null) {
            liveChatAdapter.clear();
        }
        if (livePollPanel != null) {
            livePollPanel.disconnect();
            livePollPanel = null;
        }
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

    @Override
    protected void onDestroy() {
        releaseLiveChat();
        super.onDestroy();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && isLivestream) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_CAPTIONS
                    || keyCode == KeyEvent.KEYCODE_INFO
                    || keyCode == KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK) {
                toggleLiveChat();
                return true;
            }
        }
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (isControllerVisible) {
            playerView.hideController();
        } else if (liveChatVisible) {
            toggleLiveChat();
        } else {
            super.onBackPressed();
        }
    }

    private void setupLikeAndDislike() {
        if (isLivestream) {
            like.setVisibility(View.GONE);
            dislike.setVisibility(View.GONE);
            return;
        }

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

            // MediaTek devices (e.g. Google TV Streamer) freeze MPEG-TS live video in the HW
            // AVC decoder while audio continues; prefer the software decoder for live playback.
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);
            if (isLivestream && DevicePlaybackCompat.preferSoftwareVideoDecoderForLive()) {
                renderersFactory.setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE);
                MainFragment.dLog("PLAYBACK", "Software video decoder for live on "
                        + Build.MODEL + " (" + Build.HARDWARE + ")");
            }
            player = new ExoPlayer.Builder(this)
                    .setRenderersFactory(renderersFactory)
                    .build();

            // Set player on PlayerView BEFORE setting media source to ensure surface is ready
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

            if (isLivestream) {
                attachPlaybackAnalytics();
            }

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

    private void attachPlaybackAnalytics() {
        detachPlaybackAnalytics();
        playbackAnalytics = new AnalyticsListener() {
            @Override
            public void onVideoDecoderInitialized(
                    AnalyticsListener.EventTime eventTime,
                    String decoderName,
                    long initializationDurationMs) {
                MainFragment.dLog("PLAYBACK", "Video decoder: " + decoderName);
                if (decoderName != null && decoderName.toLowerCase().contains("mtk")) {
                    MainFragment.dError("PLAYBACK", "MediaTek decoder active during live — may freeze");
                }
            }
        };
        player.addAnalyticsListener(playbackAnalytics);
    }

    private void detachPlaybackAnalytics() {
        if (player != null && playbackAnalytics != null) {
            player.removeAnalyticsListener(playbackAnalytics);
        }
        playbackAnalytics = null;
    }

    private void saveVideoPosition() {
        // Livestreams have no videoId; don't post progress for them
        if (player != null && video != null && video.getVideoId() != null) {
            client.setVideoProgress(video.getVideoId(), (int) (player.getCurrentPosition() / 1000));
        }
    }

    private void releasePlayer() {
        detachPlaybackAnalytics();
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

        releaseLiveChat();
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

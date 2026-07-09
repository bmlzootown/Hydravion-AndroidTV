package ml.bmlzootown.hydravion.browse;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.VerticalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.VolleyError;
import androidx.media3.common.util.Util;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.g00fy2.versioncompare.Version;
import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import kotlin.Unit;
import ml.bmlzootown.hydravion.BuildConfig;
import ml.bmlzootown.hydravion.Constants;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.ThemeManager;
import ml.bmlzootown.hydravion.authenticate.AuthManager;
import ml.bmlzootown.hydravion.authenticate.LogoutRequestTask;
import ml.bmlzootown.hydravion.card.CardPresenter;
import ml.bmlzootown.hydravion.card.CardPlaceholder;
import ml.bmlzootown.hydravion.card.ThumbnailPrefetch;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.client.SocketClient;
import ml.bmlzootown.hydravion.client.SyncEvent;
import ml.bmlzootown.hydravion.client.UserSync;
import ml.bmlzootown.hydravion.creator.FloatplaneLiveStream;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.models.Channel;
import ml.bmlzootown.hydravion.models.ChildImage;
import ml.bmlzootown.hydravion.models.Creator;
import ml.bmlzootown.hydravion.models.Delivery;
import ml.bmlzootown.hydravion.models.Thumbnail;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.models.VideoInfo;
import ml.bmlzootown.hydravion.models.VideoProgress;
import ml.bmlzootown.hydravion.models.VideoTypeUtil;
import ml.bmlzootown.hydravion.playback.PlaybackActivity;
import ml.bmlzootown.hydravion.subscription.Subscription;

public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";
    public static boolean debug = true;

    private HydravionClient client;
    private final String version = BuildConfig.VERSION_NAME;

    private SocketClient socketClient;
    private Socket socket;
    private final Gson gson = new Gson();

    public static List<Subscription> subscriptions = new ArrayList<>();
    public static BrowseSupportFragment bsf;

    /**
     * One sidebar row of videos: either a creator's full feed (channelId == null)
     * or a single channel's feed. Keyed by the ListRow/HeaderItem id.
     */
    private static class RowInfo {
        final String creatorGUID;
        @Nullable
        String channelId;
        final ArrayObjectAdapter adapter;
        int fetched = 0;
        boolean loading = false;
        boolean exhausted = false;

        RowInfo(String creatorGUID, @Nullable String channelId, ArrayObjectAdapter adapter) {
            this.creatorGUID = creatorGUID;
            this.channelId = channelId;
            this.adapter = adapter;
        }
    }

    private static class ChannelRowData {
        final long rowId;
        final String channelId;
        final String title;
        @Nullable
        final String iconUrl;
        final ArrayObjectAdapter adapter;
        final ListRow listRow;

        ChannelRowData(long rowId, String channelId, String title, @Nullable String iconUrl,
                       ArrayObjectAdapter adapter, ListRow listRow) {
            this.rowId = rowId;
            this.channelId = channelId;
            this.title = title;
            this.iconUrl = iconUrl;
            this.adapter = adapter;
            this.listRow = listRow;
        }
    }

    /**
     * A subscription that has multiple channels. Collapsed by default so the
     * sidebar only shows the subscription name until the user expands it.
     */
    private static class SubscriptionGroup {
        final String creatorGUID;
        final String title;
        @Nullable
        final String iconUrl;
        long subscriptionRowId;
        SubscriptionHeaderItem headerItem;
        boolean expanded = false;
        final List<ChannelRowData> channelRows = new ArrayList<>();

        SubscriptionGroup(String creatorGUID, String title, @Nullable String iconUrl) {
            this.creatorGUID = creatorGUID;
            this.title = title;
            this.iconUrl = iconUrl;
        }
    }

    private final Map<Long, RowInfo> rowsById = new HashMap<>();
    private final Map<String, List<Channel>> channelsByCreator = new HashMap<>();
    private final List<SubscriptionGroup> subscriptionGroups = new ArrayList<>();
    private final Map<String, SubscriptionGroup> subscriptionGroupsByCreator = new HashMap<>();
    private final Set<Long> deferredChannelRowIds = new HashSet<>();
    // Shared with CardPresenter; contents are updated in place as progress loads
    private final List<VideoProgress> videoProgress = new ArrayList<>();
    private CardPresenter cardPresenter;

    private boolean backgroundManagerPrepared = false;
    private boolean uiInitialized = false;
    private boolean headerSelectionHooked = false;
    private boolean isLoggedIn = false;
    private int loginRetryCount = 0;
    private static final int MAX_LOGIN_RETRIES = 3;

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupCollapsibleHeaders();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        bsf = this;
        client = HydravionClient.getInstance(requireActivity());
        socketClient = SocketClient.getInstance(requireActivity());
        checkLogin();

        client.getLatest(v -> {
            if (new Version(version).isLowerThan(v.substring(1))) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Update Available");
                builder.setMessage("Version " + v + " now available via Github: \n\nhttps://github.com/bmlzootown/Hydravion-AndroidTV/releases");
                builder.setPositiveButton("OKAY", null);
                builder.create().show();
            }
            return Unit.INSTANCE;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() == null) {
            return;
        }
        // After long background / TV sleep, silently ensure the access token is still valid.
        // Do not force a full re-login on transient network failures — only when credentials
        // are actually gone (matches force-close-and-reopen behavior users already rely on).
        AuthManager authManager = AuthManager.getInstance(requireActivity());
        if (isLoggedIn) {
            authManager.withValidAccessToken(
                    accessToken -> Unit.INSTANCE,
                    () -> {
                        if (!authManager.hasRefreshToken()) {
                            dLog("LOGIN", "Credentials cleared while backgrounded; restarting login");
                            isLoggedIn = false;
                            checkLogin();
                        } else {
                            dLog("LOGIN", "Transient token refresh failure on resume; keeping session");
                        }
                        return Unit.INSTANCE;
                    });
        } else if (authManager.hasRefreshToken()) {
            // Startup may have failed transiently while tokens remain on disk (common after TV sleep).
            dLog("LOGIN", "Resuming with stored refresh token — recovering session");
            checkLogin();
        }
    }

    private void checkLogin() {
        AuthManager authManager = AuthManager.getInstance(requireActivity());
        authManager.withValidAccessToken(accessToken -> {
            dLog("LOGIN", "Access token valid (or refreshed successfully)");
            loginRetryCount = 0;
            isLoggedIn = true;
            initialize();
            return Unit.INSTANCE;
        }, () -> {
            // Only start QR login when we truly have no refresh token left.
            // Transient refresh failures must not kick the user into the login loop.
            if (authManager.hasRefreshToken()) {
                if (loginRetryCount < MAX_LOGIN_RETRIES) {
                    loginRetryCount++;
                    dLog("LOGIN", "Token refresh failed transiently; retry " + loginRetryCount + "/" + MAX_LOGIN_RETRIES);
                    new Handler(Looper.getMainLooper()).postDelayed(this::checkLogin, 2000L);
                    return Unit.INSTANCE;
                }
                dLog("LOGIN", "Token refresh failed after retries; keeping credentials, not forcing QR login");
                loginRetryCount = 0;
                // Stay on browse without wiping tokens — user can retry via Refresh / reopen.
                Toast.makeText(getContext(), "Could not refresh session. Check your network.", Toast.LENGTH_LONG).show();
                return Unit.INSTANCE;
            }
            dLog("LOGIN", "No valid access token or refresh token available. Starting login flow.");
            loginRetryCount = 0;
            isLoggedIn = false;
            Intent intent = new Intent(getActivity(), ml.bmlzootown.hydravion.authenticate.QrLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivityForResult(intent, 42);
            return Unit.INSTANCE;
        });
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && resultCode == RESULT_OK && data != null) {
            String accessToken = data.getStringExtra("access_token");
            String refreshToken = data.getStringExtra("refresh_token");
            long expiresIn = data.getLongExtra("expires_in", 3600L);

            if (accessToken != null && !accessToken.isEmpty()) {
                // Use empty string if refreshToken is null to avoid NullPointerException
                String safeRefreshToken = (refreshToken != null) ? refreshToken : "";
                // Route through AuthManager so in-memory cache / stuck refresh state stay in sync
                AuthManager.getInstance(requireActivity()).storeTokens(accessToken, safeRefreshToken, expiresIn);

                // Mark as logged in and initialize
                isLoggedIn = true;
                initialize();
            } else {
                dLog(TAG, "Login result missing access token; restarting login flow.");
                // Restart login flow to avoid running without credentials
                checkLogin();
            }
        } else if (requestCode == 42) {
            // QR login cancelled (e.g. user pressed back) — don't leave an unusable
            // empty browse screen with no way back into the login flow.
            if (getContext() != null) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Login Required")
                        .setMessage("You must link your Floatplane account to use Hydravion.")
                        .setPositiveButton("Log In",
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    checkLogin();
                                })
                        .setNegativeButton("Exit",
                                (dialog, which) -> requireActivity().finish())
                        .setCancelable(false)
                        .create()
                        .show();
            }
        } else if (requestCode == Constants.REQ_CODE_DETAIL && resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra("REFRESH", false)) {
                refreshVideoProgress();
            }
        }
    }

    private void initialize() {
        refreshSubscriptions();
        prepareBackgroundManager();

        // Only setup UI elements and listeners once, before views are created
        if (!uiInitialized) {
            setupUIElements();
            setupEventListeners();
            uiInitialized = true;
        }
        // TODO: Temporarily disabled - backend doesn't support auth tokens with websockets yet
        // Setup Socket
        // setupSocket();
    }

    private void setupSocket() {
        socketClient.initialize(sock -> {
            if (sock == null) {
                dLog("SOCKET", "Failed to initialize socket due to auth error");
                return Unit.INSTANCE;
            }

            socket = sock;
            socket.on("connect", onSocketConnect);
            socket.on("disconnect", onSocketDisconnect);
            socket.on("syncEvent", onSyncEvent);
            return Unit.INSTANCE;
        });
    }

    // Socket Event Emitters
    private final Emitter.Listener onSocketConnect = args -> {
        dLog("SOCKET", "Connected");
        JSONObject jo = new JSONObject();
        try {
            jo.put("url", "/api/v3/socket/connect");
            dLog("SOCKET --> EMIT", jo.toString());
            socket.emit("post", jo, new Ack() {
                @Override
                public void call(Object... args) {
                    UserSync us = socketClient.parseUserSync(args[0].toString());
                    dLog("SOCKET --> EMIT RESPONSE", String.valueOf(us));
                    if (us != null && us.getStatusCode() != null && us.getStatusCode() == 200) {
                        dLog("SOCKET", "Synced!");
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    };

    private final Emitter.Listener onSocketDisconnect = args -> {
        dLog("SOCKET", "Disconnected");
        // TODO: Temporarily disabled - backend doesn't support auth tokens with websockets yet
        // setupSocket();
    };

    private final Emitter.Listener onSyncEvent = args -> {
        JSONObject obj = (JSONObject) args[0];
        SyncEvent event = socketClient.parseSyncEvent(obj);
        String e = gson.toJson(event);
        dLog("SOCKET", e);
        if (event.getEvent().equalsIgnoreCase("creatorNotification")) {
            if (event.getData().getEventType().equalsIgnoreCase("CONTENT_POST_RELEASE")) {
                dLog("SOCKET", "CONTENT_POST_RELEASE");
                client.getVideoObject(event.getData().getVideo().getGuid(), video -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> addNewVideoToRows(video));
                    }
                    return Unit.INSTANCE;
                });
            }
        }
        dLog("SOCKET --> SYNCEVENT", event.toString());
    };

    private void logout() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE);
        String accessToken = prefs.getString(Constants.PREF_ACCESS_TOKEN, null);

        // Best-effort token revocation; ignore errors
        if (accessToken != null && !accessToken.isEmpty()) {
            LogoutRequestTask lrt = new LogoutRequestTask(getContext());
            lrt.logout(accessToken, new LogoutRequestTask.VolleyCallback() {
                @Override
                public void onSuccess(String response) {
                    dLog("LOGOUT", "Token revoked");
                }

                @Override
                public void onError(VolleyError error) {
                    dLog("LOGOUT", "Revocation failed: " + error.getMessage());
                }
            });
        }

        // Clear tokens and in-memory data
        // Use AuthManager to clear both SharedPreferences and in-memory cache
        AuthManager.getInstance(requireActivity()).clearTokens();

        // Clear all in-memory data structures
        subscriptions.clear();
        rowsById.clear();
        channelsByCreator.clear();
        subscriptionGroups.clear();
        subscriptionGroupsByCreator.clear();
        deferredChannelRowIds.clear();
        liveCheckedCreators.clear();
        channelsFetchCompleted = false;
        videoProgress.clear();

        // Disconnect socket if connected
        if (socket != null && socket.connected()) {
            socket.disconnect();
            socket.off();
            socket = null;
        }

        // Clear the adapter to prevent stale data from being displayed
        if (getAdapter() != null) {
            setAdapter(null);
        }

        // Reset UI initialization flag to allow proper setup on next login
        uiInitialized = false;
        headerSelectionHooked = false;

        // Mark as logged out to prevent callbacks from updating UI
        isLoggedIn = false;

        // Restart the QR login flow instead of closing the app
        checkLogin();
    }

    private void refreshSubscriptions() {
        client.getSubs(subs -> {
            if (subs == null) {
                // Network / API failure without confirmed auth loss — do not force logout.
                // A confirmed auth failure is handled via the onAuthFailure callback below.
                dLog(TAG, "getSubs returned null (non-auth failure); showing retry dialog");
                if (getContext() == null) {
                    return Unit.INSTANCE;
                }
                new AlertDialog.Builder(getContext())
                        .setTitle("Connection Problem")
                        .setMessage("Could not load subscriptions. Check your network and try again.")
                        .setPositiveButton("Retry",
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    refreshSubscriptions();
                                })
                        .setNegativeButton("Cancel",
                                (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                if (subs.length == 0) {
                    new AlertDialog.Builder(getContext())
                            .setTitle("No Subscriptions Found")
                            .setMessage("Must be subscribed to a creator to utilize this app -- see official Floatplane website.")
                            .setPositiveButton("OK",
                                    (dialog, which) -> {
                                        dialog.dismiss();
                                        logout();
                                    })
                            .create()
                            .show();
                }
                gotSubscriptions(subs);
            }

            return Unit.INSTANCE;
        }, () -> {
            // Confirmed auth failure (tokens cleared / still 401 after refresh)
            AuthManager authManager = AuthManager.getInstance(requireActivity());
            if (!authManager.hasRefreshToken()) {
                if (getContext() == null) {
                    return Unit.INSTANCE;
                }
                new AlertDialog.Builder(getContext())
                        .setTitle("Session Expired")
                        .setMessage("Your Floatplane session has expired. Please relink your account.")
                        .setPositiveButton("Relink",
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    logout();
                                })
                        .setNegativeButton("Cancel",
                                (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                dLog(TAG, "getSubs auth callback but refresh token still present; not forcing relink");
            }
            return Unit.INSTANCE;
        });
    }

    private void gotSubscriptions(Subscription[] subs) {
        // Guard against processing subscriptions if we're logged out
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring subscription update - user is logged out");
            return;
        }

        List<Subscription> trimmed = new ArrayList<>();
        for (Subscription sub : subs) {
            if (!containsSub(trimmed, sub)) {
                trimmed.add(sub);
            }
        }
        subscriptions = trimmed;
        dLog("SUBS", trimmed.size() + " unique subscriptions");

        if (trimmed.isEmpty()) {
            return;
        }

        // Paint the sidebar immediately; channel metadata refines when the batch returns.
        channelsFetchCompleted = false;
        buildRows();

        List<String> creatorIds = new ArrayList<>();
        for (Subscription sub : trimmed) {
            if (sub.getCreator() != null && !channelsByCreator.containsKey(sub.getCreator())) {
                creatorIds.add(sub.getCreator());
            }
        }
        if (creatorIds.isEmpty()) {
            channelsFetchCompleted = true;
            finishInitialRowLoad();
            return;
        }

        client.getChannelsForCreators(creatorIds, grouped -> {
            if (!isLoggedIn) {
                return Unit.INSTANCE;
            }
            for (String creatorId : creatorIds) {
                List<Channel> channels = grouped.get(creatorId);
                List<Channel> list = channels != null ? new ArrayList<>(channels) : new ArrayList<>();
                list.sort(Comparator.comparingInt(Channel::getOrder));
                channelsByCreator.put(creatorId, list);
                if (!list.isEmpty()) {
                    dLog(TAG, "Loaded " + list.size() + " channels for creator " + creatorId);
                    upgradeCreatorRow(creatorId, list);
                }
            }
            channelsFetchCompleted = true;
            finishInitialRowLoad();
            return Unit.INSTANCE;
        });
    }

    /**
     * Load the initially visible row after the browse fragments are attached.
     * Deferred until channel metadata is available so multi-channel creators don't
     * get marked exhausted on an empty creator-wide feed.
     */
    private void finishInitialRowLoad() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isLoggedIn || getActivity() == null) {
                return;
            }
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
            if (adapter == null || adapter.size() == 0) {
                return;
            }
            int selectedPosition = getSelectedPosition();
            if (selectedPosition < 0 || selectedPosition >= adapter.size()) {
                selectedPosition = 0;
            }
            loadRowAtBrowsePosition(selectedPosition);
        });
    }

    @Nullable
    private static String channelIconUrl(@Nullable List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        Channel first = channels.get(0);
        return (first.getIcon() != null) ? first.getIcon().getPath() : null;
    }

    @Nullable
    private static String defaultChannelId(@Nullable List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        return channels.get(0).getId();
    }

    private void reloadMainRowIfNeeded(String creatorGUID) {
        for (Map.Entry<Long, RowInfo> entry : rowsById.entrySet()) {
            RowInfo info = entry.getValue();
            if (!creatorGUID.equals(info.creatorGUID) || info.channelId != null) {
                continue;
            }
            if (info.exhausted || (info.fetched == 0 && !info.loading)) {
                info.exhausted = false;
                info.fetched = 0;
                ensurePlaceholders(info.adapter);
                loadMoreForRow(entry.getKey());
            }
            break;
        }
    }

    /**
     * When channel metadata arrives after the initial flat row was shown, upgrade to a
     * collapsible subscription group without rebuilding the whole browse adapter.
     */
    private void upgradeCreatorRow(String creatorGUID, List<Channel> channels) {
        if (!isLoggedIn || channels == null) {
            return;
        }

        SubscriptionGroup existing = subscriptionGroupsByCreator.get(creatorGUID);
        if (existing != null) {
            if (existing.channelRows.isEmpty() && !channels.isEmpty()) {
                appendChannelRows(existing, channels);
                if (existing.expanded) {
                    scheduleChannelLoads(existing);
                }
            }
            reloadMainRowIfNeeded(creatorGUID);
            return;
        }

        if (channels.isEmpty()) {
            return;
        }

        long flatRowId = -1;
        RowInfo mainInfo = null;
        for (Map.Entry<Long, RowInfo> entry : rowsById.entrySet()) {
            RowInfo info = entry.getValue();
            if (creatorGUID.equals(info.creatorGUID) && info.channelId == null) {
                flatRowId = entry.getKey();
                mainInfo = info;
                break;
            }
        }
        if (flatRowId < 0 || mainInfo == null) {
            return;
        }

        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
        if (adapter == null) {
            return;
        }
        int index = findRowIndex(flatRowId);
        if (index < 0) {
            return;
        }

        Object rowObj = adapter.get(index);
        if (!(rowObj instanceof ListRow)) {
            return;
        }
        ListRow currentRow = (ListRow) rowObj;
        if (currentRow.getHeaderItem() instanceof SubscriptionHeaderItem) {
            return;
        }

        String title = currentRow.getHeaderItem() != null
                ? currentRow.getHeaderItem().getName() : "Creator";

        SubscriptionGroup group = new SubscriptionGroup(creatorGUID, title, channelIconUrl(channels));
        SubscriptionHeaderItem header = new SubscriptionHeaderItem(
                flatRowId, title, creatorGUID, channelIconUrl(channels));
        group.subscriptionRowId = flatRowId;
        group.headerItem = header;
        subscriptionGroups.add(group);
        subscriptionGroupsByCreator.put(creatorGUID, group);

        appendChannelRows(group, channels);
        adapter.replace(index, new ListRow(header, mainInfo.adapter));
        adapter.notifyArrayItemRangeChanged(index, 1);
        reloadMainRowIfNeeded(creatorGUID);
    }

    private void appendChannelRows(SubscriptionGroup group, List<Channel> channels) {
        if (cardPresenter == null) {
            return;
        }
        long nextId = maxRowId() + 1;
        for (Channel channel : channels) {
            long channelRowId = nextId++;
            String iconUrl = (channel.getIcon() != null) ? channel.getIcon().getPath() : null;
            ArrayObjectAdapter channelAdapter = new ArrayObjectAdapter(cardPresenter);
            ensurePlaceholders(channelAdapter);
            rowsById.put(channelRowId, new RowInfo(group.creatorGUID, channel.getId(), channelAdapter));
            IconHeaderItem channelHeader = new IconHeaderItem(
                    channelRowId, channel.getTitle(), iconUrl, null, true);
            ListRow channelRow = new ListRow(channelHeader, channelAdapter);
            group.channelRows.add(new ChannelRowData(
                    channelRowId, channel.getId(), channel.getTitle(), iconUrl,
                    channelAdapter, channelRow));
            deferredChannelRowIds.add(channelRowId);
        }
    }

    private long maxRowId() {
        long max = 0;
        for (Long id : rowsById.keySet()) {
            if (id > max) {
                max = id;
            }
        }
        return max;
    }

    private void ensurePlaceholders(ArrayObjectAdapter adapter) {
        if (adapter.size() > 0) {
            return;
        }
        for (int i = 0; i < CardPlaceholder.COUNT; i++) {
            adapter.add(CardPlaceholder.INSTANCE);
        }
    }

    private void removePlaceholders(ArrayObjectAdapter adapter) {
        int count = 0;
        for (int i = 0; i < adapter.size(); i++) {
            if (adapter.get(i) == CardPlaceholder.INSTANCE) {
                count++;
            } else {
                break;
            }
        }
        if (count > 0) {
            adapter.removeItems(0, count);
        }
    }

    private boolean containsSub(List<Subscription> trimmed, Subscription sub) {
        for (Subscription s : trimmed) {
            if (s.getCreator() != null && s.getCreator().equals(sub.getCreator())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sidebar layout:
     * - subscriptions with channels are collapsible (collapsed by default)
     * - expanding shows "All Videos" + each channel underneath
     * - subscriptions without channels get a single row
     * - Settings row at the bottom
     */
    private void buildRows() {
        rowsById.clear();
        videoProgress.clear();
        subscriptionGroups.clear();
        subscriptionGroupsByCreator.clear();
        deferredChannelRowIds.clear();
        liveCheckedCreators.clear();
        channelsFetchCompleted = false;

        ListRowPresenter rowPresenter = new HydravionListRowPresenter();
        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(rowPresenter);
        cardPresenter = new CardPresenter(videoProgress);

        long id = 1;
        for (Subscription sub : subscriptions) {
            String creatorGUID = sub.getCreator();
            if (creatorGUID == null) {
                continue;
            }
            String title = (sub.getPlan() != null && sub.getPlan().getTitle() != null)
                    ? sub.getPlan().getTitle() : "Creator";

            List<Channel> creatorChannels = channelsByCreator.get(creatorGUID);
            if (creatorChannels != null && !creatorChannels.isEmpty()) {
                SubscriptionGroup group = new SubscriptionGroup(creatorGUID, title, null);

                long mainRowId = id++;
                ArrayObjectAdapter mainAdapter = new ArrayObjectAdapter(cardPresenter);
                rowsById.put(mainRowId, new RowInfo(creatorGUID, null, mainAdapter));
                SubscriptionHeaderItem header = new SubscriptionHeaderItem(
                        mainRowId, title, creatorGUID, channelIconUrl(creatorChannels));
                group.subscriptionRowId = mainRowId;
                group.headerItem = header;
                rowsAdapter.add(new ListRow(header, mainAdapter));
                subscriptionGroups.add(group);
                subscriptionGroupsByCreator.put(creatorGUID, group);

                for (Channel channel : creatorChannels) {
                    long channelRowId = id++;
                    String iconUrl = (channel.getIcon() != null) ? channel.getIcon().getPath() : null;
                    ArrayObjectAdapter channelAdapter = new ArrayObjectAdapter(cardPresenter);
                    ensurePlaceholders(channelAdapter);
                    rowsById.put(channelRowId, new RowInfo(creatorGUID, channel.getId(), channelAdapter));
                    IconHeaderItem channelHeader = new IconHeaderItem(
                            channelRowId, channel.getTitle(), iconUrl, null, true);
                    ListRow channelRow = new ListRow(channelHeader, channelAdapter);
                    group.channelRows.add(new ChannelRowData(
                            channelRowId, channel.getId(), channel.getTitle(), iconUrl,
                            channelAdapter, channelRow));
                    deferredChannelRowIds.add(channelRowId);
                }
            } else {
                addVideoRow(rowsAdapter, id++, creatorGUID, null, title, null);
            }
        }

        HeaderItem gridHeader = new HeaderItem(id, getString(R.string.settings));
        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.refresh));
        gridRowAdapter.add(getResources().getString(R.string.live_stream));
        gridRowAdapter.add(getResources().getString(R.string.format_settings));
        gridRowAdapter.add(getResources().getString(R.string.appearance_settings));
        gridRowAdapter.add(getResources().getString(R.string.app_info));
        gridRowAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        setAdapter(rowsAdapter);
        int selectedPosition = getSelectedPosition();
        if (selectedPosition < 0 || selectedPosition >= rowsAdapter.size()) {
            selectedPosition = 0;
        }
        setSelectedPosition(selectedPosition, false);

        // Skeleton cards for every row; content loads after channels + UI are ready.
        for (Long rowId : new ArrayList<>(rowsById.keySet())) {
            RowInfo info = rowsById.get(rowId);
            if (info != null) {
                ensurePlaceholders(info.adapter);
            }
        }
    }

    private void addVideoRow(ArrayObjectAdapter rowsAdapter, long id, String creatorGUID,
                             @Nullable String channelId, String name, @Nullable String iconUrl) {
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(cardPresenter);
        rowsById.put(id, new RowInfo(creatorGUID, channelId, adapter));
        rowsAdapter.add(new ListRow(new IconHeaderItem(id, name, iconUrl, creatorGUID, false), adapter));
    }

    private int findRowIndex(long headerId) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
        if (adapter == null) {
            return -1;
        }
        for (int i = 0; i < adapter.size(); i++) {
            Object item = adapter.get(i);
            if (item instanceof ListRow) {
                HeaderItem header = ((ListRow) item).getHeaderItem();
                if (header != null && header.getId() == headerId) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void collapseGroup(SubscriptionGroup group) {
        if (!group.expanded) {
            return;
        }
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
        if (adapter == null) {
            return;
        }
        int subIndex = findRowIndex(group.subscriptionRowId);
        if (subIndex < 0) {
            return;
        }

        // Remove channel rows inserted after the subscription header
        adapter.removeItems(subIndex + 1, group.channelRows.size());

        group.headerItem.setExpanded(false);
        group.expanded = false;
        adapter.notifyArrayItemRangeChanged(subIndex, 1);
    }

    private void expandGroup(SubscriptionGroup group) {
        if (group.expanded) {
            return;
        }
        for (SubscriptionGroup other : subscriptionGroups) {
            if (other.expanded) {
                collapseGroup(other);
            }
        }

        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
        if (adapter == null) {
            return;
        }
        int subIndex = findRowIndex(group.subscriptionRowId);
        if (subIndex < 0) {
            return;
        }

        List<ListRow> rowsToInsert = new ArrayList<>(group.channelRows.size());
        for (ChannelRowData channel : group.channelRows) {
            ensurePlaceholders(channel.adapter);
            rowsToInsert.add(channel.listRow);
        }
        adapter.addAll(subIndex + 1, rowsToInsert);

        group.headerItem.setExpanded(true);
        group.expanded = true;
        adapter.notifyArrayItemRangeChanged(subIndex, 1);
        scheduleChannelLoads(group);
    }

    /**
     * When a subscription expands, every channel row is visible in the vertical
     * content stack — load them all with a short stagger to avoid burst traffic.
     */
    private void scheduleChannelLoads(SubscriptionGroup group) {
        Handler handler = new Handler(Looper.getMainLooper());
        int delayMs = 0;
        for (ChannelRowData channel : group.channelRows) {
            final long rowId = channel.rowId;
            handler.postDelayed(() -> onRowDisplayed(rowId), delayMs);
            delayMs += 100;
        }
    }

    private void loadRowAtBrowsePosition(int position) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getAdapter();
        if (adapter == null || position < 0 || position >= adapter.size()) {
            return;
        }
        Object item = adapter.get(position);
        if (item instanceof ListRow) {
            HeaderItem header = ((ListRow) item).getHeaderItem();
            if (header != null) {
                onRowDisplayed(header.getId());
            }
        }
    }

    private Unit onRowDisplayed(long rowId) {
        RowInfo info = rowsById.get(rowId);
        if (info == null) {
            return Unit.INSTANCE;
        }
        if (!info.loading && !info.exhausted && info.fetched == 0) {
            dLog(TAG, "Loading content for row " + rowId
                    + (info.channelId != null ? " channel=" + info.channelId : ""));
            loadMoreForRow(rowId);
        }
        if (info.channelId == null) {
            maybeFetchLive(info.creatorGUID);
        }
        return Unit.INSTANCE;
    }

    private final Set<String> liveCheckedCreators = new HashSet<>();
    private boolean channelsFetchCompleted = false;

    private void maybeFetchLive(@Nullable String creatorGUID) {
        if (creatorGUID == null || liveCheckedCreators.contains(creatorGUID)) {
            return;
        }
        for (Subscription sub : subscriptions) {
            if (creatorGUID.equals(sub.getCreator())) {
                liveCheckedCreators.add(creatorGUID);
                client.getLive(sub, live -> {
                    gotLiveInfo(sub, live);
                    return Unit.INSTANCE;
                });
                break;
            }
        }
    }

    private void toggleSubscription(SubscriptionHeaderItem header) {
        SubscriptionGroup group = subscriptionGroupsByCreator.get(header.getCreatorGUID());
        if (group == null) {
            return;
        }
        if (group.expanded) {
            collapseGroup(group);
        } else {
            expandGroup(group);
        }
    }

    private Unit onHeaderClicked(RowHeaderPresenter.ViewHolder viewHolder, Row row) {
        if (row != null && row.getHeaderItem() instanceof SubscriptionHeaderItem) {
            toggleSubscription((SubscriptionHeaderItem) row.getHeaderItem());
            return Unit.INSTANCE;
        }
        // Default Leanback behaviour: hide the sidebar and focus the content row
        if (!isInHeadersTransition()) {
            startHeadersTransition(false);
            if (getRowsSupportFragment() != null && getRowsSupportFragment().getView() != null) {
                getRowsSupportFragment().getView().requestFocus();
            }
        }
        return Unit.INSTANCE;
    }

    /**
     * Leanback 1.0 does not expose a header-click setter on BrowseSupportFragment,
     * so we attach directly to the child HeadersSupportFragment once it exists.
     * Header *selection* (D-pad down through channels) is hooked separately so channel
     * rows load without entering the horizontal content strip.
     */
    private void setupCollapsibleHeaders() {
        attachHeaderSelectionListener();
    }

    private void attachHeaderSelectionListener() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isAdded()) {
                return;
            }
            for (Fragment fragment : getChildFragmentManager().getFragments()) {
                if (!(fragment instanceof HeadersSupportFragment)) {
                    continue;
                }
                HeadersSupportFragment headers = (HeadersSupportFragment) fragment;
                headers.setOnHeaderClickedListener(this::onHeaderClicked);

                if (headerSelectionHooked) {
                    return;
                }
                android.view.View headersView = headers.getView();
                if (headersView == null) {
                    retryAttachHeaderSelectionListener();
                    return;
                }
                VerticalGridView headerGrid = headersView.findViewById(androidx.leanback.R.id.browse_headers);
                if (headerGrid == null) {
                    retryAttachHeaderSelectionListener();
                    return;
                }
                headerGrid.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
                    @Override
                    public void onChildViewHolderSelected(RecyclerView parent,
                            RecyclerView.ViewHolder child, int position, int subposition) {
                        if (!(child instanceof ItemBridgeAdapter.ViewHolder)) {
                            return;
                        }
                        Object item = ((ItemBridgeAdapter.ViewHolder) child).getItem();
                        if (!(item instanceof ListRow)) {
                            return;
                        }
                        HeaderItem header = ((ListRow) item).getHeaderItem();
                        if (header == null) {
                            return;
                        }
                        final long rowId = header.getId();
                        new Handler(Looper.getMainLooper()).post(() -> onRowDisplayed(rowId));
                    }
                });
                headerSelectionHooked = true;
                return;
            }
            retryAttachHeaderSelectionListener();
        });
    }

    private void retryAttachHeaderSelectionListener() {
        if (!headerSelectionHooked && isAdded()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::attachHeaderSelectionListener, 100);
        }
    }

    private Unit onRowEndReached(Long rowId) {
        loadMoreForRow(rowId);
        return Unit.INSTANCE;
    }

    private void loadMoreForRow(long rowId) {
        RowInfo info = rowsById.get(rowId);
        if (info == null || info.loading || info.exhausted) {
            return;
        }
        info.loading = true;
        ensurePlaceholders(info.adapter);

        client.getVideos(info.creatorGUID, info.channelId, info.fetched, vids -> {
            info.loading = false;
            if (!isLoggedIn) {
                return Unit.INSTANCE;
            }
            if (vids == null) {
                removePlaceholders(info.adapter);
                dLog(TAG, "Video load failed for row " + rowId + "; will retry on demand");
                return Unit.INSTANCE;
            }
            if (vids.length == 0) {
                List<Channel> channels = channelsByCreator.get(info.creatorGUID);
                if (info.channelId == null && channels == null && !channelsFetchCompleted) {
                    info.loading = false;
                    ensurePlaceholders(info.adapter);
                    dLog(TAG, "Deferring video load for row " + rowId + " until channels arrive");
                    return Unit.INSTANCE;
                }
                if (info.channelId == null && channels != null && !channels.isEmpty()) {
                    info.channelId = defaultChannelId(channels);
                    info.loading = false;
                    info.exhausted = false;
                    ensurePlaceholders(info.adapter);
                    dLog(TAG, "Retrying row " + rowId + " with default channel " + info.channelId);
                    loadMoreForRow(rowId);
                    return Unit.INSTANCE;
                }
                removePlaceholders(info.adapter);
                info.exhausted = true;
                return Unit.INSTANCE;
            }

            removePlaceholders(info.adapter);
            info.fetched += vids.length;
            List<String> ids = new ArrayList<>();
            for (Video video : vids) {
                info.adapter.add(video);
                if (isProgressEligible(video)) {
                    ids.add(video.getId());
                }
            }
            if (getContext() != null) {
                ThumbnailPrefetch.prefetch(getContext(), vids);
            }
            info.adapter.notifyArrayItemRangeChanged(0, info.adapter.size());
            fetchProgressFor(ids, info.adapter);
            return Unit.INSTANCE;
        });
    }

    private boolean isProgressEligible(Video video) {
        return video != null
                && !"live".equalsIgnoreCase(video.getType())
                && video.getId() != null
                && !video.getId().isEmpty();
    }

    private List<String> collectProgressIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (RowInfo info : rowsById.values()) {
            for (int i = 0; i < info.adapter.size(); i++) {
                Object item = info.adapter.get(i);
                if (item instanceof Video && isProgressEligible((Video) item)) {
                    ids.add(((Video) item).getId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private void fetchProgressFor(List<String> blogPostIds, ArrayObjectAdapter adapter) {
        if (blogPostIds.isEmpty()) {
            return;
        }
        client.getVideoProgress(blogPostIds, progress -> {
            if (!isLoggedIn) {
                return Unit.INSTANCE;
            }
            videoProgress.addAll(progress);
            // Rebind so already-visible cards pick up their progress bars
            adapter.notifyArrayItemRangeChanged(0, adapter.size());
            return Unit.INSTANCE;
        });
    }

    private void refreshVideoProgress() {
        if (!isLoggedIn) {
            return;
        }
        List<String> ids = collectProgressIds();
        if (ids.isEmpty()) {
            return;
        }
        client.getVideoProgress(ids, progress -> {
            if (!isLoggedIn) {
                return Unit.INSTANCE;
            }
            videoProgress.clear();
            videoProgress.addAll(progress);
            for (RowInfo info : rowsById.values()) {
                info.adapter.notifyArrayItemRangeChanged(0, info.adapter.size());
            }
            return Unit.INSTANCE;
        });
    }

    @Nullable
    private RowInfo findCreatorRow(@Nullable String creatorGUID) {
        if (creatorGUID == null) {
            return null;
        }
        for (RowInfo info : rowsById.values()) {
            if (info.channelId == null && creatorGUID.equals(info.creatorGUID)) {
                return info;
            }
        }
        return null;
    }

    private void gotLiveInfo(Subscription sub, Delivery live) {
        // Guard against processing live info if we're logged out
        if (!isLoggedIn) {
            dLog(TAG, "Ignoring live info update - user is logged out");
            return;
        }

        if (live.getGroups() == null || live.getGroups().isEmpty()
                || live.getGroups().get(0).getOrigins() == null || live.getGroups().get(0).getOrigins().isEmpty()
                || live.getGroups().get(0).getVariants() == null || live.getGroups().get(0).getVariants().isEmpty()) {
            return;
        }

        String l = live.getGroups().get(0).getOrigins().get(0).getUrl() + live.getGroups().get(0).getVariants().get(0).getUrl();
        sub.setStreamUrl(l);
        client.checkLive(l, (status) -> {
            // Double-check we're still logged in when callback executes
            if (!isLoggedIn) {
                dLog(TAG, "Ignoring live status callback - user logged out during request");
                return Unit.INSTANCE;
            }
            sub.setStreaming(status == 200);
            dLog("LIVE STATUS", String.valueOf(status));
            if (status == 200) {
                addLiveCard(sub);
            }
            return Unit.INSTANCE;
        });
        dLog("LIVE", l);
    }

    /**
     * Prepend a LIVE card to the creator's first (all videos) row.
     */
    private void addLiveCard(Subscription sub) {
        RowInfo info = findCreatorRow(sub.getCreator());
        FloatplaneLiveStream liveInfo = sub.getStreamInfo();
        if (info == null || liveInfo == null) {
            return;
        }

        // Don't add the live card twice
        if (info.adapter.size() > 0) {
            Object first = info.adapter.get(0);
            if (first instanceof Video && "live".equalsIgnoreCase(((Video) first).getType())) {
                return;
            }
        }

        Video stream = new Video();
        stream.setType("live");

        Creator creator = new Creator();
        creator.setId((sub.getCreator() == null) ? "" : sub.getCreator());
        stream.setCreator(creator);
        stream.setDescription(liveInfo.getDescription());
        stream.setTitle("LIVE: " + liveInfo.getTitle());
        stream.setVidUrl(sub.getStreamUrl());
        if (liveInfo.getThumbnail() != null) {
            Thumbnail thumbnail = new Thumbnail();
            ChildImage ci = new ChildImage();
            ci.setPath(liveInfo.getThumbnail().getPath());
            ci.setWidth(liveInfo.getThumbnail().getWidth());
            ci.setHeight(liveInfo.getThumbnail().getHeight());
            List<ChildImage> cis = new ArrayList<>();
            cis.add(ci);
            thumbnail.setChildImages(cis);
            thumbnail.setPath(liveInfo.getThumbnail().getPath());
            thumbnail.setHeight(liveInfo.getThumbnail().getHeight());
            thumbnail.setWidth(liveInfo.getThumbnail().getWidth());
            stream.setThumbnail(thumbnail);
        }

        info.adapter.add(0, stream);
    }

    /**
     * Socket push of a new post: prepend to the creator's "All Videos" row if not present.
     */
    private void addNewVideoToRows(Video video) {
        if (!isLoggedIn || video.getCreator() == null) {
            return;
        }
        RowInfo info = findCreatorRow(video.getCreator().getId());
        if (info == null) {
            return;
        }
        for (int i = 0; i < info.adapter.size(); i++) {
            Object item = info.adapter.get(i);
            if (item instanceof Video && ((Video) item).getGuid().equalsIgnoreCase(video.getGuid())) {
                return;
            }
        }
        // Insert after a live card if one is pinned at the top
        int insertAt = 0;
        if (info.adapter.size() > 0) {
            Object first = info.adapter.get(0);
            if (first instanceof Video && "live".equalsIgnoreCase(((Video) first).getType())) {
                insertAt = 1;
            }
        }
        info.adapter.add(insertAt, video);
    }

    private void prepareBackgroundManager() {
        // BackgroundManager is a singleton per activity, so we need to avoid attaching multiple times
        if (backgroundManagerPrepared) {
            dLog(TAG, "BackgroundManager already prepared, skipping");
            return;
        }

        try {
            BackgroundManager mBackgroundManager = BackgroundManager.getInstance(requireActivity());
            mBackgroundManager.attach(requireActivity().getWindow());
            backgroundManagerPrepared = true;

            DisplayMetrics mMetrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        } catch (IllegalStateException e) {
            // BackgroundManager is already attached, which is fine
            dLog(TAG, "BackgroundManager already attached: " + e.getMessage());
            backgroundManagerPrepared = true;
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupUIElements() {
        setBadgeDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.white_plane));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setHeaderPresenterSelector(new PresenterSelector() {
            private final FilteredHeaderPresenter presenter = new FilteredHeaderPresenter();

            @Override
            public Presenter getPresenter(Object item) {
                return presenter;
            }
        });

        setBrandColor(ContextCompat.getColor(requireContext(), R.color.fastlane_background));
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new BrowseViewClickListener(requireContext(), this::onVideoSelected, this::onSettingsSelected));
        setOnItemViewSelectedListener(new ItemViewSelectedListener(this::onRowEndReached, this::onRowDisplayed));
    }

    private Unit onVideoSelected(@Nullable Presenter.ViewHolder itemViewHolder, @NonNull Video video) {
        if (itemViewHolder != null) {
            // Get intent to switch to DetailActivity ready
            Intent intent = new Intent(getActivity(), DetailsActivity.class);

            // Setup transition animation to detail screen
            Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            requireActivity(),
                            itemViewHolder.view.findViewById(R.id.image),
                            DetailsActivity.SHARED_ELEMENT_NAME)
                    .toBundle();

            if (video.getType().equalsIgnoreCase("live") || VideoTypeUtil.isTextPost(video)) {
                intent.putExtra(DetailsActivity.Video, video);
                if (VideoTypeUtil.isTextPost(video)) {
                    startActivityForResult(intent, Constants.REQ_CODE_DETAIL, bundle);
                } else {
                    requireActivity().startActivity(intent, bundle);
                }
            } else {
                client.getVideoInfo(video.getVideoId(), videoInfo -> {
                    String res = getHighestSupportedRes(videoInfo);
                    client.getVideo(video, res, newVideo -> {
                        newVideo.setVideoInfo(videoInfo);
                        intent.putExtra(DetailsActivity.Video, newVideo);
                        startActivityForResult(intent, Constants.REQ_CODE_DETAIL, bundle);
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
            }
        }

        return Unit.INSTANCE;
    }

    private Unit onSettingsSelected(@NonNull SettingsAction action) {
        switch (action) {
            case REFRESH:
                refreshSubscriptions(); // Re-fetches subs + channels, rebuilds all rows
                break;
            case LOGOUT:
                logout();
                break;
            case APP_INFO:
                showInfo();
                break;
            case LIVESTREAM:
                selectLivestream();
                break;
            case FORMAT_SETTINGS:
                showFormatSettings();
                break;
            case APPEARANCE:
                showAppearanceSettings();
                break;
        }
        return Unit.INSTANCE;
    }

    private void showInfo() {
        new AlertDialog.Builder(getContext())
                .setTitle("Hydravion (AndroidTV)")
                .setMessage("Version: " + version + "\n\n" +
                        "Contributors:\n" +
                        "- bmlzootown\n" +
                        "- NickM-27\n" +
                        "- Jman012\n")
                .create()
                .show();
    }

    private void showAppearanceSettings() {
        String[] options = {
                getString(R.string.theme_dark),
                getString(R.string.theme_light)
        };
        int selected = ThemeManager.isLight(requireContext()) ? 1 : 0;

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.appearance_settings_title))
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    String theme = which == 1 ? ThemeManager.THEME_LIGHT : ThemeManager.THEME_DARK;
                    boolean currentlyLight = ThemeManager.isLight(requireContext());
                    if ((which == 1) != currentlyLight) {
                        ThemeManager.setThemePreference(
                                (androidx.appcompat.app.AppCompatActivity) requireActivity(),
                                theme
                        );
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFormatSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE);
        String currentFormat = prefs.getString(Constants.PREF_OUTPUT_FORMAT, Constants.OUTPUT_FORMAT_DEFAULT);

        // Create format options array
        String[] formatOptions = {
            getString(R.string.format_hls_mpegts),
            getString(R.string.format_hls_fmp4),
            getString(R.string.format_dash_mpegts),
            getString(R.string.format_dash_m4s),
            getString(R.string.format_flat)
        };

        // Determine which option is currently selected
        int selectedIndex = 0;
        if (Constants.OUTPUT_FORMAT_HLS_FMP4.equals(currentFormat)) {
            selectedIndex = 1;
        } else if (Constants.OUTPUT_FORMAT_DASH_MPEGTS.equals(currentFormat)) {
            selectedIndex = 2;
        } else if (Constants.OUTPUT_FORMAT_DASH_M4S.equals(currentFormat)) {
            selectedIndex = 3;
        } else if (Constants.OUTPUT_FORMAT_FLAT.equals(currentFormat)) {
            selectedIndex = 4;
        }

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.format_settings_title))
                .setSingleChoiceItems(formatOptions, selectedIndex, (dialog, which) -> {
                    String newFormat;
                    String formatName;
                    switch (which) {
                        case 0:
                            newFormat = Constants.OUTPUT_FORMAT_HLS_MPEGTS;
                            formatName = getString(R.string.format_hls_mpegts);
                            break;
                        case 1:
                            newFormat = Constants.OUTPUT_FORMAT_HLS_FMP4;
                            formatName = getString(R.string.format_hls_fmp4);
                            break;
                        case 2:
                            newFormat = Constants.OUTPUT_FORMAT_DASH_MPEGTS;
                            formatName = getString(R.string.format_dash_mpegts);
                            break;
                        case 3:
                            newFormat = Constants.OUTPUT_FORMAT_DASH_M4S;
                            formatName = getString(R.string.format_dash_m4s);
                            break;
                        case 4:
                            newFormat = Constants.OUTPUT_FORMAT_FLAT;
                            formatName = getString(R.string.format_flat);
                            break;
                        default:
                            newFormat = Constants.OUTPUT_FORMAT_DEFAULT;
                            formatName = getString(R.string.format_hls_mpegts);
                    }

                    // Save preference
                    prefs.edit().putString(Constants.PREF_OUTPUT_FORMAT, newFormat).apply();

                    // Show confirmation
                    String message = getString(R.string.format_changed, formatName);
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

                    dLog("SETTINGS", "Format changed to: " + newFormat);

                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void selectLivestream() {
        List<String> subs = new ArrayList<>();
        for (Subscription s : subscriptions) {
            if (s.getPlan() != null) {
                subs.add(s.getPlan().getTitle());
            }
        }
        CharSequence[] s = subs.toArray(new CharSequence[0]);
        new AlertDialog.Builder(getContext())
                .setTitle("Play livestream?")
                .setItems(s, (dialog, which) -> {
                    String stream = subscriptions.get(which).getStreamUrl();
                    if (stream != null) {
                        dLog("LIVE", stream);
                        Video live = new Video();
                        live.setVidUrl(stream);
                        Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                        intent.putExtra(DetailsActivity.Video, live);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getActivity(), "Subscription does not include access to livestream.", Toast.LENGTH_LONG).show();
                    }
                })
                .create()
                .show();
    }

    private String getHighestSupportedRes(VideoInfo info) {
        int y = Util.getCurrentDisplayModeSize(requireContext()).y;
        AtomicBoolean found = new AtomicBoolean(false);
        String res = "";
        info.getLevels().forEach(level -> {
            if (level.getName().equalsIgnoreCase(Integer.toString(y))) {
                found.set(true);
            }
        });
        if (found.get()) {
            res = Integer.toString(y);
        } else {
            res = "1080";
        }

        dLog("Supported Resolution", res);
        return res;
    }

    public static void dLog(String tag, String msg) {
        if (debug) {
            Log.d(tag, msg);
        }
    }

    public static void dError(String tag, String msg) {
        if (debug) {
            Log.e(tag, msg);
        }
    }
}

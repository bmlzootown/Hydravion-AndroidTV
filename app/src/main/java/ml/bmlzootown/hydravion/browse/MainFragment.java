package ml.bmlzootown.hydravion.browse;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.android.volley.VolleyError;
import com.google.android.exoplayer2.util.Util;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import kotlin.Unit;
import ml.bmlzootown.hydravion.CardPresenter;
import ml.bmlzootown.hydravion.Constants;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.authenticate.LogoutRequestTask;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.client.SocketClient;
import ml.bmlzootown.hydravion.client.SyncEvent;
import ml.bmlzootown.hydravion.client.UserSync;
import ml.bmlzootown.hydravion.creator.FloatplaneLiveStream;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.authenticate.LoginActivity;
import ml.bmlzootown.hydravion.models.ChildImage;
import ml.bmlzootown.hydravion.models.Live;
import ml.bmlzootown.hydravion.models.Thumbnail;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.models.VideoInfo;
import ml.bmlzootown.hydravion.playback.PlaybackActivity;
import ml.bmlzootown.hydravion.subscription.Subscription;
import ml.bmlzootown.hydravion.subscription.SubscriptionHeaderPresenter;

public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";
    public static boolean debug = true;

    private HydravionClient client;

    private SocketClient socketClient;
    private Socket socket;
    private final Gson gson = new Gson();

    public static String sailssid;
    public static String cdn;

    public static List<Subscription> subscriptions = new ArrayList<>();
    private static List<Video> streams = new ArrayList<>();
    public static HashMap<String, ArrayList<Video>> videos = new HashMap<>();
    private int subCount;
    private int page = 1;

    private int rowSelected;
    private int colSelected;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        //Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        client = HydravionClient.Companion.getInstance(requireActivity(), requireActivity().getPreferences(Context.MODE_PRIVATE));
        socketClient = SocketClient.Companion.getInstance(requireActivity(), requireActivity().getPreferences(Context.MODE_PRIVATE));
        checkLogin();
    }

    private void checkLogin() {
        boolean gotCookies = loadCredentials();
        if (!gotCookies) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivityForResult(intent, 42);
            cdn = "edge03-na.floatplane.com";
        } else {
            initialize();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && resultCode == 1 && data != null) {
            ArrayList<String> cookies = data.getStringArrayListExtra("cookies");
            for (String cookie : cookies) {
                String[] c = cookie.split("=");
                if (c[0].equalsIgnoreCase("sails.sid")) {
                    sailssid = c[1];
                }
            }
            dLog("MainFragment", sailssid);

            saveCredentials();
            initialize();
        }
    }

    private void initialize() {
        refreshSubscriptions();
        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();

        // Setup Socket
        socket = socketClient.initialize();
        socket.on("connect", onSocketConnect);
        socket.on("disconnect", onSocketDisconnect);
        socket.on("syncEvent", onSyncEvent);
    }

    // Socket Event Emitters
    private final Emitter.Listener onSocketConnect = args -> {
        dLog("SOCKET", "Connected");
        JSONObject jo = new JSONObject();
        try {
            jo.put("url", "/api/sync/connect");
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
    };

    private final Emitter.Listener onSyncEvent = args -> {
        JSONObject obj = (JSONObject)args[0];
        SyncEvent event = socketClient.parseSyncEvent(obj);
        String e = gson.toJson(event);
        dLog("SOCKET", e);
        if (event.getEvent().equalsIgnoreCase("postRelease")) {
            dLog("SOCKET", "postRelease");
            client.getVideoObject(event.getData().getVideo().getGuid(), video -> {
                int row = getRow(video, subscriptions);
                if (row != -1) {
                    addToRow(video, subscriptions);
                }
                return Unit.INSTANCE;
            });
        } else if (event.getEvent().equalsIgnoreCase("creatorNotification")) {
            dLog("SOCKET", "creatorNotification");
            if (event.getData().getEventType().equalsIgnoreCase("CONTENT_LIVESTREAM_START")) {
                dLog("SOCKET", "CONTENT_LIVESTREAM_START");
                Integer row = getRow(event.getData().getCreator(), subscriptions);
                Thumbnail th = new Thumbnail();
                th.setPath(event.getData().getIcon());
                streams.get(row).setThumbnail(th);

                if (row != -1) {
                    addToRow(streams.get(row), subscriptions);
                }
            }
        }
        dLog("SOCKET --> SYNCEVENT", event.toString());
    };

    private boolean loadCredentials() {
        SharedPreferences prefs = requireActivity().getPreferences(Context.MODE_PRIVATE);
        sailssid = prefs.getString(Constants.PREF_SAIL_SSID, "default");
        cdn = prefs.getString(Constants.PREF_CDN, "default");
        dLog("SAILS.SID", sailssid);
        dLog("CDN", cdn);

        if (sailssid.equals("default") || cdn.equals("default")) {
            dLog("LOGIN", "Credentials not found!");
            return false;
        } else {
            dLog("LOGIN", "Credentials found!");
            return true;
        }
    }

    private void logout() {
        // Invalidate cookies via API
        LogoutRequestTask lrt = new LogoutRequestTask(getContext());
        String cookies = "sails.sid=" + sailssid + ";";
        lrt.logout(cookies, new LogoutRequestTask.VolleyCallback() {
            @Override
            public void onSuccess(String response) {
                dLog("LOGOUT", "Success!");
            }

            @Override
            public void onError(VolleyError error) {
                dLog("LOGOUT --> ERROR", error.getMessage());
            }
        });

        // Removed cookies, save dummy cookies, and close client
        sailssid = "default";
        saveCredentials();
        requireActivity().finishAndRemoveTask();
    }

    private void saveCredentials() {
        requireActivity().getPreferences(Context.MODE_PRIVATE).edit()
                .putString(Constants.PREF_SAIL_SSID, sailssid)
                .putString(Constants.PREF_CDN, cdn)
                .apply();
    }

    private void gotLiveInfo(Subscription sub, Live live) {
        String l = live.getCdn() + live.getResource().getUri();
        String pattern = "\\{(.*?)\\}";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(live.getResource().getUri());
        if (m.find()) {
            for (int i = 0; i < m.groupCount(); i++) {
                //dLog("LIVE", m.group(i));
                String var = m.group(i).substring(1, m.group(i).length() - 1);

                if (var.equalsIgnoreCase("token")) {
                    l = l.replaceAll("\\{token\\}", live.getResource().getData().getToken());
                    sub.setStreamUrl(l);
                    client.checkLive(l, (status) -> {
                        sub.setStreaming(status == 200);
                        dLog("LIVE STATUS", String.valueOf(status));
                        return Unit.INSTANCE;
                    });
                    dLog("LIVE", l);
                }
                //dLog("LIVE", l);
            }
        }
    }

    private void refreshSubscriptions() {
        client.getSubs(subscriptions -> {
            if (subscriptions == null) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Session Expired")
                        .setMessage("Re-open Hydravion to login again!")
                        .setPositiveButton("OK",
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    logout();
                                })
                        .create()
                        .show();
            } else {
                gotSubscriptions(subscriptions);
            }

            return Unit.INSTANCE;
        });
    }

    private void gotSubscriptions(Subscription[] subs) {
        List<Subscription> trimmed = new ArrayList<>();
        for (Subscription sub : subs) {
            if (trimmed.size() > 0) {
                if (!containsSub(trimmed, sub)) {
                    trimmed.add(sub);
                }
            } else {
                trimmed.add(sub);
            }
        }
        subscriptions = trimmed;
        for (Subscription sub : subscriptions) {
            if (sub.getCreator() != null) {
                client.getLive(sub.getCreator(), live -> {
                    gotLiveInfo(sub, live);
                    return Unit.INSTANCE;
                });
                client.getVideos(sub.getCreator(), 1, videos -> {
                    gotVideos(sub.getCreator(), videos);
                    return Unit.INSTANCE;
                });
            }
        }
        subCount = trimmed.size();
        dLog("ROWS", trimmed.size() + "");
    }

    private boolean containsSub(List<Subscription> trimmed, Subscription sub) {
        for (Subscription s : trimmed) {
            if (s.getCreator() != null && s.getCreator().equals(sub.getCreator())) {
                return true;
            }
        }

        return false;
    }

    private void gotVideos(String creatorGUID, Video[] vids) {
        if (videos.get(creatorGUID) != null && videos.get(creatorGUID).size() > 0) {
            videos.get(creatorGUID).addAll(Arrays.asList(vids));
        } else {
            videos.put(creatorGUID, new ArrayList<>(Arrays.asList(vids)));
        }


        if (subCount > 1) {
            subCount--;
        } else {
            refreshRows();
            subCount = subscriptions.size();
            setSelectedPosition(rowSelected, false, new ListRowPresenter.SelectItemViewHolderTask(colSelected));
        }
    }

    private void refreshRows() {
        List<Subscription> subs = subscriptions;
        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter();

        int i;
        for (i = 0; i < subs.size(); i++) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);

            Subscription sub = subscriptions.get(i);
            List<Video> vids = videos.get(sub.getCreator());

            boolean isStreaming = sub.getStreaming() != null ? sub.getStreaming() : false;

            // Stream node
            int row = getRow(sub.getCreator(), subscriptions);
            Video stream = new Video();
            stream.setType("live");
            FloatplaneLiveStream live = sub.getStreamInfo();
            stream.setCreator(Objects.requireNonNull(sub.getCreator()));
            stream.setDescription(Objects.requireNonNull(live).getDescription());
            stream.setTitle("LIVE: " + live.getTitle());
            stream.setVidUrl(Objects.requireNonNull(sub.getStreamUrl()));
            Thumbnail thumbnail = new Thumbnail();
            ChildImage ci = new ChildImage();
            ci.setPath(live.getThumbnail().getPath());
            ci.setWidth(live.getThumbnail().getWidth());
            ci.setHeight(live.getThumbnail().getHeight());
            List<ChildImage> cis = new ArrayList<>();
            thumbnail.setChildImages(cis);
            thumbnail.setPath(live.getThumbnail().getPath());
            thumbnail.setHeight(live.getThumbnail().getHeight());
            thumbnail.setWidth(live.getThumbnail().getWidth());
            stream.setThumbnail(thumbnail);
            streams.add(row, stream);

            // If streaming, append stream node to beginning of video list, else setup live check
            if (isStreaming) {
                dLog("STREAMING", "true");
                if (vids != null) {
                    vids.add(0, stream);
                }
            }

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
        gridRowAdapter.add(getResources().getString(R.string.select_server));
        gridRowAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        setAdapter(rowsAdapter);
    }

    private void addToRow(Video video, List<Subscription> subs) {
        dLog("addToRow", video.getGuid());
        for (int i = 0; i < subs.size(); i++) {
            String creator = subs.get(i).getCreator();
            String vid = video.getCreator();
            assert creator != null;
            if (creator.equalsIgnoreCase(vid)) {
                dLog("addToRow", "Adding video to row " + i + ", creator " + creator);
                ArrayObjectAdapter rows = (ArrayObjectAdapter) getAdapter();
                ListRow lr = (ListRow) rows.get(i);
                ArrayObjectAdapter vids = (ArrayObjectAdapter) lr.getAdapter();
                boolean addVid = true;
                for (int z = 0; z < vids.size(); z++) {
                    Video v = (Video) vids.get(z);
                    if (v.getGuid().equalsIgnoreCase(video.getGuid())) {
                        dLog("addToRow", "Video already found. Not adding.");
                        addVid = false;
                    }
                }
                if (addVid) {
                    dLog("addToRow", "Adding video " + video.getGuid() + " to row " + i);
                    vids.add(0, video);
                    vids.notifyArrayItemRangeChanged(0, vids.size());
                }
            }
        }
    }

    private int getRow(Video video, List<Subscription> subs) {
        int row = -1;
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).getCreator().equalsIgnoreCase(video.getCreator())) {
                row = i;
            }
        }
        return row;
    }

    private int getRow(String creatorGUID, List<Subscription> subs) {
        int row = -1;
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).getCreator().equalsIgnoreCase(creatorGUID)) {
                row = i;
            }
        }
        return row;
    }

    private void prepareBackgroundManager() {
        BackgroundManager mBackgroundManager = BackgroundManager.getInstance(requireActivity());
        mBackgroundManager.attach(requireActivity().getWindow());

        DisplayMetrics mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupUIElements() {
        setBadgeDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.white_plane));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object item) {
                return new SubscriptionHeaderPresenter();
            }
        });

        setBrandColor(ContextCompat.getColor(requireContext(), R.color.fastlane_background));
    }

    private void setupEventListeners() {
        /*setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Implement your own in-app search", Toast.LENGTH_LONG)
                        .show();
            }
        });*/

        setOnItemViewClickedListener(new BrowseViewClickListener(requireContext(), this::onVideoSelected, this::onSettingsSelected));
        setOnItemViewSelectedListener(new ItemViewSelectedListener(this::onCheckIndices, this::onRowSelected));
    }

    private Unit onCheckIndices(@NonNull String creator, int selected) {
        colSelected = selected;

        subscriptions.forEach(sub -> {
            if (creator.equals(sub.getCreator())) {
                rowSelected = subscriptions.indexOf(sub);
            }
        });
        return Unit.INSTANCE;
    }

    private Unit onRowSelected() {
        subscriptions.forEach(sub -> {
            client.getVideos(sub.getCreator(), page + 1, videos -> {
                gotVideos(sub.getCreator(), videos);
                return Unit.INSTANCE;
            });
            page++;
        });
        return Unit.INSTANCE;
    }

    private Unit onVideoSelected(@Nullable Presenter.ViewHolder itemViewHolder, @NonNull Video video) {
        if (itemViewHolder != null) {
            if (video.getType().equalsIgnoreCase("live")) {
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra(DetailsActivity.Video, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        DetailsActivity.SHARED_ELEMENT_NAME)
                        .toBundle();
                requireActivity().startActivity(intent, bundle);
            } else {
                client.getVideoInfo(video.getGuid(), videoInfo -> {
                    String res = getHighestSupportedRes(videoInfo);
                    client.getVideo(video, res, newVideo -> {
                        newVideo.setVideoInfo(videoInfo);
                        Intent intent = new Intent(getActivity(), DetailsActivity.class);
                        intent.putExtra(DetailsActivity.Video, newVideo);

                        Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                requireActivity(),
                                ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                DetailsActivity.SHARED_ELEMENT_NAME)
                                .toBundle();
                        requireActivity().startActivity(intent, bundle);
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
                videos.clear();
                refreshSubscriptions(); // Refresh will get subs and videos again, then refresh row UI
                break;
            case LOGOUT:
                logout();
                break;
            case SELECT_SERVER:
                selectServer();
                break;
            case LIVESTREAM:
                selectLivestream();
                break;
        }
        return Unit.INSTANCE;
    }

    private void selectServer() {
        client.getCdnServers(hostnames -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Select CDN Server")
                    .setItems(hostnames,
                            (dialog, which) -> {
                                String server = hostnames[which];
                                dLog("CDN", server);
                                SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
                                prefs.edit().putString("cdn", server).apply();
                            })
                    .create()
                    .show();
            return Unit.INSTANCE;
        });
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

        //TODO -- Fix 4K playback
        if (res == "2160") {
            res = "1080";
        }
        return res;
    }

    public static void dLog(String tag, String msg) {
        if (debug) {
            Log.d(tag, msg);
        }
    }
}
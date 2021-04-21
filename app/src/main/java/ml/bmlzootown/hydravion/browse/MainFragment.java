package ml.bmlzootown.hydravion.browse;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;
import ml.bmlzootown.hydravion.CardPresenter;
import ml.bmlzootown.hydravion.Constants;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.login.LoginActivity;
import ml.bmlzootown.hydravion.models.Live;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.playback.PlaybackActivity;
import ml.bmlzootown.hydravion.subscription.Subscription;
import ml.bmlzootown.hydravion.subscription.SubscriptionHeaderPresenter;

public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";

    private HydravionClient client;
    private DisplayMetrics mMetrics;
    private BackgroundManager mBackgroundManager;

    public static String sailssid;
    public static String cfduid;
    public static String cdn;

    public static List<Subscription> subscriptions = new ArrayList<>();
    public static HashMap<String, ArrayList<Video>> videos = new HashMap<>();
    private int subCount;
    private int page = 1;

    private int rowSelected;
    private int colSelected;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        client = HydravionClient.Companion.getInstance(requireActivity(), requireActivity().getPreferences(Context.MODE_PRIVATE));
        checkLogin();
        //test();

        //prepareBackgroundManager();

        //setupUIElements();

        //setupEventListeners();
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
                if (c[0].equalsIgnoreCase("__cfduid")) {
                    cfduid = c[1];
                }
            }
            Log.d("MainFragment", cfduid + "; " + sailssid);

            saveCredentials();
            initialize();
        }
    }

    private void initialize() {
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
        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();
    }

    private boolean loadCredentials() {
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        sailssid = prefs.getString(Constants.PREF_SAIL_SSID, "default");
        cfduid = prefs.getString(Constants.PREF_CFD_UID, "default");
        cdn = prefs.getString(Constants.PREF_CDN, "default");
        Log.d("LOGIN", sailssid);
        Log.d("LOGIN", cfduid);
        Log.d("CDN", cdn);
        if (sailssid.equals("default") || cfduid.equals("default") || cdn.equals("default")) {
            Log.d("LOGIN", "Credentials not found!");
            return false;
        } else {
            Log.d("LOGIN", "Credentials found!");
            return true;
        }
    }

    private void logout() {
        sailssid = "default";
        cfduid = "default";
        saveCredentials();
        getActivity().finishAndRemoveTask();
    }

    private void saveCredentials() {
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREF_SAIL_SSID, sailssid).apply();
        prefs.edit().putString(Constants.PREF_CFD_UID, cfduid).apply();
        prefs.edit().putString(Constants.PREF_CDN, cdn).apply();
    }

    private void gotLiveInfo(Subscription sub, Live live) {
        String l = live.getCdn() + live.getResource().getUri();
        String pattern = "\\{(.*?)\\}";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(live.getResource().getUri());
        if (m.find()) {
            for (int i = 0; i < m.groupCount(); i++) {
                //Log.d("LIVE", m.group(i));
                String var = m.group(i).substring(1, m.group(i).length() - 1);
                if (var.equalsIgnoreCase("token")) {
                    l = l.replaceAll("\\{token\\}", live.getResource().getData().getToken());
                    sub.setStreamUrl(l);
                    Log.d("LIVE", l);
                }
                //Log.d("LIVE", l);
            }
        }
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
            client.getLive(sub.getCreator(), live -> {
                gotLiveInfo(sub, live);
                return Unit.INSTANCE;
            });
            client.getVideos(sub.getCreator(), 1, videos -> {
                gotVideos(sub.getCreator(), videos);
                return Unit.INSTANCE;
            });
        }
        subCount = trimmed.size();
        Log.d("ROWS", trimmed.size() + "");
    }

    private boolean containsSub(List<Subscription> trimmed, Subscription sub) {
        for (Subscription s : trimmed) {
            if (s.getCreator().equals(sub.getCreator())) {
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

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(requireActivity());
        mBackgroundManager.attach(requireActivity().getWindow());

        mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupUIElements() {
        // setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.videos_by_google_banner));
        setBadgeDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.white_plane));
        //setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent
        // over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object item) {
                return new SubscriptionHeaderPresenter();
            }
        });

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(requireContext(), R.color.fastlane_background));
        // set search icon color
        //setSearchAffordanceColor(ContextCompat.getColor(getContext(), R.color.search_opaque));
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
            client.getVideo(video, newVideo -> {
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra(DetailsActivity.Video, newVideo);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        DetailsActivity.SHARED_ELEMENT_NAME)
                        .toBundle();
                getActivity().startActivity(intent, bundle);
                return Unit.INSTANCE;
            });
        }

        return Unit.INSTANCE;
    }

    private Unit onSettingsSelected(@NonNull SettingsAction action) {
        switch (action) {
            case REFRESH:
                refreshRows();
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
                                Log.d("CDN", server);
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
            subs.add(s.getPlan().getTitle());
        }
        CharSequence[] s = subs.toArray(new CharSequence[subs.size()]);
        new AlertDialog.Builder(getContext())
                .setTitle("Play livestream?")
                .setItems(s, (DialogInterface.OnClickListener) (dialog, which) -> {
                    String stream = subscriptions.get(which).getStreamUrl();
                    if (stream != null) {
                        Log.d("LIVE", stream);
                        Video live = new Video();
                        live.setVidUrl(stream);
                        Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                        intent.putExtra(DetailsActivity.Video, (Serializable) live);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getActivity(), "Subscription does not include access to livestream.", Toast.LENGTH_LONG).show();
                    }
                })
                .create()
                .show();
    }
}
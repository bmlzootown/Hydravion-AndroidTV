package ml.bmlzootown.hydravion.authenticate;

import android.content.Context;

import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogoutRequestTask {
    private Context context;

    public LogoutRequestTask(Context context) {
        this.context = context;
    }

    public void logout(String cookies, final LogoutRequestTask.VolleyCallback callback) {
        String uri = "https://www.floatplane.com/api/auth/logout";
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, uri,
                callback::onSuccess, error -> {
            error.printStackTrace();
            callback.onError(error);
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Cookie", cookies);
                params.put("Accept", "application/json");
                params.put("User-Agent", "Hydravion (AndroidTV), CFNetwork");
                return params;
            }
        };

        queue.add(stringRequest);
    }

    public interface VolleyCallback {
        void onSuccess(String response);

        void onError(VolleyError error);
    }

}

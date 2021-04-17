package ml.bmlzootown.hydravion;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

public class RequestTask {

    private static String response;
    private Context context;

    public RequestTask(Context context) {
        this.context = context;
    }

    public void sendRequest(String uri, final String cookies, final VolleyCallback callback) {
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, uri,
                response -> {
                    //Log.d("JSON", response);
                    callback.onSuccess(response);
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                callback.onError(error);
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Cookie", cookies);
                params.put("Accept", "application/json");
                return params;
            }
        };

        queue.add(stringRequest);
    }

    public void sendRequest(String uri, final String cookies, final String creatorGUID, final VolleyCallback callback) {
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, uri,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        //Log.d("JSON", response);
                        callback.onSuccessCreator(response, creatorGUID);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                callback.onError(error);
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Cookie", cookies);
                params.put("Accept", "application/json");
                return params;
            }
        };

        queue.add(stringRequest);
    }

    public interface VolleyCallback {

        void onSuccess(String string);

        void onSuccessCreator(String string, String creatorGUID);

        void onError(VolleyError error);
    }
}

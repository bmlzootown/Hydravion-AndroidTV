package ml.bmlzootown.hydravion.login;

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

public class LoginRequestTask {
    private Context context;
    private ArrayList<String> cookies;

    public LoginRequestTask(Context context) {
        this.context = context;
    }

    public void sendRequest(String user, String pass, final LoginRequestTask.VolleyCallback callback) {
        String uri = "https://www.floatplane.com/api/auth/login";
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, uri,
                response -> callback.onSuccess(cookies, response), error -> {
            error.printStackTrace();
            callback.onError();
        }) {
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                List<Header> headers = response.allHeaders;
                ArrayList<String> cs = new ArrayList<>();
                for (Header header : headers) {
                    if (header.getName().equals("Set-Cookie")) {
                        cs.add(header.getValue().split(";")[0]);
                    }
                }
                cookies = cs;
                return super.parseNetworkResponse(response);
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Accept", "application/json");
                params.put("User-Agent", "Hydravion (AndroidTV), CFNetwork");
                return params;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("username", user);
                params.put("password", pass);

                return params;
            }
        };

        queue.add(stringRequest);
    }

    public void sendRequest(String token, ArrayList<String> cookiez, final LoginRequestTask.TwoFACallback callback) {
        String uri = "https://www.floatplane.com/api/v2/auth/checkFor2faLogin";
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, uri,
                response -> callback.onSuccess(cookies), error -> {
            error.printStackTrace();
            callback.onError(error);
        }) {
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                List<Header> headers = response.allHeaders;
                ArrayList<String> cs = new ArrayList<>();
                for (Header header : headers) {
                    if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                        cs.add(header.getValue().split(";")[0]);
                    }
                }
                cookies = cs;
                return super.parseNetworkResponse(response);
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                StringBuilder cs = new StringBuilder();
                for (String c : cookiez) {
                    cs.append(c).append(";");
                }
                params.put("Cookie", cs.toString());
                params.put("Accept", "application/json");
                return params;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("token", token);

                return params;
            }
        };

        queue.add(stringRequest);
    }

    public interface TwoFACallback {
        void onSuccess(ArrayList<String> string);

        void onError(VolleyError ve);
    }

    public interface VolleyCallback {
        void onSuccess(ArrayList<String> string, String response);

        void onError();
    }

}

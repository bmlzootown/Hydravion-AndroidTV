package ml.bmlzootown.hydravion;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class TokenRequestTask {
    private Context context;
    public static String generate = "https://www.bmlzoo.town/hydravion/generate?token=";
    public static String authenticate = "https://www.bmlzoo.town/hydravion/authenticate?token=";
    public static String disconenct = "https://www.bmlzoo.town/hydravion/disconnect?token=";

    public TokenRequestTask(Context context) {
        this.context = context;
    }

    public void doRequest(String uri, final TokenRequestTask.VolleyCallback callback) {
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, uri,
                response -> callback.onSuccess(response), error -> {
                    error.printStackTrace();
                    callback.onError(error);
                }) {};

        queue.add(stringRequest);
    }

    public interface VolleyCallback {
        void onSuccess(String response);
        void onError(VolleyError error);
    }

}

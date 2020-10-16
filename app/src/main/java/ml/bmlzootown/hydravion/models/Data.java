package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Data implements Serializable
{

    @SerializedName("token")
    @Expose
    private String token;
    private final static long serialVersionUID = -1210501026000171807L;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

}
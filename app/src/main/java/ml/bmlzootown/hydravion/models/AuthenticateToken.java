package ml.bmlzootown.hydravion.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class AuthenticateToken {

    @SerializedName("linked")
    @Expose
    private String linked;
    @SerializedName("oauth_token")
    @Expose
    private String oauthToken;

    public String getLinked() {
        return linked;
    }

    public void setLinked(String linked) {
        this.linked = linked;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

}
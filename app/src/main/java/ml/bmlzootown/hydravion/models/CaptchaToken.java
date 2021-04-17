package ml.bmlzootown.hydravion.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class CaptchaToken {

    @SerializedName("code")
    @Expose
    private String code;
    @SerializedName("expiration")
    @Expose
    private Integer expiration;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getExpiration() {
        return expiration;
    }

    public void setExpiration(Integer expiration) {
        this.expiration = expiration;
    }

}
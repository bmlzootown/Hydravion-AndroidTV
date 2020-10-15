package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class LoginResponse implements Serializable
{

    @SerializedName("needs2FA")
    @Expose
    private Boolean needs2FA;
    private final static long serialVersionUID = -4398239096952064720L;

    public Boolean getNeeds2FA() {
        return needs2FA;
    }

    public void setNeeds2FA(Boolean needs2FA) {
        this.needs2FA = needs2FA;
    }

}
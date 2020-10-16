package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Resource implements Serializable
{

    @SerializedName("uri")
    @Expose
    private String uri;
    @SerializedName("data")
    @Expose
    private Data data;
    private final static long serialVersionUID = -3382096757991842096L;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

}
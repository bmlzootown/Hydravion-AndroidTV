package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Live implements Serializable
{

    @SerializedName("cdn")
    @Expose
    private String cdn;
    @SerializedName("strategy")
    @Expose
    private String strategy;
    @SerializedName("resource")
    @Expose
    private Resource resource;
    private final static long serialVersionUID = -1809509705115050385L;

    public String getCdn() {
        return cdn;
    }

    public void setCdn(String cdn) {
        this.cdn = cdn;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

}
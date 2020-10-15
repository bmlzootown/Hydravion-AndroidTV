package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Edge implements Serializable
{

    @SerializedName("hostname")
    @Expose
    private String hostname;
    @SerializedName("queryPort")
    @Expose
    private Integer queryPort;
    @SerializedName("bandwidth")
    @Expose
    private Long bandwidth;
    @SerializedName("allowDownload")
    @Expose
    private Boolean allowDownload;
    @SerializedName("allowStreaming")
    @Expose
    private Boolean allowStreaming;
    @SerializedName("datacenter")
    @Expose
    private Datacenter datacenter;
    private final static long serialVersionUID = 5533637734963865843L;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Integer getQueryPort() {
        return queryPort;
    }

    public void setQueryPort(Integer queryPort) {
        this.queryPort = queryPort;
    }

    public Long getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(Long bandwidth) {
        this.bandwidth = bandwidth;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public Boolean getAllowStreaming() {
        return allowStreaming;
    }

    public void setAllowStreaming(Boolean allowStreaming) {
        this.allowStreaming = allowStreaming;
    }

    public Datacenter getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(Datacenter datacenter) {
        this.datacenter = datacenter;
    }

}
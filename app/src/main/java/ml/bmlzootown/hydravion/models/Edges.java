package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Edges implements Serializable
{

    @SerializedName("edges")
    @Expose
    private List<Edge> edges = null;
    @SerializedName("client")
    @Expose
    private Client client;
    private final static long serialVersionUID = -2484672046679225559L;

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

}
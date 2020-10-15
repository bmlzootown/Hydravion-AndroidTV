package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TimelineSprite implements Serializable
{

    @SerializedName("width")
    @Expose
    private Integer width;
    @SerializedName("height")
    @Expose
    private Integer height;
    @SerializedName("path")
    @Expose
    private String path;
    @SerializedName("childImages")
    @Expose
    private List<Object> childImages = null;
    private final static long serialVersionUID = -6988319265401232931L;

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<Object> getChildImages() {
        return childImages;
    }

    public void setChildImages(List<Object> childImages) {
        this.childImages = childImages;
    }

}

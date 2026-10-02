package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Thumbnail implements Serializable
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
    private List<ChildImage> childImages = null;
    private final static long serialVersionUID = 4394307609485324681L;

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

    /** Child rendition when the API sent one, otherwise the original path. */
    public String getBestPath() {
        if (childImages != null) {
            for (ChildImage child : childImages) {
                if (child != null && child.getPath() != null && !child.getPath().isEmpty()) {
                    return child.getPath();
                }
            }
        }
        if (path != null && !path.isEmpty()) {
            return path;
        }
        return null;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<ChildImage> getChildImages() {
        return childImages;
    }

    public void setChildImages(List<ChildImage> childImages) {
        this.childImages = childImages;
    }

}
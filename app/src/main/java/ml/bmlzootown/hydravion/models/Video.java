package ml.bmlzootown.hydravion.models;

import java.io.Serializable;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Video implements Serializable
{

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("guid")
    @Expose
    private String guid;
    //----
    @SerializedName("vidurl")
    @Expose
    private String vidurl;
    //---
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("description")
    @Expose
    private String description;
    @SerializedName("releaseDate")
    @Expose
    private String releaseDate;
    @SerializedName("duration")
    @Expose
    private Integer duration;
    @SerializedName("creator")
    @Expose
    private String creator;
    @SerializedName("likes")
    @Expose
    private Integer likes;
    @SerializedName("dislikes")
    @Expose
    private Integer dislikes;
    @SerializedName("score")
    @Expose
    private Integer score;
    @SerializedName("isProcessing")
    @Expose
    private Boolean isProcessing;
    @SerializedName("primaryBlogPost")
    @Expose
    private String primaryBlogPost;
    @SerializedName("thumbnail")
    @Expose
    private Thumbnail thumbnail;
    @SerializedName("isAccessible")
    @Expose
    private Boolean isAccessible;
    @SerializedName("hasAccess")
    @Expose
    private Boolean hasAccess;
    @SerializedName("private")
    @Expose
    private Boolean _private;
    @SerializedName("subscriptionPermissions")
    @Expose
    private List<String> subscriptionPermissions = null;
    private final static long serialVersionUID = -5477687235495303564L;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    //---
    public String getVidUrl() {
        return vidurl;
    }

    public void setVidUrl(String vidurl) {
        this.vidurl = vidurl;
    }
    //---

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Integer getLikes() {
        return likes;
    }

    public void setLikes(Integer likes) {
        this.likes = likes;
    }

    public Integer getDislikes() {
        return dislikes;
    }

    public void setDislikes(Integer dislikes) {
        this.dislikes = dislikes;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Boolean getIsProcessing() {
        return isProcessing;
    }

    public void setIsProcessing(Boolean isProcessing) {
        this.isProcessing = isProcessing;
    }

    public String getPrimaryBlogPost() {
        return primaryBlogPost;
    }

    public void setPrimaryBlogPost(String primaryBlogPost) {
        this.primaryBlogPost = primaryBlogPost;
    }

    public Thumbnail getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Thumbnail thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Boolean getIsAccessible() {
        return isAccessible;
    }

    public void setIsAccessible(Boolean isAccessible) {
        this.isAccessible = isAccessible;
    }

    public Boolean getHasAccess() {
        return hasAccess;
    }

    public void setHasAccess(Boolean hasAccess) {
        this.hasAccess = hasAccess;
    }

    public Boolean getPrivate() {
        return _private;
    }

    public void setPrivate(Boolean _private) {
        this._private = _private;
    }

    public List<String> getSubscriptionPermissions() {
        return subscriptionPermissions;
    }

    public void setSubscriptionPermissions(List<String> subscriptionPermissions) {
        this.subscriptionPermissions = subscriptionPermissions;
    }

}
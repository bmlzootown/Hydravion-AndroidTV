package ml.bmlzootown.hydravion.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Plan {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("description")
    @Expose
    private String description;
    @SerializedName("price")
    @Expose
    private String price;
    @SerializedName("priceYearly")
    @Expose
    private Object priceYearly;
    @SerializedName("currency")
    @Expose
    private String currency;
    @SerializedName("logo")
    @Expose
    private Object logo;
    @SerializedName("interval")
    @Expose
    private String interval;
    @SerializedName("featured")
    @Expose
    private Boolean featured;
    @SerializedName("allowGrandfatheredAccess")
    @Expose
    private Boolean allowGrandfatheredAccess;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public Object getPriceYearly() {
        return priceYearly;
    }

    public void setPriceYearly(Object priceYearly) {
        this.priceYearly = priceYearly;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Object getLogo() {
        return logo;
    }

    public void setLogo(Object logo) {
        this.logo = logo;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public Boolean getAllowGrandfatheredAccess() {
        return allowGrandfatheredAccess;
    }

    public void setAllowGrandfatheredAccess(Boolean allowGrandfatheredAccess) {
        this.allowGrandfatheredAccess = allowGrandfatheredAccess;
    }

}
package ml.bmlzootown.hydravion.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Subscription {

    @SerializedName("startDate")
    @Expose
    private String startDate;
    @SerializedName("endDate")
    @Expose
    private String endDate;
    @SerializedName("paymentID")
    @Expose
    private Integer paymentID;
    @SerializedName("interval")
    @Expose
    private String interval;
    @SerializedName("paymentCancelled")
    @Expose
    private Boolean paymentCancelled;
    @SerializedName("plan")
    @Expose
    private Plan plan;
    @SerializedName("creator")
    @Expose
    private String creator;

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Integer getPaymentID() {
        return paymentID;
    }

    public void setPaymentID(Integer paymentID) {
        this.paymentID = paymentID;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Boolean getPaymentCancelled() {
        return paymentCancelled;
    }

    public void setPaymentCancelled(Boolean paymentCancelled) {
        this.paymentCancelled = paymentCancelled;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

}
package com.hackathon.aianalysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AnalyzeResponse {

    @JsonProperty("출발지")
    private String departure;

    @JsonProperty("목적지")
    private String destination;

    @JsonProperty("목표")
    private String goal;

    @JsonProperty("물건")
    private String item;

    @JsonProperty("기타")
    private String etc;

    public String getDeparture() { return departure; }
    public void setDeparture(String departure) { this.departure = departure; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public String getEtc() { return etc; }
    public void setEtc(String etc) { this.etc = etc; }
}

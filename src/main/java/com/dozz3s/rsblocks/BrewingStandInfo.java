package com.dozz3s.rsblocks;

public class BrewingStandInfo {
    private final int brewTime;

    public BrewingStandInfo(int accelerationFactor) {
        this.brewTime = accelerationFactor;
    }

    public int getBrewTime() {
        return brewTime;
    }
}
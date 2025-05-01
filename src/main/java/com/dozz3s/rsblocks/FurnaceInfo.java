package com.dozz3s.rsblocks;

public class FurnaceInfo {
    private final int cookTime;

    public FurnaceInfo(int accelerationFactor) {
        this.cookTime = accelerationFactor;
    }

    public int getCookTime() {
        return cookTime;
    }
}
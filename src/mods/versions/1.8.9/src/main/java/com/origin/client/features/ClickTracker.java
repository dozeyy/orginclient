package com.origin.client.features;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling 1-second click counters feeding the CPS HUD element. */
public final class ClickTracker {

    private static final Deque<Long> LEFT = new ArrayDeque<Long>();
    private static final Deque<Long> RIGHT = new ArrayDeque<Long>();

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!event.buttonstate) return;
        long now = System.currentTimeMillis();
        if (event.button == 0) LEFT.addLast(now);
        else if (event.button == 1) RIGHT.addLast(now);
    }

    private static int count(Deque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
        return q.size();
    }

    public static int leftCps() { return count(LEFT); }

    public static int rightCps() { return count(RIGHT); }
}

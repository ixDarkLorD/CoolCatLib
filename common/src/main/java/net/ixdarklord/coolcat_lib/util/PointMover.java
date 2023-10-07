package net.ixdarklord.coolcat_lib.util;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PointMover {
    private double x;
    private double y;
    private final List<Double> xList;
    private final List<Double> yList;
    private final int indexOf;
    private final int time;
    private final double speed;
    private double alpha; // Opacity value (0.0 to 1.0)

    public PointMover(double x, double y, int timeMilliseconds, double speed) {
        this.x = x;
        this.y = y;
        this.xList = null;
        this.yList = null;
        this.indexOf = -1;
        this.time = timeMilliseconds;
        this.speed = speed;
        this.alpha = 0.0;
    }

    public PointMover(List<Double> xList, List<Double> yList, int indexOf, int timeMilliseconds, double speed) {
        this.x = 0;
        this.y = 0;
        this.xList = xList;
        this.yList = yList;
        this.indexOf = indexOf;
        this.time = timeMilliseconds;
        this.speed = speed;
        this.alpha = 0.0;
    }

    public void moveTo(double newX, double newY) {
        double dx = newX - x;
        double dy = newY - y;
        if (xList != null && yList != null && indexOf >= 0) {
            dx = newX - xList.get(indexOf);
            dy = newY - yList.get(indexOf);
        }
        double distance = Math.sqrt(dx * dx + dy * dy);
        int numSteps = (int) (distance / speed);

        // Calculate the step size for position and alpha
        double stepX = dx / numSteps;
        double stepY = dy / numSteps;
        double stepAlpha = 1.0 / numSteps; // Alpha step for smooth fade-in and fade-out

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            private int stepCount = 0;

            @Override
            public void run() {
                if (stepCount < numSteps) {
                    if (xList != null && yList != null && indexOf >= 0) {
                        xList.set(indexOf, xList.get(indexOf) + stepX);
                        yList.set(indexOf, yList.get(indexOf) + stepY);
                        System.out.println("Position: (" + xList.get(indexOf) + ", " + yList.get(indexOf) + "), Alpha: " + alpha);
                    } else {
                        x += stepX;
                        y += stepY;
                        System.out.println("Position: (" + x + ", " + y + "), Alpha: " + alpha);
                    }
                    alpha += stepAlpha;
                    stepCount++;
                } else {
                    timer.cancel();
                }
            }
        }, 0, time); // Update at the specified speed interval
    }
}

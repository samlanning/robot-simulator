package com.samlanning.robot_simulator.simulator.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.samlanning.robot_simulator.iface.MapBlock;
import com.samlanning.robot_simulator.iface.RobotMap;
import com.samlanning.robot_simulator.robots.RobotsEnum;
import com.samlanning.robot_simulator.simulator.executor.Direction;
import com.samlanning.robot_simulator.simulator.executor.RobotState;
import com.samlanning.robot_simulator.simulator.gui.GUIState.GUIRobotState;

public class MapPanel extends JPanel implements GUIState.Listener {
    
    private final GUIState state;
    private final AnimationThread animationThread;
    
    protected MapPanel(GUIState state) {
        this.state = state;
        
        animationThread = new AnimationThread();
        animationThread.start();
        
        state.addListener(this);
    }
    
    @Override
    public void update() {
        animationThread.drawFrames(true);
    }
    
    @Override
    public void updateFrameDuration(long duration) {
        animationThread.updateFrameDuration(duration);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        Rectangle clip = g.getClipBounds();
        
        RobotMap map = state.map();
        if (map == null) {
            FontMetrics fm = g.getFontMetrics();
            String text = "No Map Loaded";
            int x, y;
            x = (clip.width - fm.stringWidth(text)) / 2;
            y = (clip.height - fm.getHeight()) / 2;
            g.drawString(text, x, y);
            return;
        }
        
        int mapWidth;
        int mapHeight;
        int widthFromHeight = clip.height / map.getHeight() * map.getWidth();
        if (widthFromHeight > clip.width) {
            mapWidth = clip.width;
            mapHeight = clip.width / map.getWidth() * map.getHeight();
        } else {
            mapWidth = widthFromHeight;
            mapHeight = clip.height;
        }
        int mapX = (clip.width - mapWidth) / 2;
        int mapY = (clip.height - mapHeight) / 2;
        
        g.setColor(Color.black);
        g.fillRect(mapX, mapY, mapWidth, mapHeight);
        
        // Add Tiles
        int totalX = map.getWidth();
        int totalY = map.getHeight();
        int tileWidth = mapWidth / totalX;
        int tileHeight = mapHeight / totalY;
        for (int x = 0; x < totalX; x++) {
            for (int y = 0; y < totalY; y++) {
                Rectangle r = getRectangle(mapX, mapY, tileWidth, tileHeight, x, y);
                paintTile(g, r, map.getBlock(x, y));
            }
        }
        
        // Add Robots
        boolean animating = false;
        Map<Point, List<RobotsEnum>> overlappingRobots = new HashMap<>();
        for (Map.Entry<RobotsEnum, GUIRobotState> entry : state.robots().entrySet()) {
            Point p = new Point(entry.getValue().state.x, entry.getValue().state.y);
            Rectangle r = getRectangle(mapX, mapY, tileWidth, tileHeight, p.x, p.y);
            animating =
                paintRobot(g2, r, tileWidth, tileHeight, entry.getKey(), entry.getValue())
                    || animating;
            List<RobotsEnum> robots = overlappingRobots.get(p);
            if (robots == null) {
                robots = new LinkedList<>();
                overlappingRobots.put(p, robots);
            }
            robots.add(entry.getKey());
        }
        
        // Add Overlapping Robots Indicators
        for (Map.Entry<Point, List<RobotsEnum>> entry : overlappingRobots.entrySet()) {
            if (entry.getValue().size() > 1) {
                Rectangle r =
                    getRectangle(mapX, mapY, tileWidth, tileHeight, entry.getKey().x,
                        entry.getKey().y);
                drawOverlappingIndicators(g, r, entry.getValue());
            }
        }
        
        // Stop drawing more frames if there are no animations
        if (!animating)
            animationThread.drawFrames(false);
    }
    
    private Rectangle getRectangle(int mapX, int mapY, int tileWidth, int tileHeight, int x, int y) {
        return new Rectangle(mapX + x * tileWidth, mapY + y * tileHeight, tileWidth, tileHeight);
    }
    
    private void paintTile(Graphics g, Rectangle clip, MapBlock block) {
        
        switch (block) {
            case EMPTY:
                g.setColor(Color.white);
                break;
            
            case FINISH:
                g.setColor(new Color(00, 200, 255));
                break;
            
            case WALL:
                g.setColor(new Color(30, 30, 30));
                break;
        }
        g.fillRect(clip.x + 1, clip.y + 1, clip.width - 2, clip.height - 2);
    }
    
    /**
     * @return true if the robot is in the process of being animated
     */
    private boolean paintRobot(Graphics2D g, Rectangle clip, int tileWidth, int tileHeight,
        RobotsEnum robot, GUIRobotState state) {
        
        boolean animated = false;
        long currentTime = System.currentTimeMillis();
        double animationRemaining = 0;
        
        int robotX = clip.x;
        int robotY = clip.y;
        
        if (state.state.status == RobotState.Status.CRASHED) {
            // Move into wall
            robotX += tileWidth * state.state.direction.vectorX() * 0.2;
            robotY += tileHeight * state.state.direction.vectorY() * 0.2;
        }
        
        if (currentTime < state.animatingTo) {
            animationRemaining =
                (double) (state.animatingTo - currentTime)
                    / (double) (state.animatingTo - state.animatingFrom);
            if (state.state.x != state.previousState.x) {
                robotX += (state.previousState.x - state.state.x) * tileWidth * animationRemaining;
                animated = true;
            }
            if (state.state.y != state.previousState.y) {
                robotY += (state.previousState.y - state.state.y) * tileHeight * animationRemaining;
                animated = true;
            }
            if (state.state.status == RobotState.Status.CRASHED) {
                // Move into wall
                double mul;
                if (animationRemaining > 0.5) {
                    mul = animationRemaining * 3 - 2;
                } else {
                    mul = 0 - animationRemaining;
                }
                robotX -= tileWidth * mul * state.state.direction.vectorX() * 0.2;
                robotY -= tileHeight * mul * state.state.direction.vectorY() * 0.2;
                animated = true;
            }
        }
        
        if (state.state.status == RobotState.Status.RUNNING)
            g.setColor(robot.color);
        else {
            int alpha = 100;
            if (currentTime < state.animatingTo) {
                alpha += 155 * animationRemaining;
            }
            g.setColor(new Color(robot.color.getRed(), robot.color.getGreen(), robot.color
                .getBlue(), alpha));
        }
        
        g.fillOval(robotX + 10, robotY + 10, clip.width - 20, clip.height - 20);
        
        g.setColor(Color.white);
        
        double angle = getAngle(state.state.direction);
        if (currentTime < state.animatingTo
            && state.state.direction != state.previousState.direction) {
            if (state.previousState.direction.anticlockwise() == state.state.direction) {
                angle += Math.PI / 2 * animationRemaining;
            } else {
                angle -= Math.PI / 2 * animationRemaining;
            }
            animated = true;
        }
        
        int centreX = robotX + clip.width / 2;
        int centreY = robotY + clip.height / 2;
        int length = clip.width / 2 - 15;
        int otherX = centreX + (int) (Math.sin(angle) * length);
        int otherY = centreY - (int) (Math.cos(angle) * length);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(centreX, centreY, otherX, otherY);
        return animated;
        
    }
    
    private static double getAngle(Direction direction) {
        switch (direction) {
            case UP:
                return 0;
            case RIGHT:
                return Math.PI / 2;
            case DOWN:
                return Math.PI;
            case LEFT:
                return Math.PI * 3 / 2;
        }
        throw new InternalError("Unrecognized Angle: " + direction);
    }
    
    private void drawOverlappingIndicators(Graphics g, Rectangle clip, List<RobotsEnum> robots) {
        g.setColor(Color.white);
        g.fillRoundRect(clip.x + 5, clip.y + 5, 5 + 10 * robots.size(), 15, 5, 5);
        int x = clip.x + 10;
        for (RobotsEnum robot : robots) {
            g.setColor(robot.color);
            g.fillOval(x, clip.y + 10, 5, 5);
            x += 10;
        }
    }
    
    private class AnimationThread extends Thread {
        
        private long frameDuration = 50;
        private boolean drawFrames = false;
        
        public AnimationThread() {
            super("Animation Thread");
        }
        
        @Override
        public void run() {
            long lastFrame = System.currentTimeMillis();
            while (true) {
                long target = lastFrame + frameDuration;
                long current = System.currentTimeMillis();
                try {
                    if (current < target)
                        Thread.sleep(target - current);
                    synchronized (this) {
                        while (!drawFrames)
                            wait();
                    }
                } catch (InterruptedException e) {
                    continue;
                }
                lastFrame = System.currentTimeMillis();
                MapPanel.this.repaint();
            }
        }
        
        public synchronized void drawFrames(boolean draw) {
            this.drawFrames = draw;
            notify();
        }
        
        public synchronized void updateFrameDuration(long duration) {
            this.frameDuration = duration;
            this.interrupt();
        }
        
    }
    
    @Override
    public void updateAnimationDuration(long duration) {}
    
    @Override
    public void updateDelay(long duration) {}
    
    @Override
    public void updateRunning(boolean running) {}
    
}

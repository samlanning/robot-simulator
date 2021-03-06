package com.samlanning.robot_simulator.robots;

import java.awt.Color;

import com.samlanning.robot_simulator.iface.Robot;

/**
 * An enum of all robots that you want to make available.
 */
public enum RobotsEnum {
    
    MY_ROBOT(new CustomRobot(), new Color(100, 100, 100)),
    RANDOM(new RandomRobot(), new Color(0, 100, 100)),
    ;
    
    public final Robot robot;
    public final Color color;
    
    private RobotsEnum(Robot robot, Color color){
        this.robot = robot;
        this.color = color;
    }
}

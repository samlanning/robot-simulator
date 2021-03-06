package com.samlanning.robot_simulator.simulator.executor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.samlanning.robot_simulator.iface.RobotMap;
import com.samlanning.robot_simulator.robots.RobotsEnum;
import com.samlanning.robot_simulator.simulator.executor.exceptions.StoppedException;

public class SimulatorExecutor {
    
    private final RobotsEnum[] robots;
    private final RobotExecutor[] robotExecutors;
    
    private final State state;
    
    public SimulatorExecutor(RobotMap map, Collection<RobotsEnum> robots, Listener listener,
        long delay) {
        this.robots = robots.toArray(new RobotsEnum[robots.size()]);
        this.robotExecutors = new RobotExecutor[this.robots.length];
        
        this.state = new State(delay);
        
        for (int i = 0; i < this.robots.length; i++) {
            this.robotExecutors[i] = new RobotExecutor(map, listener, state, this.robots[i]);
            this.robotExecutors[i].start();
        }
    }
    
    public void setRunning(boolean running) {
        state.setRunning(running);
    }
    
    public void stop() {
        state.stop();
    }
    
    public void setDelay(long delay) {
        state.setDelay(delay);
    }
    
    public static interface Listener {
        public void newRobotState(RobotsEnum robot, RobotState state);
    }
    
    protected static class State {
        
        private boolean running = false;
        private boolean stopped = false;
        private long delay;
        
        private Set<Thread> sleeping = new HashSet<>();
        
        public State(long delay) {
            this.delay = delay;
        }
        
        public synchronized void waitUntilRunning() throws StoppedException {
            while (!running) {
                if (stopped)
                    throw new StoppedException();
                try {
                    wait();
                } catch (InterruptedException e) {
                    /*
                     * Continue to wait until running, or until exited,
                     * regardless of what may have caused the interruption
                     */
                }
            }
        }
        
        private synchronized void addCurrentThreadToSleeping() {
            sleeping.add(Thread.currentThread());
        }
        
        private synchronized void removeCurrentThreadFromSleeping() {
            sleeping.remove(Thread.currentThread());
        }
        
        /**
         * Interruptable and adjustable wait
         * 
         * @throws StoppedException
         */
        public void waitForNextMove() throws StoppedException {
            waitUntilRunning();
            addCurrentThreadToSleeping();
            long waitingFrom = System.currentTimeMillis();
            while (true) {
                
                long currentTime = System.currentTimeMillis();
                long waitingTo = waitingFrom + delay;
                
                if (currentTime < waitingTo) {
                    try {
                        Thread.sleep(waitingTo - currentTime);
                        break;
                    } catch (InterruptedException e) {
                        synchronized (this) {
                            if (stopped) {
                                removeCurrentThreadFromSleeping();
                                throw new StoppedException();
                            }
                        }
                        /*
                         * Continue waiting if interrupted and not stopped. This
                         * will occur when the interval is changed while threads
                         * are waiting for the next move. The amount they need
                         * to wait will be adjusted.
                         */
                    }
                } else {
                    break;
                }
            }
            removeCurrentThreadFromSleeping();
        }
        
        private synchronized void setRunning(boolean running) {
            if (!stopped) {
                this.running = running;
                notifyAll();
            } else {
                throw new RuntimeException("Already Stopped");
            }
        }
        
        private synchronized void setDelay(long delay) {
            if (!stopped) {
                this.delay = delay;
                for (Thread thread : sleeping)
                    thread.interrupt();
            } else {
                throw new RuntimeException("Already Stopped");
            }
        }
        
        private synchronized void stop() {
            this.running = false;
            this.stopped = true;
            notifyAll();
            for (Thread thread : sleeping)
                thread.interrupt();
            sleeping.clear();
        }
        
    }
    
}

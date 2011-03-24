package com.real.cassandra.queue.locks.hazelcast;

import com.hazelcast.core.ILock;
import com.real.cassandra.queue.locks.Lock;

public class HazelcastLock implements Lock {
    private ILock lock;

    public HazelcastLock( ILock lock ) {
        this.lock = lock;
    }
    
    @Override
    public void release() {
        lock.unlock();
    }

}

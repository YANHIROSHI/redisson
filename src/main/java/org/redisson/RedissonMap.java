/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.redisson.core.RMap;

import com.lambdaworks.redis.RedisConnection;

//TODO implement watching by keys instead of map name
public class RedissonMap<K, V> implements RMap<K, V> {

    private final RedisConnection<Object, Object> connection;
    private final String name;
    private final Redisson redisson;

    RedissonMap(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        this.redisson = redisson;
        this.connection = connection;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public int size() {
        return connection.hlen(name).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return connection.hexists(name, key);
    }

    @Override
    public boolean containsValue(Object value) {
        return connection.hvals(name).contains(value);
    }

    @Override
    public V get(Object key) {
        return (V) connection.hget(name, key);
    }

    @Override
    public V put(K key, V value) {
        V prev = get(key);
        connection.hset(name, key, value);
        return prev;
    }

    @Override
    public V remove(Object key) {
        V prev = get(key);
        connection.hdel(name, key);
        return prev;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        connection.hmset(name, (Map<Object, Object>) map);
    }

    @Override
    public void clear() {
        connection.del(name);
    }

    @Override
    public Set<K> keySet() {
        // TODO use Set in internals
        return new HashSet<K>((Collection<? extends K>) connection.hkeys(name));
    }

    @Override
    public Collection<V> values() {
        return (Collection<V>) connection.hvals(name);
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        Map<Object, Object> map = connection.hgetall(name);
        Map<K, V> result = new HashMap<K, V>();
        for (java.util.Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put((K)entry.getKey(), (V)entry.getValue());
        }
        return result.entrySet();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        while (true) {
            Boolean res = connection.hsetnx(getName(), key, value);
            if (!res) {
                V result = get(key);
                if (result != null) {
                    return result;
                }
            } else {
                return null;
            }
        }
    }

    private boolean isEquals(RedisConnection<Object, Object> connection, Object key, Object value) {
        Object val = connection.hget(getName(), key);
        return (value != null && value.equals(val)) || (value == null && val == null);
    }

    @Override
    public boolean remove(Object key, Object value) {
        RedisConnection<Object, Object> connection = redisson.connect();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)
                        && isEquals(connection, key, value)) {
                    connection.multi();
                    connection.hdel(getName(), key);
                    if (connection.exec().size() == 1) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        RedisConnection<Object, Object> connection = redisson.connect();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)
                        && isEquals(connection, key, oldValue)) {
                    connection.multi();
                    connection.hset(getName(), key, newValue);
                    if (connection.exec().size() == 1) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public V replace(K key, V value) {
        RedisConnection<Object, Object> connection = redisson.connect();
        try {
            while (true) {
                connection.watch(getName());
                if (connection.hexists(getName(), key)) {
                    V prev = (V) connection.hget(getName(), key);
                    connection.multi();
                    connection.hset(getName(), key, value);
                    if (connection.exec().size() == 1) {
                        return prev;
                    }
                }
                return null;
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public void destroy() {
        connection.close();

        redisson.remove(this);
    }

}

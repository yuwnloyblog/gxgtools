package com.yuwnloy.gxgtools.lru;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class LRUHashMap<K, V> {

	@SuppressWarnings("unused")
	private static final long serialVersionUID = 8660712027640838753L;

	private static ScheduledExecutorService expireExecutor = Executors
			.newSingleThreadScheduledExecutor();

	private AtomicBoolean isCleanerRuning = new AtomicBoolean(false);

	private LRUContainerMap<K, TimestampEntryValue<V>> container;

	private Runnable expireRunnable = new Runnable() {

		@Override
		public void run() {
			long nextInterval = 1000;
			container.lock();
			try {
				boolean shouldStopCleaner = true;
				if (container.size() > 0) {
					long now = System.currentTimeMillis();
					List<K> toBeRemoved = new ArrayList<>();
					for (Entry<K, TimestampEntryValue<V>> e : container
							.entrySet()) {
						K key = e.getKey();
						TimestampEntryValue<V> tValue = e.getValue();
						long timeLapsed = now - tValue.timestamp;
						if (timeLapsed >= duration) {
							toBeRemoved.add(key);
						} else {
							long delta = duration - timeLapsed;
							if (delta > 1000L) {
								nextInterval = delta;
							}
							break;
						}
					}

					if (toBeRemoved.size() > 0) {
						for (K key : toBeRemoved) {
							container.remove(key);
						}
					}

					if (container.size() > 0) {
						shouldStopCleaner = false;
					}
				}

				if (shouldStopCleaner) {
					isCleanerRuning.compareAndSet(true, false);
				} else {
					expireExecutor.schedule(this, nextInterval,
							TimeUnit.MILLISECONDS);
				}

			} finally {
				container.unlock();
			}

		}
	};

	private long duration = -1;

	public LRUHashMap(int maxSize, final F.Action2<K, V> onEvict) {
		this(maxSize, onEvict, -1L);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public LRUHashMap(int maxSize, final F.Action2<K, V> onEvict, long duration) {

		F.Action2<K, TimestampEntryValue<V>> doOnEvict = null;

		if (onEvict != null) {
			doOnEvict = new F.Action2<K, LRUHashMap.TimestampEntryValue<V>>() {

				@Override
				public void invoke(K key, TimestampEntryValue<V> value) {
					if (value != null) {
						onEvict.invoke(key, value.value);
					}
				}
			};
		}
		this.duration = duration;
		container = new LRUContainerMap(maxSize, doOnEvict);

	}

	int getMaxSize() {
		return container.getMaxSize();
	}

	void setMaxSize(int maxSize) {
		container.setMaxSize(maxSize);
	}

	public long getDuration() {
		return duration;
	}

	public V put(K key, V value) {
		TimestampEntryValue<V> v = new TimestampEntryValue<>();
		v.timestamp = System.currentTimeMillis();
		v.value = value;
		TimestampEntryValue<V> old = container.put(key, v);

		if (duration > 0) {
			if (isCleanerRuning.compareAndSet(false, true)) {
				expireExecutor.schedule(expireRunnable, duration,
						TimeUnit.MILLISECONDS);
			}
		}

		return old == null ? null : old.value;
	}

	public V putIfAbsent(K key, V value) {
		TimestampEntryValue<V> v = new TimestampEntryValue<>();
		v.timestamp = System.currentTimeMillis();
		v.value = value;
		TimestampEntryValue<V> old = container.putIfAbsent(key, v);

		if (old == null) {
			if (duration > 0) {
				if (isCleanerRuning.compareAndSet(false, true)) {
					expireExecutor.schedule(expireRunnable, duration,
							TimeUnit.MILLISECONDS);
				}
			}
		}

		return old == null ? null : old.value;
	}

	public boolean containsKey(Object key) {
		return container.containsKey(key);
	}

	public V get(Object key) {
		TimestampEntryValue<V> got = container.get(key);
		V ret = null;
		if (got != null) {
			got.timestamp = System.currentTimeMillis();
			ret = got.value;
		}
		return ret;
	}

	public V remove(Object key) {
		TimestampEntryValue<V> removed = container.remove(key);
		V ret = null;
		if (removed != null) {
			ret = removed.value;
		}
		return ret;
	}

	public Map<K, Object> clone() {
		return container.clone();
	}

	static class TimestampEntryValue<V> {
		public V value;
		public long timestamp;
	}

	private static class LRUContainerMap<K, V extends TimestampEntryValue<?>>
			extends LinkedHashMap<K, V> {
		private static ExecutorService pool = Executors.newCachedThreadPool();
		private static final long serialVersionUID = -2108033306317724707L;

		private ReentrantLock lock = new ReentrantLock();

		private int maxSize;

		private F.Action2<K, V> onEvict;

		public LRUContainerMap(int maxSize, F.Action2<K, V> onEvict) {
			super(16, 0.75f, true);
			this.maxSize = maxSize;
			this.onEvict = onEvict;
		}

		public int getMaxSize() {
			return maxSize;
		}

		public void setMaxSize(int maxSize) {
			this.maxSize = maxSize;
		}

		public void lock() {
			lock.lock();
		}

		public void unlock() {
			lock.unlock();
		}

		@Override
		public V put(K key, V value) {
			lock();
			try {
				return super.put(key, value);
			} finally {
				unlock();
			}
		}

		public V putIfAbsent(K key, V value) {
			lock();
			try {
				V result = super.get(key);
				if (result != null) {
					return result;
				} else {
					super.put(key, value);
					return null;
				}
			} finally {
				unlock();
			}
		}

		@Override
		public V get(Object key) {
			lock.lock();
			try {
				return super.get(key);
			} finally {
				lock.unlock();
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public V remove(final Object key) {
			lock();
			try {
				final V ret = super.remove(key);
				if (onEvict != null) {
					pool.execute(new Runnable() {
						@Override
						public void run() {
							try {
								onEvict.invoke((K) key, ret);
							} catch (Exception ignore) {
							}
						}
					});
				}
				return ret;
			} finally {
				unlock();
			}
		}

		@Override
		protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
			final boolean ret = size() > maxSize;
			if (onEvict != null && ret) {
				pool.execute(new Runnable() {
					@Override
					public void run() {
						onEvict.invoke(eldest.getKey(), eldest.getValue());
					}
				});
			}
			return ret;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Map<K, Object> clone() {
			Map<K, V> map;

			lock();
			try {
				map = (Map<K, V>) super.clone();
			} finally {
				unlock();
			}

			Iterator<Entry<K, V>> iter = map.entrySet().iterator();
			Map<K, Object> result = new HashMap<>();
			while (iter.hasNext()) {
				Entry<K, V> entry = iter.next();
				result.put(entry.getKey(), entry.getValue().value);
			}

			return result;
		}
	}

	public static void main(String[] args) {
		LRUHashMap<String, String> map = new LRUHashMap<>(10,
				new F.Action2<String, String>() {

					@Override
					public void invoke(String key, String value) {
						System.out.println("Entry evicted: key = " + key
								+ ", value = " + value);
					}
				}, 5000);

		for (int i = 0; i < 11; i++) {
			map.put("key" + i, "value" + i);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

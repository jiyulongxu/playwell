package playwell.storage.rocksdb;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.CollectionUtils;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import playwell.common.EasyMap;
import playwell.storage.rocksdb.RocksDBHelper.CfConfigItems;


/**
 * RocksDB操作，Not Thread Safe
 *
 * @author chihongze@gmail.com
 */
public class RocksDBOperation {

  // RocksDB Instance
  private final RocksDB rocksDBInstance;

  // Column Family Handle
  private final ColumnFamilyHandle columnFamilyHandle;

  // Column family configuration
  private final EasyMap cfConfig;

  // Current WriteBatch
  private WriteBatch writeBatch = null;


  public RocksDBOperation(
      RocksDB rocksDBInstance, ColumnFamilyHandle columnFamilyHandle, EasyMap cfConfig) {
    this.rocksDBInstance = rocksDBInstance;
    this.columnFamilyHandle = columnFamilyHandle;
    this.cfConfig = cfConfig;
  }

  /**
   * 开启WriteBatch构建批量写入
   *
   * @return RocksDBOperation
   */
  public RocksDBOperation beginWriteBatch() {
    this.writeBatch = new WriteBatch();
    return this;
  }

  public RocksDBOperation put(byte[] key, byte[] value) {
    try {
      // 非批量写入
      if (writeBatch == null) {
        rocksDBInstance.put(columnFamilyHandle, key, value);
      } else {
        writeBatch.put(columnFamilyHandle, key, value);
      }
      return this;
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public RocksDBOperation put(String key, byte[] value) {
    return this.put(key.getBytes(Charset.defaultCharset()), value);
  }

  public RocksDBOperation merge(byte[] key, byte[] value) {
    try {
      if (writeBatch == null) {
        rocksDBInstance.merge(columnFamilyHandle, key, value);
      } else {
        writeBatch.merge(columnFamilyHandle, key, value);
      }
      return this;
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public RocksDBOperation merge(String key, byte[] value) {
    return this.merge(key.getBytes(Charset.defaultCharset()), value);
  }

  public void endWriteBatch(WriteOptions writeOptions) {
    if (writeBatch == null) {
      throw new IllegalStateException(
          "Invalid rocksdb write batch state, you must begin write batch first!");
    }

    try {
      rocksDBInstance.write(writeOptions, writeBatch);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    } finally {
      writeBatch = null; // clean write batch
    }
  }

  public void endWriteBatch() {
    // no WriteOptions
    this.endWriteBatch(new WriteOptions());
  }

  public void delete(byte[] key) {
    try {
      this.rocksDBInstance.delete(columnFamilyHandle, key);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void delete(WriteOptions writeOptions, byte[] key) {
    try {
      this.rocksDBInstance.delete(columnFamilyHandle, writeOptions, key);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteRange(byte[] beginKey, byte[] endKey) {
    try {
      this.rocksDBInstance.deleteRange(columnFamilyHandle, beginKey, endKey);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] getBytes(byte[] key) {
    try {
      return rocksDBInstance.get(columnFamilyHandle, key);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] getBytes(String key) {
    return this.getBytes(key.getBytes(Charset.defaultCharset()));
  }

  public Map<byte[], byte[]> multiGet(List<byte[]> keys) {
    if (CollectionUtils.isEmpty(keys)) {
      return Collections.emptyMap();
    }

    try {
      final List<byte[]> values = rocksDBInstance
          .multiGetAsList(IntStream.range(0, keys.size()).mapToObj(i -> columnFamilyHandle)
              .collect(Collectors.toList()), keys);
      final Map<byte[], byte[]> result = new HashMap<>(keys.size());
      for (int i = 0; i < keys.size(); i++) {
        final byte[] value = values.get(i);
        if (value == null) {
          continue;
        }
        result.put(keys.get(i), value);
      }
      return result;
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public RocksIterator iterator() {
    return rocksDBInstance.newIterator(columnFamilyHandle);
  }

  public void iterateFromFirstWithConsumer(BiConsumer<byte[], byte[]> kvConsumer,
      boolean useSnapshot) {
    this.iterateFromFirstWithConsumer(
        null,
        kvConsumer,
        useSnapshot
    );
  }

  public void iterateFromFirstWithConsumer(
      BiPredicate<byte[], byte[]> until, BiConsumer<byte[], byte[]> kvConsumer,
      boolean useSnapshot) {
    Snapshot snapshot = null;
    try {
      final ReadOptions readOptions = newIteratorReadOptions();
      if (useSnapshot) {
        snapshot = rocksDBInstance.getSnapshot();
        readOptions.setSnapshot(snapshot);
      }
      iterateFromFirstWithConsumer(
          until,
          kvConsumer,
          readOptions
      );
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
  }

  public void iterateFromFirstWithConsumer(
      BiPredicate<byte[], byte[]> until, BiConsumer<byte[], byte[]> kvConsumer,
      ReadOptions readOptions) {
    try (final RocksIterator iterator = rocksDBInstance
        .newIterator(columnFamilyHandle, readOptions)) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        final byte[] keyBytes = iterator.key();
        final byte[] valueBytes = iterator.value();
        kvConsumer.accept(keyBytes, valueBytes);
        if (until != null && until.test(keyBytes, valueBytes)) {
          break;
        }
      }
    }
  }

  /**
   * 从头开始遍历列族，直到满足了指定的条件
   *
   * @param until 遍历停止条件
   * @param kvConsumer KV消费者
   * @param readOptions RocksDB readOptions
   */
  public <K, V> void iterateFromFirstWithConsumer(
      Function<byte[], K> keySerializer, Function<byte[], V> valueSerializer,
      BiPredicate<K, V> until, BiConsumer<K, V> kvConsumer, ReadOptions readOptions) {

    try (final RocksIterator iterator = rocksDBInstance
        .newIterator(columnFamilyHandle, readOptions)) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        final K key = keySerializer.apply(iterator.key());
        final V value = valueSerializer.apply(iterator.value());
        if (until.test(key, value)) {
          break;
        }

        kvConsumer.accept(key, value);
      }
    }
  }

  public <K, V> void iterateFromPrevKeyWithConsumer(byte[] from, Function<byte[], K> keySerializer,
      Function<byte[], V> valueSerializer, BiPredicate<K, V> until, BiConsumer<K, V> kvConsumer,
      ReadOptions readOptions) {
    try (final RocksIterator iterator = rocksDBInstance
        .newIterator(columnFamilyHandle, readOptions)) {
      iterator.seekForPrev(from);
      while (iterator.isValid()) {
        final K key = keySerializer.apply(iterator.key());
        final V value = valueSerializer.apply(iterator.value());
        if (until != null && until.test(key, value)) {
          break;
        }
        kvConsumer.accept(key, value);
        iterator.prev();
      }
    }
  }

  public <K, V> void iterateFromFirstWithConsumer(
      Function<byte[], K> keySerializer, Function<byte[], V> valueSerializer,
      BiPredicate<K, V> until, BiConsumer<K, V> kvConsumer, boolean useSnapshot) {
    Snapshot snapshot = null;
    try {
      final ReadOptions readOptions = newIteratorReadOptions();
      if (useSnapshot) {
        snapshot = rocksDBInstance.getSnapshot();
        readOptions.setSnapshot(snapshot);
      }
      iterateFromFirstWithConsumer(
          keySerializer,
          valueSerializer,
          until,
          kvConsumer,
          readOptions
      );
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
  }

  public <K, V> void iterateFromPrevKeyWithConsumer(byte[] from, Function<byte[], K> keySerializer,
      Function<byte[], V> valueSerializer, BiPredicate<K, V> until, BiConsumer<K, V> kvConsumer,
      boolean useSnapshot) {
    Snapshot snapshot = null;
    try {
      final ReadOptions readOptions = newIteratorReadOptions();
      if (useSnapshot) {
        snapshot = rocksDBInstance.getSnapshot();
        readOptions.setSnapshot(snapshot);
      }
      iterateFromPrevKeyWithConsumer(
          from,
          keySerializer,
          valueSerializer,
          until,
          kvConsumer,
          readOptions
      );
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
  }

  /**
   * 从头开始遍历集合，并对当前遍历到的KV进行映射，最后返回收集到的映射结果
   *
   * @param until 遍历终止条件
   * @param mapper 映射逻辑
   * @param readOptions 读选项
   * @param <T> 映射结果类型
   * @return 收集到的映射结果
   */
  public <K, V, T> Collection<T> iterateFromFirstWithMapper(
      Function<byte[], K> keySerializer, Function<byte[], V> valueSerializer,
      BiPredicate<K, V> until, BiFunction<K, V, T> mapper, ReadOptions readOptions) {
    final List<T> resultCollection = new LinkedList<>();
    try (final RocksIterator iterator = rocksDBInstance
        .newIterator(columnFamilyHandle, readOptions)) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        final K key = keySerializer.apply(iterator.key());
        final V value = valueSerializer.apply(iterator.value());
        if (until.test(key, value)) {
          break;
        } else {
          final T mappedValue = mapper.apply(key, value);
          resultCollection.add(mappedValue);
        }
      }
    }
    return resultCollection;
  }

  public <K, V, T> Collection<T> iterateFromFirstWithMapper(
      Function<byte[], K> keySerializer, Function<byte[], V> valueSerializer,
      BiPredicate<K, V> until, BiFunction<K, V, T> mapper, boolean useSnapshot) {
    Snapshot snapshot = null;
    try {
      final ReadOptions readOptions = newIteratorReadOptions();
      if (useSnapshot) {
        snapshot = rocksDBInstance.getSnapshot();
        readOptions.setSnapshot(snapshot);
      }
      return iterateFromFirstWithMapper(
          keySerializer,
          valueSerializer,
          until,
          mapper,
          readOptions
      );
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
  }

  public void compactRange() {
    try {
      rocksDBInstance.compactRange(this.columnFamilyHandle);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  private ReadOptions newIteratorReadOptions() {
    final ReadOptions readOptions = new ReadOptions();
    if (cfConfig.contains(CfConfigItems.ITERATOR_READAHEAD_SIZE)) {
      readOptions.setReadaheadSize(cfConfig.getLong(CfConfigItems.ITERATOR_READAHEAD_SIZE));
    }
    return readOptions;
  }
}

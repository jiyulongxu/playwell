package playwell.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.charset.Charset;

public class MurmurHash {

  public static final long JCOMMON_SEED = 1318007700;

  private final long seed;

  private final HashFunction byteArrayHasher = Hashing.murmur3_128((int) JCOMMON_SEED);

  public MurmurHash() {
    this(JCOMMON_SEED);
  }

  public MurmurHash(long seed) {
    this.seed = seed;
  }

  private long rotateLeft64(long x, int r) {
    return (x << r) | (x >>> (64 - r));
  }

  private long fmix(long k) {
    k ^= k >>> 33;
    k *= 0xff51afd7ed558ccdL;
    k ^= k >>> 33;
    k *= 0xc4ceb9fe1a85ec53L;
    k ^= k >>> 33;

    return k;
  }

  public byte[] hash(byte[] data) {
    HashCode hashCode = byteArrayHasher.hashBytes(data);

    return hashCode.asBytes();
  }

  public long hashToLong(byte[] data) {
    HashCode hashCode = byteArrayHasher.hashBytes(data);

    return hashCode.asLong();
  }

  public long hash(String str) {
    return hashToLong(hash(str.getBytes(Charset.forName("UTF-8"))));
  }

  public long hash(long data) {
    long c1 = 0x87c37b91114253d5L;
    long c2 = 0x4cf5ad432745937fL;

    long h1 = seed, h2 = seed;

    long k1 = data;
    k1 *= c1;
    k1 = rotateLeft64(k1, 31);
    k1 *= c2;
    h1 ^= k1;

    h1 ^= 8;
    h2 ^= 8;

    h1 += h2;
    h2 += h1;

    return (fmix(h1) + fmix(h2));
  }
}

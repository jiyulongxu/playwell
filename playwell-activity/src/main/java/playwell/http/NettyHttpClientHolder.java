package playwell.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import playwell.common.EasyMap;

/**
 * NettyHttpClientHolder持有系统中通用的Http客户端，供所有的HttpEventBus使用
 */
public class NettyHttpClientHolder implements Closeable {

  private static final NettyHttpClientHolder INSTANCE = new NettyHttpClientHolder();

  private AsyncHttpClient asyncHttpClient = null;

  private NettyHttpClientHolder() {

  }

  public static NettyHttpClientHolder getInstance() {
    return INSTANCE;
  }

  public synchronized void init(EasyMap configuration) {
    if (asyncHttpClient != null) {
      return;
    }

    final String eventLoop = configuration.getString(ConfigItems.EVENT_LOOP);
    final int nThreads = configuration.getInt(ConfigItems.N_THREADS,
        Runtime.getRuntime().availableProcessors());
    final EventLoopGroup eventLoopGroup;
    if (EventLoopTypes.EPOLL.equals(eventLoop)) {
      eventLoopGroup = new EpollEventLoopGroup(nThreads);
    } else if (EventLoopTypes.NIO.equals(eventLoop)) {
      eventLoopGroup = new NioEventLoopGroup(nThreads);
    } else {
      throw new RuntimeException(String.format(
          "Could not support the netty event loop type: %s", eventLoop));
    }

    final int connectTimeout = configuration.getInt(ConfigItems.CONNECT_TIMEOUT);
    final int requestTimeout = configuration.getInt(ConfigItems.REQUEST_TIMEOUT);
    final int readTimeout = configuration.getInt(ConfigItems.READ_TIMEOUT);

    final AsyncHttpClientConfig asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
        .setEventLoopGroup(eventLoopGroup)
        .setConnectTimeout(connectTimeout)
        .setRequestTimeout(requestTimeout)
        .setReadTimeout(readTimeout)
        .build();

    this.asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig);
  }

  public Optional<AsyncHttpClient> getClient() {
    return Optional.ofNullable(asyncHttpClient);
  }

  @Override
  public void close() throws IOException {
    asyncHttpClient.close();
  }

  interface ConfigItems {

    String EVENT_LOOP = "event_loop";

    String N_THREADS = "n_threads";

    String CONNECT_TIMEOUT = "connect_timeout";

    String REQUEST_TIMEOUT = "request_timeout";

    String READ_TIMEOUT = "read_timeout";
  }

  interface EventLoopTypes {

    String EPOLL = "epoll";

    String NIO = "nio";
  }
}

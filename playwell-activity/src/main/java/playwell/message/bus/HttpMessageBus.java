package playwell.message.bus;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.http.CommonHeaders;
import playwell.http.HttpServiceManager;
import playwell.http.NettyHttpClientHolder;
import playwell.message.Message;
import playwell.message.bus.codec.JSONArrayMessageCodec;
import playwell.message.bus.codec.JSONObjectMessageCodec;
import spark.Service;

/**
 * HttpEventBus提供了从Http通道读写消息的功能 HttpEventBus持有一个URL地址
 *
 * 对于读取消息： 如果本地的HttpServiceManager中持有与URL相同地址和端口的Service，那么将在该Service中注册路由，
 * 请求到该路由的消息会保存到一个缓冲区中，系统从该缓冲区读取消息。
 *
 * 对于写消息： 如果为EventBus配置了消息合并，那么将会把消息拼接成一个JSON数组，然后执行请求 否则将为每个消息执行异步请求。
 */
public class HttpMessageBus extends BufferedMessageBus {

  private static final Logger logger = LogManager.getLogger(HttpMessageBus.class);

  private static final JSONObjectMessageCodec messageCodec = new JSONObjectMessageCodec();

  private static final JSONArrayMessageCodec sequenceMessageCodec = new JSONArrayMessageCodec();

  private static final AsyncCompletionHandler<Response> responseHandler = new AsyncCompletionHandler<Response>() {

    @Override
    public Response onCompleted(Response response) {
      return response;
    }

    @Override
    public void onThrowable(Throwable t) {
      logger.error(t.getMessage(), t);
    }
  };

  private String url;

  private String host;

  private int port;

  private String path;

  private boolean combine;

  private ConcurrentLinkedQueueMessageBus buffer;

  private boolean isServer = false;

  @Override
  protected void initMessageBus(EasyMap configuration) {
    try {
      this.url = configuration.getString(ConfigItems.URL);
      URL urlObj = new URL(this.url);
      this.host = urlObj.getHost();
      this.port = urlObj.getPort();
      this.path = urlObj.getPath();
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    this.combine = configuration.getBoolean(ConfigItems.COMBINE, true);

    // 注册Route
    final HttpServiceManager httpServiceManager = HttpServiceManager.getInstance();
    if (httpServiceManager.isInited()) {
      Optional<Service> serviceOptional = httpServiceManager.getService(host, port);
      serviceOptional.ifPresent(service -> {
        this.buffer = new ConcurrentLinkedQueueMessageBus();
        this.buffer.initMessageBus(configuration.getSubArguments(ConfigItems.BUFFER));
        service.post(path, (request, response) -> {
          response.header(
              CommonHeaders.JSON_CONTENT_TYPE.getName(),
              CommonHeaders.JSON_CONTENT_TYPE.getValue()
          );

          String body = request.body();
          if (StringUtils.isEmpty(body)) {
            response.status(400);
            return Result.failWithCodeAndMessage(
                "bad_request",
                "There is no message in the request body"
            ).toJSONString();
          }

          body = StringUtils.strip(body);
          if (body.startsWith("{")) {
            buffer.write(messageCodec.decode(body));
            response.status(200);
            return Result.ok().toJSONString();
          } else if (body.startsWith("[")) {
            buffer.write(sequenceMessageCodec.decode(body));
            response.status(200);
            return Result.ok().toJSONString();
          } else {
            response.status(400);
            return Result.failWithCodeAndMessage(
                "bad_request",
                "The http event bus only accept json or json array"
            );
          }
        });

        this.isServer = true;
      });
    }
  }

  @Override
  protected void directWrite(Collection<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return;
    }

    // 如果进程本身就是http server，那么可以直接写入buffer当中
    if (this.isServer) {
      this.buffer.write(messages);
      return;
    }

    final NettyHttpClientHolder httpClientHolder = NettyHttpClientHolder.getInstance();
    final Optional<AsyncHttpClient> clientOptional = httpClientHolder.getClient();
    if (!clientOptional.isPresent()) {
      throw new RuntimeException("There is no available http client for the HttpEventBus");
    }

    final AsyncHttpClient httpClient = clientOptional.get();

    if (combine) {
      httpClient
          .preparePost(url)
          .setBody((String) sequenceMessageCodec.encode(messages))
          .addHeader("Content-Type", "application/json;charset=utf-8")
          .execute(responseHandler);
    } else {
      for (Message message : messages) {
        httpClient
            .preparePost(url)
            .setBody((String) messageCodec.encode(message))
            .addHeader("Content-Type", "application/json;charset=utf-8")
            .execute(responseHandler);
      }
    }
  }

  @Override
  public Collection<Message> read(int maxFetchNum) {
    if (buffer == null) {
      throw new RuntimeException(String.format(
          "Could not read from the HttpEventBus: '%s', There is no http service",
          name()
      ));
    }
    return buffer.read(maxFetchNum);
  }

  @Override
  public int readWithConsumer(int maxFetchNum, Consumer<Message> eventConsumer) {
    if (buffer == null) {
      throw new RuntimeException(String.format(
          "Could not read from the HttpEventBus: '%s', There is no http service",
          name()
      ));
    }
    return buffer.readWithConsumer(maxFetchNum, eventConsumer);
  }

  interface ConfigItems {

    String URL = "url";

    String COMBINE = "combine";

    String BUFFER = "buffer";
  }
}

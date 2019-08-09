package playwell.baas.common;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Response;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.http.NettyHttpClientHolder;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;

/**
 * Http request helper
 */
public final class HttpRequests {

  private static final Logger logger = LogManager.getLogger(HttpRequests.class);

  private static final Logger httpRequestLogger = LogManager.getLogger("http_request");

  private HttpRequests() {

  }

  public static Collection<ServiceResponseMessage> makeSingleRequests(
      Collection<ServiceRequestMessage> messages,
      int maxRetry,
      Function<ServiceRequestMessage, String> urlExtractor,
      Function<ServiceRequestMessage, Map<String, String>> headersExtractor,
      Function<ServiceRequestMessage, String> bodyExtractor,
      Function<Response, Result> responseMapper) {
    final List<ServiceResponseMessage> results = new ArrayList<>(messages.size());

    int retryCount = 0;
    do {
      // 没有待处理的消息，无需继续重试了
      if (CollectionUtils.isEmpty(messages)) {
        return results;
      }
      final List<ServiceRequestMessage> errorRequests = new LinkedList<>();
      makeRequests(
          messages,
          urlExtractor,
          headersExtractor,
          bodyExtractor,
          responseMapper,
          results,
          errorRequests
      );
      messages = errorRequests;
    } while (retryCount++ < maxRetry);

    // 处理重试后依然失败的请求
    if (CollectionUtils.isNotEmpty(messages)) {
      results.addAll(messages
          .stream()
          .map(message ->
              new ServiceResponseMessage(
                  CachedTimestamp.nowMilliseconds(),
                  message,
                  Result.failWithCodeAndMessage(
                      ErrorCodes.ERROR, "Request error")
              ))
          .collect(Collectors.toList()));
    }

    return results;
  }

  private static void makeRequests(
      Collection<ServiceRequestMessage> messages,
      Function<ServiceRequestMessage, String> urlExtractor,
      Function<ServiceRequestMessage, Map<String, String>> headersExtractor,
      Function<ServiceRequestMessage, String> bodyExtractor,
      Function<Response, Result> responseMapper,
      List<ServiceResponseMessage> results,
      List<ServiceRequestMessage> errorRequests) {

    Optional<AsyncHttpClient> httpClientOptional = NettyHttpClientHolder.getInstance().getClient();
    if (!httpClientOptional.isPresent()) {
      throw new RuntimeException("There is no available http client to use!");
    }
    AsyncHttpClient httpClient = httpClientOptional.get();

    final List<HttpServiceResponseFuture> futures = new LinkedList<>();
    for (ServiceRequestMessage message : messages) {
      futures.add(makeRequest(
          httpClient,
          responseMapper,
          urlExtractor,
          headersExtractor,
          bodyExtractor,
          message
      ));
    }

    for (HttpServiceResponseFuture future : futures) {
      try {
        final ServiceResponseMessage responseMessage = future.get();
        results.add(responseMessage);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        errorRequests.add(future.getServiceRequestMessage());
      }
    }
  }

  private static HttpServiceResponseFuture makeRequest(
      AsyncHttpClient asyncHttpClient,
      Function<Response, Result> responseMapper,
      Function<ServiceRequestMessage, String> urlExtractor,
      Function<ServiceRequestMessage, Map<String, String>> headersExtractor,
      Function<ServiceRequestMessage, String> bodyExtractor,
      ServiceRequestMessage requestMessage
  ) {
    final String url = urlExtractor.apply(requestMessage);
    final Map<String, String> headers = headersExtractor.apply(requestMessage);
    final String body = bodyExtractor.apply(requestMessage);

    BoundRequestBuilder requestBuilder = asyncHttpClient.preparePost(url);
    if (MapUtils.isNotEmpty(headers)) {
      headers.forEach(requestBuilder::addHeader);
    }
    requestBuilder.setBody(body);

    if (httpRequestLogger.isDebugEnabled()) {
      httpRequestLogger.debug(JSON.toJSONString(ImmutableMap.of(
          "url", url,
          "headers", headers,
          "body", body
      )));
    }

    return new HttpServiceResponseFuture(
        requestMessage, requestBuilder.execute(), responseMapper);
  }

  public static Collection<ServiceResponseMessage> makeCombineRequests(
      String url,
      Map<String, String> headers,
      int maxRetry,
      Collection<ServiceRequestMessage> requestMessages,
      Function<Collection<ServiceRequestMessage>, String> bodyGenerator,
      Function<Response, Collection<ServiceResponseMessage>> responseGenerator) {
    final Optional<AsyncHttpClient> httpClientOptional = NettyHttpClientHolder.getInstance()
        .getClient();
    if (!httpClientOptional.isPresent()) {
      throw new RuntimeException("There is no available http client to use!");
    }

    final AsyncHttpClient httpClient = httpClientOptional.get();
    int retryCount = 0;

    do {
      final BoundRequestBuilder requestBuilder = httpClient.preparePost(url);

      // 添加Header
      if (MapUtils.isNotEmpty(headers)) {
        headers.forEach(requestBuilder::addHeader);
      }
      // 请求体
      final String body = bodyGenerator.apply(requestMessages);
      requestBuilder.setBody(body);

      if (httpRequestLogger.isDebugEnabled()) {
        httpRequestLogger.debug(JSON.toJSONString(ImmutableMap.of(
            "url", url,
            "headers", headers,
            "body", body
        )));
      }

      // 获取响应
      try {
        final Future<Response> responseFuture = requestBuilder.execute();
        return responseGenerator.apply(responseFuture.get());
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    } while (retryCount++ < maxRetry);

    return requestMessages.stream().map(request -> new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(),
        request,
        Result.failWithCodeAndMessage(
            ErrorCodes.ERROR,
            "Request Error"
        )
    )).collect(Collectors.toList());
  }

  interface ErrorCodes {

    String ERROR = "error";
  }
}

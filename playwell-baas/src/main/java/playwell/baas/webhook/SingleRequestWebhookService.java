package playwell.baas.webhook;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.Response;
import playwell.baas.common.HttpRequests;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.service.PlaywellService;
import playwell.util.ModelUtils;


/**
 * SingleRequestWebhookService会将每个ServiceRequestMessage转化为一个单独的Http请求
 * 如果发生了传输层面的错误，会进行重试，并将最终的请求结果映射为ServiceResponseMessage。
 */
public class SingleRequestWebhookService implements PlaywellService {

  private static final Logger logger = LogManager.getLogger(SingleRequestWebhookService.class);

  // Result Mapper
  private final Function<Response, Result> SINGLE_RESPONSE_MAPPER = response -> {
    final String responseBody = response.getResponseBody(Charset.forName("UTF-8"));
    try {
      final JSONObject jsonBody = JSONObject.parseObject(responseBody);
      return Result.fromMap(new EasyMap(jsonBody.getInnerMap()));
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return Result.failWithCodeAndMessage(ErrorCodes.INVALID_RESPONSE, "");
    }
  };

  // Http body extractor
  private final Function<ServiceRequestMessage, String> SINGLE_BODY_EXTRACTOR = requestMessage -> {
    final EasyMap args = requestMessage.getMapArgs();
    return JSON.toJSONString(args.get(ArgNames.BODY, ""));
  };

  // 请求URL
  private String url;

  // URL extractor
  private final Function<ServiceRequestMessage, String> SINGLE_URL_EXTRACTOR =
      requestMessage -> {
        final EasyMap args = requestMessage.getMapArgs();
        return args.getString(ArgNames.URL, url);
      };

  // 最大重试次数
  private int maxRetry;

  // 默认Http headers
  private Map<String, String> defaultHttpHeaders;

  // Http headers extractor
  private final Function<ServiceRequestMessage, Map<String, String>> SINGLE_HEADERS_EXTRACTOR =
      requestMessage -> {
        final EasyMap args = requestMessage.getMapArgs();
        Map<String, String> requestHeaders = ModelUtils.mapValueToString(
            args.getSubArguments(ArgNames.HEADERS).toMap());
        final Map<String, String> headers;
        if (MapUtils.isEmpty(defaultHttpHeaders)) {
          headers = requestHeaders;
        } else {
          headers = new HashMap<>(defaultHttpHeaders);
          headers.putAll(requestHeaders);
        }
        return headers;
      };

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.url = configuration.getString(
        ConfigItems.URL, "");
    this.maxRetry = configuration.getInt(
        ConfigItems.MAX_RETRY, ConfigItems.DEFAULT_MAX_RETRY);
    this.defaultHttpHeaders = ModelUtils.mapValueToString(
        configuration.getSubArguments(ConfigItems.DEFAULT_HTTP_HEADERS).toMap());
  }

  @Override
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return Collections.emptyList();
    }

    return HttpRequests.makeSingleRequests(
        messages,
        maxRetry,
        SINGLE_URL_EXTRACTOR,
        SINGLE_HEADERS_EXTRACTOR,
        SINGLE_BODY_EXTRACTOR,
        SINGLE_RESPONSE_MAPPER
    );
  }

  interface ConfigItems {

    String URL = "url";

    String MAX_RETRY = "max_retry";
    int DEFAULT_MAX_RETRY = 0;

    String DEFAULT_HTTP_HEADERS = "default_http_headers";
  }

  interface ArgNames {

    String URL = "url";

    String HEADERS = "headers";

    String BODY = "body";
  }

  interface ErrorCodes {

    String INVALID_RESPONSE = "invalid_response";
  }
}

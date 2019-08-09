package playwell.baas.webhook;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.baas.common.HttpRequests;
import playwell.clock.CachedTimestamp;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;
import playwell.service.PlaywellService;
import playwell.util.ModelUtils;

/**
 * CombineRequestsWebhookService 会将指定数目的ServiceRequestMessage合并成一个json数组进行请求，
 * 接口所返回的响应也需要是一个json数组，包括每个请求的结果
 *
 * 请求：
 *
 * <pre>
 *   [
 *      {"activity": 1, "did": "1", "action": "send_sms", "args": {...}},
 *      {"activity": 1, "did": "2", "action": "send_sms", "args": {...}},
 *      {"activity": 1, "did": "3", "action": "send_sms", "args": {...}},
 *   ]
 * </pre>
 *
 * 响应：
 *
 * 每条请求对应着一个结果
 *
 * <pre>
 *   [
 *     {"activity": 1, "did": "1", "action": "send_sms", "result": {"status": "fail", "error_code": "invalid_tpl", "msg": "xxxx", "data": {...}}},
 *     {"activity": 1, "did": "2", "action": "send_sms", "result": {"status": "ok", "data": {...}}},
 *     {"activity": 1, "did": "3", "action": "send_sms", "result": {"status": "ok", "data": {...}}}
 *   ]
 * </pre>
 *
 * 所有的请求都应用一个结果
 *
 * <pre>
 *   {"status": "fail", "error_code": "service_error", "msg": "Service error!"}
 * </pre>
 *
 * 只包含错误，其它的默认成功：
 *
 * <pre>
 *   [
 *      {"activity": 1, "did": "1", "action": "send_sms", "result": {"status": "fail", "error_code": "invalid_tpl"}}
 *   ]
 * </pre>
 */
public class CombineRequestsWebhookService implements PlaywellService {

  private static final Logger logger = LogManager.getLogger(CombineRequestsWebhookService.class);

  private String url;

  private int maxRetry;

  private Map<String, String> defaultHttpHeaders;

  @Override
  public void init(Object config) {
    final EasyMap configuration = (EasyMap) config;
    this.url = configuration.getString(ConfigItems.URL);
    this.maxRetry = configuration.getInt(ConfigItems.MAX_RETRY, ConfigItems.DEFAULT_MAX_RETRY);
    this.defaultHttpHeaders = ModelUtils.mapValueToString(
        configuration.getSubArguments(ConfigItems.DEFAULT_HTTP_HEADERS).toMap());
  }

  @Override
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return Collections.emptyList();
    }

    return HttpRequests.makeCombineRequests(
        this.url,
        defaultHttpHeaders,
        maxRetry,
        messages,
        requests -> {
          final JSONArray jsonArray = new JSONArray();
          for (ServiceRequestMessage message : messages) {
            final JSONObject reqObj = new JSONObject(ImmutableMap.of(
                DataFields.ACTIVITY, message.getActivityId(),
                DataFields.DOMAIN_ID, message.getDomainId(),
                DataFields.ACTION, message.getAction(),
                DataFields.ARGS, message.getArgs()
            ));
            jsonArray.add(reqObj);
          }
          return jsonArray.toJSONString();
        },
        response -> {
          try {
            final Object parsedResult = JSON
                .parse(response.getResponseBody(Charset.forName("UTF-8")));
            if (parsedResult instanceof JSONObject) {
              // 所有的请求应用一个返回结果
              final Result result = Result
                  .fromMap(new EasyMap(((JSONObject) parsedResult).getInnerMap()));
              return messages.stream().map(message -> new ServiceResponseMessage(
                  CachedTimestamp.nowMilliseconds(),
                  message,
                  result
              )).collect(Collectors.toList());
            } else if (parsedResult instanceof JSONArray) {
              final JSONArray jsonArray = (JSONArray) parsedResult;
              final Map<Triple<Integer, String, String>, Result> responseResults = new HashMap<>(
                  jsonArray.size());
              for (int i = 0; i < jsonArray.size(); i++) {
                final JSONObject jsonObject = jsonArray.getJSONObject(i);
                final int activityId = jsonObject.getInteger(DataFields.ACTIVITY);
                final String domainId = jsonObject.getString(DataFields.DOMAIN_ID);
                final String action = jsonObject.getString(DataFields.ACTION);
                final Result result = Result
                    .fromMap(new EasyMap(jsonObject.getJSONObject(DataFields.RESULT)));
                responseResults.put(Triple.of(activityId, domainId, action), result);
              }

              final List<ServiceResponseMessage> allResponse = new ArrayList<>(messages.size());
              for (ServiceRequestMessage message : messages) {
                Result result = responseResults.get(
                    Triple.of(message.getActivityId(), message.getDomainId(), message.getAction()));
                if (result == null) {
                  result = Result.ok();
                }
                allResponse.add(new ServiceResponseMessage(
                    CachedTimestamp.nowMilliseconds(), message, result));
              }
              return allResponse;
            } else {
              logger.error("Could not parse the response: " + parsedResult);
              return buildErrorResponse(
                  messages,
                  ErrorCodes.INVALID_RESPONSE,
                  ""
              );
            }
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(
                messages,
                ErrorCodes.INVALID_RESPONSE,
                ""
            );
          }
        }
    );
  }

  private Collection<ServiceResponseMessage> buildErrorResponse(
      Collection<ServiceRequestMessage> requestMessages, String errorCode, String msg) {
    return requestMessages.stream().map(reqMsg -> new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(),
        reqMsg,
        Result.failWithCodeAndMessage(errorCode, msg)
    )).collect(Collectors.toList());
  }

  interface DataFields {

    String ACTIVITY = "activity";

    String DOMAIN_ID = "did";

    String ACTION = "action";

    String ARGS = "args";

    String RESULT = "result";
  }

  interface ConfigItems {

    String URL = "url";

    String MAX_RETRY = "max_retry";
    int DEFAULT_MAX_RETRY = 0;

    String DEFAULT_HTTP_HEADERS = "default_http_headers";
  }

  interface ErrorCodes {

    String INVALID_RESPONSE = "invalid_response";
  }
}

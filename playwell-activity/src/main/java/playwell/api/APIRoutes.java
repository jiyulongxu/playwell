package playwell.api;

import com.alibaba.fastjson.JSONObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.EasyMap;
import playwell.common.Result;
import playwell.http.CommonHeaders;
import playwell.util.validate.Field;
import playwell.util.validate.InvalidFieldException;
import spark.Request;
import spark.Response;
import spark.Service;

/**
 * API Routes
 */
public abstract class APIRoutes {

  private static final Logger logger = LogManager.getLogger("api");

  /**
   * 注册API路由到指定的Http Service
   *
   * @param service Http Service
   */
  protected abstract void registerRoutes(Service service);

  protected String getResponseWithQueryParam(
      Request request, Response response, Field[] fields, Function<EasyMap, Result> handler) {
    return response(
        request,
        response,
        fields,
        this::expandQueryParamMap,
        handler
    );
  }

  protected String getResponseWithPathParam(
      Request request, Response response, Field[] fields, Function<EasyMap, Result> handler) {
    return response(
        request,
        response,
        fields,
        req -> new EasyMap(req.params().entrySet().stream()
            .map(entry -> Pair.of(entry.getKey().substring(1), entry.getValue()))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue))),
        handler
    );
  }

  protected String deleteResponse(
      Request request, Response response, Field[] fields, Function<EasyMap, Result> handler) {
    return response(
        request,
        response,
        fields,
        this::expandQueryParamMap,
        handler
    );
  }

  protected String postResponse(
      Request request, Response response, Field[] fields, Function<EasyMap, Result> handler) {
    return response(
        request,
        response,
        fields,
        req -> new EasyMap(JSONObject.parseObject(req.body())),
        handler
    );
  }

  protected String response(
      Request request,
      Response response,
      Field[] fields,
      Function<Request, EasyMap> argGenerator,
      Function<EasyMap, Result> handler) {

    response.header(
        CommonHeaders.JSON_CONTENT_TYPE.getName(),
        CommonHeaders.JSON_CONTENT_TYPE.getValue()
    );

    try {
      EasyMap requestBody = argGenerator.apply(request);

      // 验证请求字段是否合法
      if (!ArrayUtils.isEmpty(fields)) {
        try {
          Map<String, Object> data = new HashMap<>(requestBody.toMap());
          for (Field field : fields) {
            data.put(
                field.getName(),
                field.validate(requestBody.get(field.getName(), null))
            );
          }

          requestBody = new EasyMap(data);
        } catch (InvalidFieldException e) {
          response.status(400);
          return Result.failWithCodeAndMessage(
              "bad_request", e.getMessage()).toJSONString();
        }
      }

      return response(response, handler.apply(requestBody));
    } catch (Exception e) {
      response.status(500);
      logger.error(e.getMessage(), e);
      return Result.failWithCodeAndMessage(
          "server_error", e.getMessage()).toJSONString();
    }
  }

  protected String response(Response response, Result result) {
    response.header(
        CommonHeaders.JSON_CONTENT_TYPE.getName(),
        CommonHeaders.JSON_CONTENT_TYPE.getValue()
    );
    if (result.isOk()) {
      response.status(200);
    } else {
      response.status(400);
    }
    return result.toJSONString();
  }

  private EasyMap expandQueryParamMap(Request request) {
    Map<String, String[]> params = request.queryMap().toMap();
    if (MapUtils.isNotEmpty(params)) {
      Map<String, Object> data = new HashMap<>(params.size());
      for (Map.Entry<String, String[]> entry : params.entrySet()) {
        final String key = entry.getKey();
        final String[] values = entry.getValue();
        if (ArrayUtils.isEmpty(values)) {
          continue;
        }

        if (values.length == 1) {
          data.put(key, values[0]);
        } else {
          data.put(key, Arrays.asList(values));
        }
      }
      return new EasyMap(data);
    }
    return new EasyMap();
  }
}

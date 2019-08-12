package playwell.service;

import java.util.Collection;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;

/**
 * 该Service用于处理单个请求
 */
public abstract class SingleRequestService extends BasePlaywellService {

  private static final Logger logger = LogManager.getLogger(SingleRequestService.class);

  @Override
  protected Collection<Result> handleRequests(Collection<ServiceRequestMessage> messages) {
    return messages.stream().map(requestMessage -> {
      try {
        return handleRequest(requestMessage);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        return Result.failWithCodeAndMessage(
            "sys_error",
            e.getMessage()
        );
      }
    }).collect(Collectors.toList());
  }

  protected abstract Result handleRequest(ServiceRequestMessage requestMessage);
}

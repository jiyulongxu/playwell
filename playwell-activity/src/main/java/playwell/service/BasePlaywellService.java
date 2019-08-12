package playwell.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;

public abstract class BasePlaywellService implements PlaywellService {

  private static final Logger logger = LogManager.getLogger(BasePlaywellService.class);

  protected BasePlaywellService() {

  }

  @Override
  public void init(Object config) {

  }

  @Override
  public Collection<ServiceResponseMessage> handle(Collection<ServiceRequestMessage> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return Collections.emptyList();
    }

    try {
      final Collection<Result> results = handleRequests(messages);
      if (results.size() != messages.size()) {
        throw new RuntimeException(String.format(
            "The results size: %d is not match with the requests size: %d",
            results.size(),
            messages.size()
        ));
      }
      final List<ServiceResponseMessage> responseMessages = new ArrayList<>(messages.size());
      final Iterator<ServiceRequestMessage> requestIter = messages.iterator();
      final Iterator<Result> resultIter = results.iterator();
      while (requestIter.hasNext()) {
        final ServiceRequestMessage requestMessage = requestIter.next();
        final Result result = resultIter.next();
        responseMessages.add(new ServiceResponseMessage(
            CachedTimestamp.nowMilliseconds(),
            requestMessage,
            result
        ));
      }
      return responseMessages;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return messages.stream().map(req -> new ServiceResponseMessage(
          CachedTimestamp.nowMilliseconds(),
          req,
          Result.failWithCodeAndMessage(
              "sys_error",
              e.getMessage()
          )
      )).collect(Collectors.toList());
    }
  }

  protected abstract Collection<Result> handleRequests(Collection<ServiceRequestMessage> messages);
}

package playwell.service;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;

public class UpperCaseService extends BasePlaywellService {

  @Override
  protected Collection<Result> handleRequests(Collection<ServiceRequestMessage> messages) {
    return messages.stream().map(
        msg -> Result.okWithData(Collections.singletonMap(
            "text", ((String) msg.getArgs()).toUpperCase())
        )).collect(Collectors.toList());
  }
}

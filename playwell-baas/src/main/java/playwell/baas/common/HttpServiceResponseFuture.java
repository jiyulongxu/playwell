package playwell.baas.common;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import playwell.clock.CachedTimestamp;
import playwell.common.Result;
import playwell.message.ServiceRequestMessage;
import playwell.message.ServiceResponseMessage;

/**
 * HttpServiceRequestFuture
 */
public class HttpServiceResponseFuture implements Future<ServiceResponseMessage> {

  private final ServiceRequestMessage serviceRequestMessage;

  private final ListenableFuture<Response> responseFuture;

  private final Function<Response, Result> responseMapper;

  public HttpServiceResponseFuture(
      ServiceRequestMessage serviceRequestMessage,
      ListenableFuture<Response> responseFuture,
      Function<Response, Result> responseMapper) {
    this.serviceRequestMessage = serviceRequestMessage;
    this.responseFuture = responseFuture;
    this.responseMapper = responseMapper;
  }

  public ServiceRequestMessage getServiceRequestMessage() {
    return serviceRequestMessage;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return responseFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return responseFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return responseFuture.isDone();
  }

  @Override
  public ServiceResponseMessage get() throws InterruptedException, ExecutionException {
    final Response response = responseFuture.get();
    final Result result = responseMapper.apply(response);
    return new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(), serviceRequestMessage, result);
  }

  @Override
  public ServiceResponseMessage get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    final Response response = responseFuture.get(timeout, unit);
    final Result result = responseMapper.apply(response);
    return new ServiceResponseMessage(
        CachedTimestamp.nowMilliseconds(), serviceRequestMessage, result);
  }
}




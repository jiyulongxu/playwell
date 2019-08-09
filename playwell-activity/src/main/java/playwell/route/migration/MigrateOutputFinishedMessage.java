package playwell.route.migration;

import java.util.Collections;
import playwell.clock.CachedTimestamp;
import playwell.message.Message;

/**
 * 该消息会在OutputTask执行完毕之后发出， 用于通知InputTask这是最后一个消息
 */
public class MigrateOutputFinishedMessage extends Message {

  public static final String TYPE = "migration_eof";

  public MigrateOutputFinishedMessage(String outputService, String inputService) {
    super(
        TYPE,
        outputService,
        inputService,
        Collections.emptyMap(),
        CachedTimestamp.nowMilliseconds()
    );
  }
}

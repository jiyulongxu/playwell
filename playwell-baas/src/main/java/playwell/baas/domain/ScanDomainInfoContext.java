package playwell.baas.domain;


/**
 * ScanDomainInfoContext
 */
public interface ScanDomainInfoContext {

  /**
   * 返回当前迭代到的DomainInfo
   *
   * @return The current iterate DomainInfo object
   */
  DomainInfo currentDomainInfo();

  /**
   * 返回当前扫描总数
   *
   * @return Already scanned num
   */
  int alreadyScannedNum();

  /**
   * 移除当前迭代到的DomainInfo
   */
  void remove();
}

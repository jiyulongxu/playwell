package playwell.http;

/**
 * 常用的Http Header常量
 */
public enum CommonHeaders {

  JSON_CONTENT_TYPE(
      "Content-Type",
      "application/json;charset=utf-8"
  ),
  ;

  private final String name;

  private final String value;

  CommonHeaders(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return this.name;
  }

  public String getValue() {
    return this.value;
  }
}

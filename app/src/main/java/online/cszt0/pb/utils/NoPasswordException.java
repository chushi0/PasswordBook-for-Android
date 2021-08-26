package online.cszt0.pb.utils;

/**
 * 在需要用户输入密码时，用户没有输入密码，则会抛出该异常。
 * <p>
 * 该方法通常用于在使用 RxJava 库的流程中，提前将流程中断。
 *
 * @see DataImportHelper
 * @see DataExportHelper
 */
public class NoPasswordException extends Exception {
}

package aces.webctrl.centralizer.addon;
public class Utility {
  /**
   * Specifies how long to wait (in milliseconds) while an asynchronous database task completes.
   */
  public final static long ASYNC_TIMEOUT = 30000;
  /**
   * Reverses the order and XORs each character with 4.
   */
  public static void obfuscate(char[] arr){
    char c;
    for (int i=0,j=arr.length-1;i<=j;++i,--j){
      if (i==j){
        arr[i]^=4;
      }else{
        c = (char)(arr[j]^4);
        arr[j] = (char)(arr[i]^4);
        arr[i] = c;
      }
    }
  }
  /**
   * Converts a character array into a byte array.
   */
  public static byte[] toBytes(char[] arr){
    return java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(arr)).array();
  }
}
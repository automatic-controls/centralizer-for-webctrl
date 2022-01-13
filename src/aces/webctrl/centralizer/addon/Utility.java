package aces.webctrl.centralizer.addon;
import java.io.*;
public class Utility {
  /**
   * Loads all bytes from the given resource and convert to a {@code UTF-8} string.
   * @return the {@code UTF-8} string representing the given resource.
   */
  public static String loadResourceAsString(Class<?> c, String name) throws Exception {
    byte[] arr;
    try(
      BufferedInputStream s = new BufferedInputStream(c.getClassLoader().getResourceAsStream(name));
    ){
      arr = s.readAllBytes();
    }
    return new String(arr, java.nio.charset.StandardCharsets.UTF_8);
  }
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
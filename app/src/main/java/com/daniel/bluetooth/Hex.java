package com.daniel.bluetooth;

/** HEX CLASS - HELPER CLASS THAT CONVERTS STRING TO HEX AND BACK */
public class Hex {
    final private static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};


    /** CONVERTS A STRING TO A BYTE ARRAY (HEX) */
    public static byte[] stringToHex(String s) {
        char[] buffer = s.toCharArray();
        byte[] b = new byte[buffer.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) buffer[i];
        }

        return b;
    }


    /** CONVERTS A BYTE ARRAY (HEX) TO A STRING */
    public static String hexToString(byte[] bytes) {
        return new String(bytes);
    }
}

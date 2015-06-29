package com.daniel.bluetooth;

/** HEX CLASS - HELPER CLASS THAT CONVERTS STRING TO HEX AND BACK */
public class Hex {
    final private static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};


    /** CONVERTS A STRING TO A BYTE ARRAY (HEX) */
    public static byte[] stringToHex(String s) {
        int len = s.length();
        byte[] data = new byte[len/2];

        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }


    /** CONVERTS A BYTE ARRAY (HEX) TO A STRING */
    public static String hexToString(byte[] bytes) {
        char[] hexChars = new char[bytes.length*2];
        int v;

        for(int j=0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v>>>4];
            hexChars[j*2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }
}

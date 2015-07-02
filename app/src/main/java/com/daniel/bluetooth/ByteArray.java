package com.daniel.bluetooth;


public class ByteArray {
    private byte[] _bytes;
    private byte _delimiter;

    public ByteArray() {
        _bytes = null;
        _delimiter = 0x00;
    }


    /** APPENDS THE GIVEN BYTE TO THE REAR OF THE ARRAY */
    public void add(byte b) {
        if (_bytes == null) {
            _bytes = new byte[1];
            _bytes[0] = b;
        } else {
            byte[] old_bytes = _bytes;
            _bytes = new byte[old_bytes.length+1];

            int i;
            for(i=0; i<old_bytes.length; i++)
                _bytes[i] = old_bytes[i];

            _bytes[i] = b;
        }
    }


    /** APPENDS THE GIVEN BYTE ARRAY TO THE END OF THE BYTE ARRAY */
    public void add(byte[] add_bytes) {
        if(_bytes == null){
            _bytes = new byte[add_bytes.length];
            for(int i=0; i<add_bytes.length; i++)
                _bytes[i] = add_bytes[i];
        } else {
            byte[] old_bytes = _bytes;
            _bytes = new byte[_bytes.length + add_bytes.length];

            int i;
            for(i=0; i<old_bytes.length; i++)
                _bytes[i] = old_bytes[i];

            for(int x=0; x<add_bytes.length; i++,x++)
                _bytes[i] = add_bytes[x];
        }
    }


    /** SETS THE DELIMITER */
    public void setDelimiter(byte delimiter) {
        _delimiter = delimiter;
    }


    /** RETURNS AND DELETES ALL THE WAY UNTIL THE DELIMITER
     *   IF DELIMITER DOESN'T EXIST OR IS NOT SET RETURNS NULL */
    public byte[] removeUntilDelimiter() {
        if(_delimiter == 0x00)
            return null;

        int i;
        for(i=0; i<_bytes.length; i++) {
            if(_bytes[i] == _delimiter) {
                byte[] old_bytes = _bytes;
                _bytes = new byte[old_bytes.length-i];
                byte[] send_bytes = new byte[i+1];

                int x;
                for(x=0; x < i+1; x++)
                    send_bytes[x] = old_bytes[x];

                for(x=0,++i;i<old_bytes.length; i++)
                    _bytes[x] = old_bytes[i];

                return send_bytes;
            }
        }

        return null;
    }


    /** RETURNS THE GIVEN INDEX OF THE BYTE ARRAY IF APPLICABLE,
     *   OTHERWISE RETURNS NULL BYTE */
    public byte get(int x) {
        if(_bytes == null || x >= _bytes.length || x < 0)
            return 0x00;

        return _bytes[x];
    }
}

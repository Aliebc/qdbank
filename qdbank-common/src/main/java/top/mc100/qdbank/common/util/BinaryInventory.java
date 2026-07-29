package top.mc100.qdbank.common.util;

import java.io.*;

public final class BinaryInventory {
    private BinaryInventory(){}
    public static byte[][] decode(byte[] content){
        byte[][] slots=new byte[54][];
        if(content==null||content.length==0){for(int i=0;i<54;i++)slots[i]=new byte[0];return slots;}
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(content))){
            if(in.readInt()!=54)throw new IllegalArgumentException("Invalid inventory");
            for(int i=0;i<54;i++){int length=in.readInt();slots[i]=in.readNBytes(length);}
            return slots;
        }catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public static byte[] encode(byte[][] slots){
        try{
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(buffer)){
                out.writeInt(54);for(byte[] slot:slots){byte[] data=slot==null?new byte[0]:slot;out.writeInt(data.length);out.write(data);}
            }
            return buffer.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
}

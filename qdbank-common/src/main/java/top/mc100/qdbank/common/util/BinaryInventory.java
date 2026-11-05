package top.mc100.qdbank.common.util;

import top.mc100.qdbank.common.ProtocolLimits;

import java.io.*;

public final class BinaryInventory {
    private static final int MAGIC=0x51444249; // QDBI
    private static final int FORMAT=1;
    private static final int SIZE=54;
    private BinaryInventory(){}
    public static byte[][] decode(byte[] content){
        byte[][] slots=new byte[SIZE][];
        if(content==null||content.length==0){for(int i=0;i<SIZE;i++)slots[i]=new byte[0];return slots;}
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(content))){
            if(in.readInt()!=MAGIC||in.readInt()!=FORMAT||in.readInt()!=SIZE)throw new IllegalArgumentException("Invalid QDBank NBT inventory");
            for(int i=0;i<SIZE;i++){
                String key=in.readUTF();
                if(!key.equals("Slot:"+i))throw new IllegalArgumentException("Invalid slot key "+key);
                int length=in.readInt();
                if(length<0||length>ProtocolLimits.MAX_SIZE_BYTES||length>in.available())throw new IllegalArgumentException("Invalid slot payload");
                slots[i]=in.readNBytes(length);
            }
            if(in.available()!=0)throw new IllegalArgumentException("Trailing inventory data");
            return slots;
        }catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public static byte[] encode(byte[][] slots){
        try{
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(buffer)){
                if(slots.length!=SIZE)throw new IllegalArgumentException("Bank inventory must contain 54 slots");
                out.writeInt(MAGIC);out.writeInt(FORMAT);out.writeInt(SIZE);
                for(int i=0;i<SIZE;i++){
                    byte[] data=slots[i]==null?new byte[0]:slots[i];
                    out.writeUTF("Slot:"+i);out.writeInt(data.length);out.write(data);
                }
            }
            return buffer.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
}

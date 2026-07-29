package top.mc100.qdbank.common.util;

import top.mc100.qdbank.common.dto.AuditItemChange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class AuditItemCodec {
    private AuditItemCodec(){}
    public static String encode(List<AuditItemChange> changes){
        StringBuilder result=new StringBuilder();
        for(AuditItemChange change:changes){
            if(!result.isEmpty())result.append('\n');
            result.append(change.amount()).append('|')
                    .append(Base64.getEncoder().encodeToString(change.material().getBytes(StandardCharsets.UTF_8))).append('|')
                    .append(Base64.getEncoder().encodeToString(change.itemBytes()));
        }
        return result.toString();
    }
    public static List<AuditItemChange> decode(String value){
        if(value==null||value.isBlank())return List.of();
        List<AuditItemChange> result=new ArrayList<>();
        for(String line:value.split("\\n")){
            String[] parts=line.split("\\|",3);
            if(parts.length==3)result.add(new AuditItemChange(Integer.parseInt(parts[0]),
                    new String(Base64.getDecoder().decode(parts[1]),StandardCharsets.UTF_8),
                    Base64.getDecoder().decode(parts[2])));
        }
        return result;
    }
}

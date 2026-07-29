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
            result.append(change.slot()).append('|').append(change.amount()).append('|')
                    .append(Base64.getEncoder().encodeToString(change.material().getBytes(StandardCharsets.UTF_8))).append('|')
                    .append(Base64.getEncoder().encodeToString(change.itemBytes())).append('|')
                    .append(Base64.getEncoder().encodeToString(change.beforeItem())).append('|')
                    .append(Base64.getEncoder().encodeToString(change.afterItem()));
        }
        return result.toString();
    }
    public static List<AuditItemChange> decode(String value){
        if(value==null||value.isBlank())return List.of();
        List<AuditItemChange> result=new ArrayList<>();
        for(String line:value.split("\\n")){
            String[] parts=line.split("\\|",6);
            if(parts.length==6)result.add(new AuditItemChange(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),
                    new String(Base64.getDecoder().decode(parts[2]),StandardCharsets.UTF_8),
                    Base64.getDecoder().decode(parts[3]),Base64.getDecoder().decode(parts[4]),Base64.getDecoder().decode(parts[5])));
        }
        return result;
    }
}

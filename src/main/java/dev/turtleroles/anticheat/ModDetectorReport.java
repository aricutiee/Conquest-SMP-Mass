package dev.turtleroles.anticheat;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Read-only integration with ModDetector 1.0.1's documented JSONL output.
 * No dependency on obfuscated internals, no probes or punishment commands. */
final class ModDetectorReport {
    record Result(boolean available, long time, List<String> suspicious, boolean partial) {}
    static Result read(Path file, UUID player) {
        if(file==null || !Files.isRegularFile(file))return new Result(false,0,List.of(),false);
        long latest=0;var found=new TreeSet<String>();boolean partial=false;
        try(var input=new RandomAccessFile(file.toFile(),"r")) {
            long length=input.length(),start=Math.max(0,length-4*1024*1024);partial=start>0;input.seek(start);
            byte[] bytes=new byte[(int)(length-start)];input.readFully(bytes);
            String data=new String(bytes,StandardCharsets.UTF_8);
            if(start>0){int newline=data.indexOf('\n');data=newline<0?"":data.substring(newline+1);}
            // Do not consume the last unfinished append. The next inspection reads it again.
            int complete=data.lastIndexOf('\n');data=complete<0?"":data.substring(0,complete);
            for(String line:data.split("\n")) {
                if(line.isBlank())continue;
                if(line.length()>16_384){partial=true;continue;}
                try {
                    JsonObject json=JsonParser.parseString(line).getAsJsonObject();
                    if(!player.toString().equals(json.get("uuid").getAsString()))continue;
                    long time=Instant.parse(json.get("ts").getAsString()).toEpochMilli();
                    if(time<System.currentTimeMillis()-Duration.ofDays(30).toMillis() || time>System.currentTimeMillis()+60_000)continue;
                    if(time>latest+20_000)found.clear();
                    if(time<latest-20_000)continue;
                    latest=Math.max(latest,time);
                    String category=json.get("category").getAsString();
                    if(!Set.of("CHEAT","SUSPICIOUS","TRAP").contains(category))continue;
                    String name=json.has("mod_display")?json.get("mod_display").getAsString():json.get("mod").getAsString();
                    if(found.size()<80)found.add(IntelStore.clean(name,80));
                }catch(RuntimeException malformed){partial=true;}
            }
            return new Result(true,latest,List.copyOf(found),partial);
        }catch(IOException failure){return new Result(false,0,List.of(),partial);}
    }
}
